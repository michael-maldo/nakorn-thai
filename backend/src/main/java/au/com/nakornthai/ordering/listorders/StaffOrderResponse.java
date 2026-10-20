package au.com.nakornthai.ordering.listorders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff-only order view. Private contact fields never appear in the public order response. */
public record StaffOrderResponse(
    UUID id,
    String reference,
    String status,
    long totalMinor,
    String currency,
    Instant createdAt,
    Instant estimatedReadyAt,
    Instant paidAt,
    String cancellationReason,
    String customerName,
    String phone,
    String email,
    String fulfilment,
    String notes,
    long version,
    List<Line> items,
    String paymentMethod,
    String paymentStatus,
    String paymentReference,
    String refundStatus,
    boolean paymentReviewRequired,
    long subtotalMinor) {

  public record Line(
      String dishName,
      String variationName,
      int quantity,
      long unitPriceMinor,
      long lineTotalMinor,
      UUID id,
      short snapshotVersion,
      UUID collectionId,
      String collectionName,
      String collectionSlug,
      Long variationBasePriceMinor,
      Long collectionPriceOverrideMinor,
      List<SelectedOption> selectedOptions) {}

  public record SelectedOption(
      UUID optionId,
      String optionGroupName,
      String optionName,
      long priceDeltaMinor,
      int quantity) {}
}
