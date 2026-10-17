package au.com.nakornthai.staffapp.ordering.model;

import java.util.*;

/** Server snapshots. UI never edits these objects or rebuilds them from the current menu. */
public final class Order {
  public final String id,
      reference,
      status,
      currency,
      createdAt,
      estimatedReadyAt,
      paidAt,
      cancellationReason,
      customerName,
      phone,
      notes,
      paymentMethod;
  public final long totalMinor, version;
  public final List<Line> items;

  public Order(
      String id,
      String reference,
      String status,
      long version,
      String paymentMethod,
      String paidAt,
      List<Line> items) {
    this.id = id;
    this.reference = reference;
    this.status = status;
    this.version = version;
    this.paymentMethod = paymentMethod;
    this.paidAt = paidAt;
    this.items = Collections.unmodifiableList(new ArrayList<>(items));
    currency = "AUD";
    createdAt = null;
    estimatedReadyAt = null;
    cancellationReason = null;
    customerName = null;
    phone = null;
    notes = null;
    totalMinor = 0;
  }

  public static final class Line {
    public String id, dishName, variationName;
    public int quantity;
    public long unitPriceMinor;
    public List<Option> selectedOptions;
  }

  public static final class Option {
    public String optionGroupName, optionName;
    public int quantity;
    public long priceDeltaMinor;
  }

  public boolean needsPayment() {
    return paidAt == null && !"PAY_AT_RESTAURANT".equals(paymentMethod);
  }

  public List<String> actions(String role) {
    boolean front = "ADMIN".equals(role) || "FOH".equals(role),
        kitchen = "ADMIN".equals(role) || "BOH".equals(role);
    List<String> actions = new ArrayList<>();
    if (front && "NEW".equals(status)) actions.add("ACCEPTED");
    if (kitchen && "ACCEPTED".equals(status)) actions.add("PREPARING");
    if (kitchen && "PREPARING".equals(status)) actions.add("READY");
    if (front && "READY".equals(status)) actions.add("COMPLETED");
    if (front && Arrays.asList("NEW", "ACCEPTED", "PREPARING", "READY").contains(status))
      actions.add("CANCELLED");
    return actions;
  }
}
