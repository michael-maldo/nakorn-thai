package au.com.nakornthai.staffapp.printing.formatting;

import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.printing.driver.EscPosPrinterDriver;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Detailed staff docket for online orders. Customer and payment details never enter the kitchen layout. */
public final class OnlineOrderDocketFormatter {
  private static final byte ESC = 0x1b;
  private static final byte GS = 0x1d;
  private static final byte[] FEED_AND_PARTIAL_CUT = {0x1d, 0x56, 0x42, 0x00};
  private static final ZoneId MELBOURNE = ZoneId.of("Australia/Melbourne");

  private OnlineOrderDocketFormatter() {}

  public static byte[] format(PrinterProfile profile, Order order, String acceptedEstimateMinutes) {
    if (profile == null || order == null) throw new IllegalArgumentException("Order and printer profile are required");
    if (!new EscPosPrinterDriver().canPrint(profile))
      throw new IllegalArgumentException("Detailed docket requires a confirmed ESC/POS-compatible printer profile");
    int width = profile.paperWidth == 80 ? 48 : 32;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(ESC); out.write('@');

    center(out); bold(out, true); line(out, "NAKORN THAI"); bold(out, false);
    center(out); line(out, "ONLINE ORDER");
    left(out); line(out, divider(width));
    center(out); bold(out, true); large(out, true); line(out, "ORDER #" + safe(order.reference));
    large(out, false); bold(out, false);
    String placed = dateTime(order.createdAt);
    if (present(placed)) { center(out); line(out, placed); }
    left(out); line(out, divider(width));

    section(out, width, "CUSTOMER INFORMATION");
    field(out, width, "Name", order.customerName);
    field(out, width, "Phone", order.phone);
    field(out, width, "Email", order.email);

    section(out, width, "FULFILLMENT");
    field(out, width, "Type", fulfillment(order.fulfilment));
    field(out, width, "Order Time", time(order.createdAt));
    if (present(order.estimatedReadyAt)) field(out, width, "Estimated Ready", time(order.estimatedReadyAt));
    if (present(acceptedEstimateMinutes)) field(out, width, "Prep Estimate", safe(acceptedEstimateMinutes) + " min");
    field(out, width, "Status", present(acceptedEstimateMinutes) ? "ACCEPTED" : safe(order.status));

    section(out, width, "PAYMENT INFORMATION");
    field(out, width, "Method", paymentMethod(order.paymentMethod));
    field(out, width, "Status", paymentStatus(order));
    field(out, width, "Currency", order.currency);

    section(out, width, "ORDER ITEMS");
    if (order.items != null) for (Order.Line item : order.items) {
      bold(out, true);
      wrapped(out, width, safe(item.quantity) + " x " + safe(item.dishName), "");
      bold(out, false);
      if (present(item.variationName)) wrapped(out, width, "    " + item.variationName, "    ");
      if (item.selectedOptions != null) for (Order.Option option : item.selectedOptions) {
        String group = present(option.optionGroupName) ? option.optionGroupName + ": " : "";
        String text = "    " + group + safe(option.optionName);
        boolean emphasized = explicitSpecialInstruction(option);
        if (emphasized) bold(out, true);
        wrapped(out, width, text, "    ");
        if (emphasized) bold(out, false);
      }
      if (item.lineTotalMinor != null && item.quantity > 0) {
        moneyRow(out, width, "Unit Price", item.unitPriceMinor);
        moneyRow(out, width, "Line Total", item.lineTotalMinor);
      }
      line(out, divider(width));
    }

    section(out, width, "ORDER SUMMARY");
    if (order.subtotalMinor != null) moneyRow(out, width, "Subtotal", order.subtotalMinor);
    bold(out, true);
    moneyRow(out, width, "TOTAL", order.totalMinor);
    bold(out, false);

    if (present(order.notes)) {
      section(out, width, "CUSTOMER INSTRUCTIONS");
      wrapped(out, width, order.notes, "");
    }
    line(out, divider(width));
    center(out); bold(out, true); line(out, "END OF ORDER"); bold(out, false);
    center(out); line(out, divider(width));
    line(out, "");
    out.write(FEED_AND_PARTIAL_CUT, 0, FEED_AND_PARTIAL_CUT.length);
    return out.toByteArray();
  }

  private static void section(ByteArrayOutputStream out, int width, String name) {
    left(out); line(out, divider(width)); bold(out, true); line(out, name); bold(out, false); line(out, divider(width));
  }

