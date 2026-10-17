package au.com.nakornthai.staffapp.ordering.model;

import java.util.*;

public final class OrderDraft {
  public final String orderId, orderReference, operation;
  public final long version;
  public String pickupMinutes = "20", reason = "", bankReference = "";
  public boolean paymentCollected, bankReceiptChecked, invalidated;

  public OrderDraft(Order order, String operation) {
    orderId = order.id;
    orderReference = order.reference;
    version = order.version;
    this.operation = operation;
  }

  public boolean conflict(Order order) {
    return invalidated || order == null || order.version != version;
  }

  public String validate() {
    if ("ACCEPTED".equals(operation)) {
      try {
        int n = Integer.parseInt(pickupMinutes);
        if (n < 5 || n > 180) return "Pickup estimate must be 5–180 whole minutes.";
      } catch (NumberFormatException e) {
        return "Enter whole pickup minutes.";
      }
    }
    if ("CANCELLED".equals(operation) && (reason.trim().isEmpty() || reason.length() > 500))
      return "Enter a cancellation reason (up to 500 characters).";
    if ("COMPLETED".equals(operation) && !paymentCollected)
      return "Confirm payment is collected and the customer is receiving the order.";
    if ("PAYID".equals(operation)
        && (bankReference.trim().isEmpty() || bankReference.length() > 150 || !bankReceiptChecked))
      return "Enter the bank reference and confirm receipt in the bank account.";
    return null;
  }

  public Map<String, Object> command() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("version", version);
    if ("PAYID".equals(operation)) {
      result.put("bankReference", bankReference.trim());
      result.put("bankReceiptChecked", bankReceiptChecked);
    } else {
      result.put("status", operation);
      result.put("paymentCollected", paymentCollected);
      if ("ACCEPTED".equals(operation))
        result.put("pickupMinutes", Integer.parseInt(pickupMinutes));
      if ("CANCELLED".equals(operation)) result.put("reason", reason.trim());
    }
    return result;
  }
}
