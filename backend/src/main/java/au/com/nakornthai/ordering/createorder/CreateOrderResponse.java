package au.com.nakornthai.ordering.createorder;
import java.time.Instant;
import java.util.*;
public record CreateOrderResponse(UUID id, String reference, String status, long totalMinor, String currency,
        Instant createdAt, Instant estimatedReadyAt, Instant paidAt, String cancellationReason,
        String customerName, String phone, String notes, long version, List<Line> items, String paymentMethod) {
    public record Line(String dishName, String variationName, int quantity, long unitPriceMinor,
                       UUID id, short snapshotVersion, UUID collectionId, String collectionName, String collectionSlug,
                       Long variationBasePriceMinor, Long collectionPriceOverrideMinor, List<SelectedOption> selectedOptions) {}
    public record SelectedOption(UUID optionId, String optionGroupName, String optionName, long priceDeltaMinor, int quantity) {}
}
