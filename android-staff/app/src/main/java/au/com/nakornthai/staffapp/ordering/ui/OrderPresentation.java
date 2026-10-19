package au.com.nakornthai.staffapp.ordering.ui;

import au.com.nakornthai.staffapp.ordering.model.Order;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class OrderPresentation {
  private OrderPresentation() {}

  public static String time(String instant) {
    try {
      return DateTimeFormatter.ofPattern("dd MMM HH:mm:ss", Locale.forLanguageTag("en-AU"))
          .withZone(ZoneId.of("Australia/Melbourne"))
          .format(Instant.parse(instant));
    } catch (Exception invalid) {
      return "—";
    }
  }

  public static String items(Order order) {
    StringBuilder text = new StringBuilder();
    for (Order.Line line : order.items) {
      text.append(line.quantity).append(" × ").append(line.dishName);
      if (line.variationName != null && !line.variationName.isEmpty())
        text.append(" · ").append(line.variationName);
      text.append("\n");
      if (line.selectedOptions != null)
        for (Order.Option option : line.selectedOptions)
          text.append("  + ")
              .append(option.quantity)
              .append(" × ")
              .append(option.optionName)
              .append(" ( ")
              .append(option.optionGroupName)
              .append(" )\n");
    }
    if (order.notes != null && !order.notes.isEmpty())
      text.append("Notes: ").append(order.notes).append("\n");
    return text.toString().trim();
  }

  public static String payment(Order order) {
    return String.format(
        Locale.forLanguageTag("en-AU"),
        "$%.2f AUD · %s · %s",
        order.totalMinor / 100.0,
        order.paymentMethod == null ? "" : order.paymentMethod.replace('_', ' '),
        order.paidAt != null
            ? "Paid"
            : "PAY_AT_RESTAURANT".equals(order.paymentMethod)
                ? "Payment due at handover"
                : "Payment pending")
        + (order.paymentReviewRequired ? " · Financial review required" : "")
        + (order.refundStatus != null && !"NONE".equals(order.refundStatus)
            ? " · Refund: " + order.refundStatus.replace('_', ' ') : "");
  }

  public static String detail(Order order, String role) {
    String text =
        order.status
            + "\nPlaced "
            + time(order.createdAt)
            + "\nPickup "
            + time(order.estimatedReadyAt)
            + " (Melbourne)\n\n"
            + items(order);
    if (!"BOH".equals(role)) {
      if (order.customerName != null) text += "\n\n" + order.customerName;
      if (order.phone != null) text += "\n" + order.phone;
      text += "\n\n" + payment(order);
    }
    if (order.cancellationReason != null) text += "\nCancellation: " + order.cancellationReason;
    return text;
  }

  public static String action(String operation) {
    switch (operation) {
      case "ACCEPTED":
        return "Accept order";
      case "PREPARING":
        return "Start preparation";
      case "READY":
        return "Mark ready";
      case "COMPLETED":
        return "Complete handover";
      case "CANCELLED":
        return "Cancel Order";
      case "PAYID":
        return "Confirm PayID receipt";
      case "PAYPAL":
        return "Check PayPal payment";
      default:
        return operation;
    }
  }

  public static String target(String operation) {
    switch (operation) {
      case "ACCEPTED": return "Accepted";
      case "PREPARING": return "Preparing";
      case "READY": return "Ready";
      case "COMPLETED": return "Completed";
      default: return operation;
    }
  }
}
