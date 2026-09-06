package au.com.nakornthai.ordering.createorder;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.menu.infrastructure.*;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class CreateOrderHandler {
    private final EntityManager em;
    private final OrderMapper mapper;
    private final boolean enabled;
    @Value("${PAYPAL_ENABLED:false}") private boolean paypalEnabled;
    @Value("${PAYID_ENABLED:false}") private boolean payidEnabled;
    public CreateOrderHandler(EntityManager em, OrderMapper mapper, @Value("${ONLINE_ORDERING_ENABLED:false}") boolean enabled) {
        this.em=em; this.mapper=mapper; this.enabled=enabled;
    }
    public boolean enabled() { return enabled; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public CreateOrderResponse handle(CreateOrderRequest request) {
        // Transaction lock serializes retries of the same idempotency key across instances.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)", Object.class)
                .setParameter("key", request.requestId().getMostSignificantBits() ^ request.requestId().getLeastSignificantBits()).getSingleResult();
        String fingerprint = hash(java.util.stream.Stream.of(request.customerName(), request.phone(), request.notes(), fingerprintLines(request)).map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining()));
        if(request.email()!=null && !request.email().isBlank()) fingerprint=hash(fingerprint+":"+request.email().trim().toLowerCase(Locale.ROOT));
        String paymentMethod=request.paymentMethod()==null?"PAY_AT_RESTAURANT":request.paymentMethod();
        if(!paymentMethod.equals("PAY_AT_RESTAURANT"))fingerprint=hash(fingerprint+":"+paymentMethod);
        var existing = em.find(OrderJpaEntity.class, request.requestId());
        if (existing != null) {
            if (!existing.getTrackingHash().equals(hash(request.trackingToken())) || !existing.getRequestHash().equals(fingerprint))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This checkout was already submitted with different details");
            return mapper.map(existing, false);
        }
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Online ordering is currently closed");
        if((paymentMethod.equals("PAYPAL")&&!paypalEnabled) || (paymentMethod.equals("PAYID")&&!payidEnabled))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Selected payment method is unavailable");
        var order = new OrderJpaEntity(); order.setId(request.requestId());
        order.setPaymentMethod(paymentMethod);
        order.setTrackingHash(hash(request.trackingToken())); order.setRequestHash(fingerprint);
        order.setCustomerName(request.customerName().trim()); order.setPhone(request.phone().trim()); order.setNotes(request.notes().trim());
        order.setEmail(request.email()==null || request.email().isBlank()?null:request.email().trim().toLowerCase(Locale.ROOT));
        if (request.items().stream().anyMatch(l -> l.collectionId() == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Collection is required for every new order line");
        MenuCatalogLock.read(em);
        // One instant for every collection in this checkout, after acquiring the catalog lock.
        Instant checkoutAt = Instant.now();
        order.setCreatedAt(checkoutAt); order.setUpdatedAt(checkoutAt);
        var seen = new HashSet<String>();
        var lines = request.items().stream().sorted(Comparator.comparing(CreateOrderRequest.Line::configurationKey)).toList();
        for (var line : lines) {
            if (!seen.add(line.configurationKey())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate order configuration");
            var variation = em.find(MenuItemVariationJpaEntity.class, line.variationId());
            if (variation == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "An item is no longer available");
            var item = variation.getMenuItem();
            var membership = em.find(MenuCollectionItemJpaEntity.class, new MenuAssociationId(line.collectionId(), item.getId()));
            if (membership == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish is not in the selected collection");
            var collection = membership.getCollection();
            if (membership.getCollectionCategory() != null &&
                    !membership.getCollectionCategory().getCollection().getId().equals(collection.getId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid collection category placement");
            if (!MenuCatalogRules.availability(collection, checkoutAt).available())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Selected collection is currently unavailable");
            if (!"PUBLISHED".equals(item.getStatus()) || !item.isAvailable() || !membership.effectiveCategory().isActive()
                    || !variation.isActive() || !variation.isAvailable())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An item is no longer available; review your cart");
            au.com.nakornthai.menu.domain.MenuPricing.Price price;
            try {
                price = au.com.nakornthai.menu.domain.MenuPricing.calculate(variation.getPriceMinor(), variation.isDefaultVariation(),
                        membership.getPriceOverrideMinor(), MenuCatalogRules.groups(item), line.selectedOptions().stream().map(o ->
                        new au.com.nakornthai.menu.domain.MenuPricing.Selection(o.optionId(), o.quantity())).toList());
                order.setTotalMinor(Math.addExact(order.getTotalMinor(), Math.multiplyExact(price.unitPrice(), line.quantity())));
            } catch (IllegalArgumentException | ArithmeticException invalid) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid instanceof ArithmeticException ? "Order total is too large" : invalid.getMessage());
            }
            if (line.expectedUnitPriceMinor() != price.unitPrice())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A price changed; review your cart before ordering");
            var snapshot = new OrderItemJpaEntity(); snapshot.setOrder(order); snapshot.setVariationId(variation.getId());
            snapshot.setSnapshotVersion((short) 1); snapshot.setCollectionId(collection.getId());
            snapshot.setCollectionName(collection.getName()); snapshot.setCollectionSlug(collection.getSlug());
            snapshot.setVariationBasePriceMinor(price.variationBase()); snapshot.setCollectionPriceOverrideMinor(price.appliedOverride());
            snapshot.setDishName(item.getName()); snapshot.setVariationName(variation.getName());
            snapshot.setQuantity(line.quantity()); snapshot.setUnitPriceMinor(price.unitPrice());
            for (var chosen : price.options()) {
                var option = new OrderItemOptionJpaEntity(); option.setOrderItem(snapshot); option.setOptionId(chosen.optionId());
                option.setOptionGroupName(chosen.groupName()); option.setOptionName(chosen.optionName());
                option.setPriceDeltaMinor(chosen.delta()); option.setQuantity(chosen.quantity()); option.setCreatedAt(checkoutAt);
                snapshot.getSelectedOptions().add(option);
            }
            order.getItems().add(snapshot);
        }
        em.persist(order); em.flush();
        var event = new OrderEventJpaEntity(); event.setOrderId(order.getId()); event.setStatus("NEW");
        event.setActor("CUSTOMER"); event.setCreatedAt(order.getCreatedAt()); em.persist(event);
        return mapper.map(order, false);
    }
    static String fingerprintLines(CreateOrderRequest request) {
        // Preserve the old record representation solely for legacy replays.
        if (request.items().stream().allMatch(l -> l.collectionId() == null && l.selectedOptions().isEmpty()))
            return request.items().stream().map(l -> "Line[variationId=" + l.variationId() + ", quantity=" + l.quantity()
                    + ", expectedUnitPriceMinor=" + l.expectedUnitPriceMinor() + "]")
                    .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        return request.items().stream().sorted(Comparator.comparing(CreateOrderRequest.Line::configurationKey))
                .map(l -> l.configurationKey() + ":" + l.quantity() + ":" + l.expectedUnitPriceMinor())
                .collect(java.util.stream.Collectors.joining("|", "v1[", "]"));
    }
}
