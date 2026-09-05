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
        String fingerprint = hash(java.util.stream.Stream.of(request.customerName(), request.phone(), request.notes(), request.items().toString()).map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining()));
        var existing = em.find(OrderJpaEntity.class, request.requestId());
        if (existing != null) {
            if (!existing.getTrackingHash().equals(hash(request.trackingToken())) || !existing.getRequestHash().equals(fingerprint))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This checkout was already submitted with different details");
            return mapper.map(existing, false);
        }
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Online ordering is currently closed");
        var order = new OrderJpaEntity(); order.setId(request.requestId());
        order.setTrackingHash(hash(request.trackingToken())); order.setRequestHash(fingerprint);
        order.setCustomerName(request.customerName().trim()); order.setPhone(request.phone().trim()); order.setNotes(request.notes().trim());
        order.setCreatedAt(Instant.now()); order.setUpdatedAt(order.getCreatedAt());
        var seen = new HashSet<UUID>();
        // A stable lock order avoids competing checkouts locking dishes in different orders.
        var lines = request.items().stream().sorted(Comparator.comparing(CreateOrderRequest.Line::variationId)).toList();
        for (var line : lines) {
            if (!seen.add(line.variationId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate variation");
            var variation = em.find(MenuItemVariationJpaEntity.class, line.variationId());
            if (variation == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "An item is no longer available");
            var item = em.find(MenuItemJpaEntity.class, variation.getMenuItem().getId(), LockModeType.PESSIMISTIC_READ);
            em.refresh(variation, LockModeType.PESSIMISTIC_READ);
            Long visible = em.createQuery("""
                select count(m) from MenuCollectionItemJpaEntity m
                where m.menuItem.id=:id and m.collection.status='PUBLISHED'
                  and (m.collection.startsAt is null or m.collection.startsAt <= CURRENT_TIMESTAMP)
                  and (m.collection.endsAt is null or CURRENT_TIMESTAMP < m.collection.endsAt)
                """, Long.class).setParameter("id", item.getId()).getSingleResult();
            if (!"PUBLISHED".equals(item.getStatus()) || !item.isAvailable() || !item.getCategory().isActive()
                    || !variation.isActive() || !variation.isAvailable() || visible == 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "An item is no longer available; review your cart");
            if (line.expectedUnitPriceMinor() != variation.getPriceMinor())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A price changed; review your cart before ordering");
            var snapshot = new OrderItemJpaEntity(); snapshot.setOrder(order); snapshot.setVariationId(variation.getId());
            snapshot.setDishName(item.getName()); snapshot.setVariationName(variation.getName());
            snapshot.setQuantity(line.quantity()); snapshot.setUnitPriceMinor(variation.getPriceMinor()); order.getItems().add(snapshot);
            order.setTotalMinor(Math.addExact(order.getTotalMinor(), Math.multiplyExact(variation.getPriceMinor(), line.quantity())));
        }
        em.persist(order); em.flush();
        var event = new OrderEventJpaEntity(); event.setOrderId(order.getId()); event.setStatus("NEW");
        event.setActor("CUSTOMER"); event.setCreatedAt(order.getCreatedAt()); em.persist(event);
        return mapper.map(order, false);
    }
}
