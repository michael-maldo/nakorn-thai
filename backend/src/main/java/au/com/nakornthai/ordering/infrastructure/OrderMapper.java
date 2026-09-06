package au.com.nakornthai.ordering.infrastructure;
import au.com.nakornthai.ordering.createorder.CreateOrderResponse;
import org.springframework.stereotype.Component;
@Component
public class OrderMapper {
    public CreateOrderResponse map(OrderJpaEntity order, boolean contact) {
        return new CreateOrderResponse(order.getId(), order.getId().toString().substring(0,8).toUpperCase(),
                order.getStatus(), order.getTotalMinor(), order.getCurrency(), order.getCreatedAt(),
                order.getEstimatedReadyAt(), order.getPaidAt(), order.getCancellationReason(),
                contact ? order.getCustomerName() : null, contact ? order.getPhone() : null,
                order.getNotes(), order.getVersion(), order.getItems().stream().map(i ->
                    new CreateOrderResponse.Line(i.getDishName(), i.getVariationName(), i.getQuantity(), i.getUnitPriceMinor())).toList(), order.getPaymentMethod());
    }
}
