package au.com.nakornthai.ordering.infrastructure;
import au.com.nakornthai.ordering.createorder.CreateOrderResponse;
import au.com.nakornthai.ordering.listorders.StaffOrderResponse;
import au.com.nakornthai.payment.infrastructure.OrderPaymentJpaEntity;
import org.springframework.stereotype.Component;
@Component
public class OrderMapper {
    public CreateOrderResponse map(OrderJpaEntity order, boolean contact) {
        return new CreateOrderResponse(order.getId(), order.getId().toString().substring(0,8).toUpperCase(),
                order.getStatus(), order.getTotalMinor(), order.getCurrency(), order.getCreatedAt(),
                order.getEstimatedReadyAt(), order.getPaidAt(), order.getCancellationReason(),
                contact ? order.getCustomerName() : null, contact ? order.getPhone() : null,
                order.getNotes(), order.getVersion(), order.getItems().stream().map(i ->
                    new CreateOrderResponse.Line(i.getDishName(), i.getVariationName(), i.getQuantity(), i.getUnitPriceMinor(),
                            i.getId(), i.getSnapshotVersion(), i.getCollectionId(), i.getCollectionName(), i.getCollectionSlug(),
                            i.getVariationBasePriceMinor(), i.getCollectionPriceOverrideMinor(),
                            i.getSelectedOptions().stream().map(o -> new CreateOrderResponse.SelectedOption(o.getOptionId(),
                                    o.getOptionGroupName(), o.getOptionName(), o.getPriceDeltaMinor(), o.getQuantity())).toList())).toList(), order.getPaymentMethod(), order.getRefundStatus(), order.isPaymentReviewRequired());
    }

    public StaffOrderResponse mapStaff(OrderJpaEntity order, boolean privateContact) {
        return mapStaff(order, privateContact, null);
    }

    public StaffOrderResponse mapStaff(OrderJpaEntity order, boolean privateContact,
                                       OrderPaymentJpaEntity payment) {
        var publicView = map(order, privateContact);
        var lines = publicView.items().stream().map(i -> new StaffOrderResponse.Line(
                i.dishName(), i.variationName(), i.quantity(), i.unitPriceMinor(),
                Math.multiplyExact(i.unitPriceMinor(), i.quantity()), i.id(), i.snapshotVersion(),
                i.collectionId(), i.collectionName(), i.collectionSlug(), i.variationBasePriceMinor(),
                i.collectionPriceOverrideMinor(), i.selectedOptions().stream().map(o ->
                    new StaffOrderResponse.SelectedOption(o.optionId(), o.optionGroupName(),
                        o.optionName(), o.priceDeltaMinor(), o.quantity())).toList())).toList();
        long subtotal = lines.stream().mapToLong(StaffOrderResponse.Line::lineTotalMinor)
                .reduce(0L, Math::addExact);
        return new StaffOrderResponse(publicView.id(), publicView.reference(), publicView.status(),
                publicView.totalMinor(), publicView.currency(), publicView.createdAt(),
                publicView.estimatedReadyAt(), publicView.paidAt(), publicView.cancellationReason(),
                privateContact ? order.getCustomerName() : null,
                privateContact ? order.getPhone() : null,
                privateContact ? order.getEmail() : null,
                order.getFulfilment(), publicView.notes(), publicView.version(), lines,
                publicView.paymentMethod(), paymentStatus(order),
                safePaidReference(order, privateContact, payment), publicView.refundStatus(),
                publicView.paymentReviewRequired(), subtotal);
    }

    private String safePaidReference(OrderJpaEntity order, boolean privateContact,
                                     OrderPaymentJpaEntity payment) {
        if (!privateContact || order.getPaidAt() == null || payment == null
                || !order.getId().equals(payment.getOrderId())) return null;
        String reference = payment.getConfirmationReference();
        return reference != null && reference.matches("[A-Za-z0-9._/-]{1,100}")
                ? reference : null;
    }

    private String paymentStatus(OrderJpaEntity order) {
        if (order.isPaymentReviewRequired()) return "REVIEW_REQUIRED";
        String refund = order.getRefundStatus();
        if (refund != null && !"NONE".equals(refund)) return "REFUND_" + refund;
        if (order.getPaidAt() != null && "NONE".equals(refund)) return "PAID";
        if ("PAY_AT_RESTAURANT".equals(order.getPaymentMethod())) return "PAYMENT_DUE";
        return "PENDING_UNVERIFIED";
    }
}
