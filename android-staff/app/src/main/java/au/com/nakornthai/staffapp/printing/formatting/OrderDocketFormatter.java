package au.com.nakornthai.staffapp.printing.formatting;

import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.printing.driver.EscPosPrinterDriver;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Kitchen-only docket. Deliberately excludes payment and customer contact details. */
public final class OrderDocketFormatter {
  // Epson GS V 66 0: feed to the cutter position, then perform a partial cut.
  private static final byte[] FEED_AND_PARTIAL_CUT = {
    0x1d, 0x56, 0x42, 0x00
  };

  private OrderDocketFormatter() {}

  public static byte[] format(PrinterProfile profile, Order order, String pickupMinutes) {
    int width = profile.paperWidth == 80 ? 48 : 32;
    ZoneId zone = ZoneId.of(profile.timezone);
    StringBuilder text = new StringBuilder("NAKORN THAI\nKITCHEN ORDER\n\n#")
        .append(safe(order.reference)).append("\nOrder time: ")
        .append(time(order.createdAt, zone)).append('\n');
    if (pickupMinutes != null && !pickupMinutes.isEmpty())
      text.append("Pickup estimate: ").append(safe(pickupMinutes)).append(" min\n");
    else if (order.estimatedReadyAt != null && !order.estimatedReadyAt.isEmpty())
      text.append("Pickup: ").append(time(order.estimatedReadyAt, zone)).append('\n');
    text.append("\n");
    for (Order.Line line : order.items) {
      text.append(line.quantity).append(" x ").append(safe(line.dishName));
      if (line.variationName != null && !line.variationName.isEmpty())
        text.append(" / ").append(safe(line.variationName));
      text.append('\n');
      if (line.selectedOptions != null) for (Order.Option option : line.selectedOptions) {
        text.append("  + ").append(option.quantity).append(" x ").append(safe(option.optionName));
        if (option.optionGroupName != null && !option.optionGroupName.isEmpty())
          text.append(" (").append(safe(option.optionGroupName)).append(')');
        text.append('\n');
      }
    }
    if (order.notes != null && !order.notes.trim().isEmpty())
      text.append("\nOrder notes: ").append(safe(order.notes)).append('\n');
    text.append("\n\n");
    StringBuilder wrapped = new StringBuilder();
    int col = 0;
    for (char c : text.toString().toCharArray()) {
      if (c == '\n') { wrapped.append(c); col = 0; }
      else {
        if (col == width) { wrapped.append('\n'); col = 0; }
        wrapped.append(c >= 32 && c <= 126 ? c : '?'); col++;
      }
    }
    byte[] docket = wrapped.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    if (!new EscPosPrinterDriver().canPrint(profile))
      throw new IllegalArgumentException(
          "Paper cutting requires an enabled, compatibility-confirmed ESC/POS profile");
    byte[] job = java.util.Arrays.copyOf(docket, docket.length + FEED_AND_PARTIAL_CUT.length);
    System.arraycopy(FEED_AND_PARTIAL_CUT, 0, job, docket.length, FEED_AND_PARTIAL_CUT.length);
    return job;
  }

  private static String time(String value, ZoneId zone) {
    try { return DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm", Locale.ENGLISH)
        .withZone(zone).format(Instant.parse(value)); }
    catch (Exception invalid) { return "Unavailable"; }
  }

  private static String safe(String value) { return value == null ? "" : value; }
}
