package au.com.nakornthai.ordering.createorder;
import java.time.Instant;
import java.util.*;
public record CreateOrderResponse(UUID id, String reference, String status, long totalMinor, String currency,
        Instant createdAt, Instant estimatedReadyAt, Instant paidAt, String cancellationReason,
        String customerName, String phone, String notes, long version, List<Line> items, String paymentMethod) {
    public record Line(String dishName, String variationName, int quantity, long unitPriceMinor) {}
}