  private static void field(ByteArrayOutputStream out, int width, String label, String value) {
    if (!present(value)) return;
    wrapped(out, width, label + " : " + value, "Email".equals(label) ? "" : "        ");
  }

  private static void moneyRow(ByteArrayOutputStream out, int width, String label, long minor) {
    DecimalFormat money = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    money.setRoundingMode(java.math.RoundingMode.UNNECESSARY);
    String amount = "$" + money.format(BigDecimal.valueOf(minor, 2));
    String left = safe(label);
    int spaces = Math.max(1, width - left.length() - amount.length());
    line(out, left + repeat(' ', spaces) + amount);
  }

  private static void wrapped(ByteArrayOutputStream out, int width, String value, String continuationIndent) {
    String safe = printable(value);
    if (safe.isEmpty()) { line(out, ""); return; }
    int indent = Math.min(continuationIndent.length(), width - 1);
    int pos = 0;
    String prefix = "";
    while (pos < safe.length()) {
      int capacity = width - prefix.length();
      if (safe.length() - pos <= capacity) { line(out, prefix + safe.substring(pos)); break; }
      int end = pos + capacity;
      int split = safe.lastIndexOf(' ', end);
      if (split <= pos) split = end;
      line(out, prefix + safe.substring(pos, split));
      pos = split;
      while (pos < safe.length() && safe.charAt(pos) == ' ') pos++;
      prefix = repeat(' ', indent);
    }
  }

  private static void line(ByteArrayOutputStream out, String text) {
    byte[] bytes = printable(text).getBytes(StandardCharsets.US_ASCII);
    out.write(bytes, 0, bytes.length); out.write('\n');
  }

  private static void left(ByteArrayOutputStream out) { out.write(ESC); out.write('a'); out.write(0); }
  private static void center(ByteArrayOutputStream out) { out.write(ESC); out.write('a'); out.write(1); }
  private static void bold(ByteArrayOutputStream out, boolean on) { out.write(ESC); out.write('E'); out.write(on ? 1 : 0); }
  private static void large(ByteArrayOutputStream out, boolean on) { out.write(GS); out.write('!'); out.write(on ? 0x11 : 0); }
  private static String divider(int width) { return repeat('-', width); }
  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(Math.max(0, count));
    for (int i = 0; i < count; i++) result.append(value);
    return result.toString();
  }
  private static String time(String value) {
    try { return DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH).withZone(MELBOURNE).format(Instant.parse(value)); }
    catch (Exception ignored) { return ""; }
  }
  private static String dateTime(String value) {
    try { return DateTimeFormatter.ofPattern("dd MMM uuuu  h:mm a", Locale.ENGLISH).withZone(MELBOURNE).format(Instant.parse(value)).toUpperCase(Locale.ENGLISH); }
    catch (Exception ignored) { return ""; }
  }
  private static String fulfillment(String value) {
    if (!present(value)) return "";
    if ("PICKUP".equalsIgnoreCase(value)) return "PICKUP / TAKEAWAY";
    return value.toUpperCase(Locale.ENGLISH);
  }
  private static String paymentMethod(String value) {
    if ("PAYPAL".equals(value)) return "PAYPAL ONLINE";
    if ("PAYID".equals(value)) return "PAYID ONLINE";
    if ("PAY_AT_RESTAURANT".equals(value)) return "PAY AT PICKUP";
    return safe(value);
  }
  private static String paymentStatus(Order order) {
    if (present(order.paymentStatus)) return order.paymentStatus.replace('_', ' ');
    if (order.paymentReviewRequired) return "REVIEW REQUIRED";
    if (order.paidAt != null && (order.refundStatus == null || "NONE".equals(order.refundStatus))) return "PAID";
    if ("PAY_AT_RESTAURANT".equals(order.paymentMethod)) return "PAYMENT DUE";
    return "PENDING / UNVERIFIED";
  }
  private static boolean explicitSpecialInstruction(Order.Option option) {
    String group = safe(option.optionGroupName).toLowerCase(Locale.ROOT);
    String value = safe(option.optionName).trim().toLowerCase(Locale.ROOT);
    return group.contains("allergy") || value.startsWith("no ") || value.startsWith("without ") || group.contains("instruction");
  }
  private static boolean present(String value) { return value != null && !value.trim().isEmpty(); }
  private static String printable(String value) {
    StringBuilder result = new StringBuilder();
    if (value == null) return "";
    for (char c : value.toCharArray()) result.append(c >= 32 && c <= 126 ? c : '?');
    return result.toString();
  }
  private static String safe(String value) { return value == null ? "" : value; }
  private static String safe(int value) { return Integer.toString(value); }
}
