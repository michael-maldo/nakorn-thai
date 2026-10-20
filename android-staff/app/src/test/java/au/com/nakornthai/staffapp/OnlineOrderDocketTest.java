package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;

import au.com.nakornthai.staffapp.ordering.model.Order;
import au.com.nakornthai.staffapp.printing.formatting.OnlineOrderDocketFormatter;
import au.com.nakornthai.staffapp.printing.model.PrinterProfile;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

public class OnlineOrderDocketTest {
  private static final byte[] CUT = {0x1d, 0x56, 0x42, 0x00};

  private PrinterProfile profile(int width) {
    PrinterProfile profile = new PrinterProfile();
    profile.driver = "escpos";
    profile.compatibilityConfirmed = true;
    profile.paperWidth = width;
    return profile;
  }

  private Order order(String email, String status, String paymentStatus) {
    return orderAt(email, status, paymentStatus, "2026-10-20T07:24:00Z");
  }

  private Order orderAt(String email, String status, String paymentStatus, String createdAt) {
    String method = "PAYMENT_DUE".equals(paymentStatus) ? "PAY_AT_RESTAURANT" : "PAYPAL";
    String json = """
        {"id":"order-id","reference":"NT-1043","status":"%s","totalMinor":3300,"subtotalMinor":3300,
        "currency":"AUD","createdAt":"2026-10-20T07:24:00Z","estimatedReadyAt":"2026-10-20T07:45:00Z",
        "paidAt":null,"customerName":"Sarah Mitchell","phone":"0412 345 678","email":"%s","fulfilment":"PICKUP",
        "notes":"Please include extra napkins and chilli sauce.","version":2,"paymentMethod":"%s","paymentStatus":"%s",
        "refundStatus":"NONE","paymentReviewRequired":false,"items":[
        {"dishName":"Pad Thai","variationName":"Chicken","quantity":2,"unitPriceMinor":1650,"lineTotalMinor":3300,
        "selectedOptions":[{"optionGroupName":"Protein","optionName":"Chicken","quantity":1,"priceDeltaMinor":0},
        {"optionGroupName":"Allergy note","optionName":"NO PEANUTS","quantity":1,"priceDeltaMinor":0}]}]}
        """.formatted(status, email, method, paymentStatus)
            .replace("2026-10-20T07:24:00Z", createdAt);
    return new Gson().fromJson(json, Order.class);
  }

  private String text(byte[] bytes) {
    return new String(bytes, 0, bytes.length - CUT.length, StandardCharsets.US_ASCII);
  }

  @Test public void formatsDetailedDocketWithEscPosHeaderWrappingMelbourneTimeAndExactCut() {
    String email = "sarah.mitchell.with.a.very.long.address@example-restaurant.test";
    byte[] bytes = OnlineOrderDocketFormatter.format(profile(58), order(email, "NEW", "PAID"), "20");
    assertArrayEquals(new byte[] {0x1b, '@'}, Arrays.copyOf(bytes, 2));
    assertArrayEquals(CUT, Arrays.copyOfRange(bytes, bytes.length - CUT.length, bytes.length));
    String docket = text(bytes);
    assertTrue(docket.contains("NAKORN THAI"));
    assertTrue(docket.contains("ONLINE ORDER"));
    assertTrue(docket.contains("ORDER #NT-1043"));
    assertTrue(docket.contains("20 OCT 2026  6:24 PM"));
    assertTrue(docket.contains("Sarah Mitchell"));
    assertTrue(docket.contains("0412 345 678"));
    assertTrue(docket.replace("\n", "").contains(email));
    assertTrue(docket.contains("PICKUP / TAKEAWAY"));
    assertTrue(docket.contains("Estimated Ready : 6:45 PM"));
    assertTrue(docket.contains("Prep Estimate : 20 min"));
    assertTrue(docket.contains("Status : ACCEPTED"));
    assertTrue(docket.contains("PAYPAL ONLINE"));
    assertTrue(docket.contains("Status : PAID"));
    assertTrue(docket.contains("2 x Pad Thai"));
    assertTrue(docket.contains("Chicken"));
    assertTrue(docket.contains("NO PEANUTS"));
    assertTrue(docket.contains("$16.50"));
    assertTrue(docket.contains("$33.00"));
    assertTrue(docket.contains("$33.00"));
    assertTrue(docket.contains("Please include extra napkins"));
    assertTrue(docket.contains("\u001bE\u0001NAKORN THAI"));
    assertTrue(docket.contains("\u001d!\u0011ORDER #NT-1043"));
    assertTrue(docket.indexOf("END OF ORDER") > docket.indexOf("TOTAL"));
    assertFalse(docket.contains("REFERENCE"));
  }

  @Test public void handlesMissingOptionalFieldsAndUnresolvedPaymentAccurately() {
    Order order = order(null, "ACCEPTED", "PENDING_UNVERIFIED");
    assertNull(order.email);
    String docket = text(OnlineOrderDocketFormatter.format(profile(80), order, null));
    assertFalse(docket.contains("Email :"));
    assertTrue(docket.contains("PAYPAL ONLINE"));
    assertTrue(docket.contains("PENDING UNVERIFIED"));
    assertTrue(docket.contains("Subtotal"));
    assertFalse(docket.contains("Discount"));
    assertFalse(docket.contains("Fee"));
    assertTrue(docket.contains("Currency : AUD"));
    assertTrue(docket.contains("20 OCT 2026  6:24 PM"));
    for (String line : docket.split("\n")) assertTrue("line exceeds 80mm width: " + line, line.length() <= 48);
  }

  @Test public void refusesUnknownOrUnconfirmedPrinterBeforeProducingCutJob() {
    PrinterProfile profile = profile(80);
    profile.compatibilityConfirmed = false;
    assertThrows(IllegalArgumentException.class,
        () -> OnlineOrderDocketFormatter.format(profile, order("", "ACCEPTED", "PAYMENT_DUE"), null));
  }

  @Test public void payAtPickupAndFailedOrReviewPaymentStatusesStayExplicit() {
    String due = text(OnlineOrderDocketFormatter.format(profile(58), order("", "ACCEPTED", "PAYMENT_DUE"), null));
    assertTrue(due.contains("PAY AT PICKUP"));
    assertTrue(due.contains("PAYMENT DUE"));
    String failed = text(OnlineOrderDocketFormatter.format(profile(58), order("", "ACCEPTED", "FAILED"), null));
    assertTrue(failed.contains("FAILED"));
    String review = text(OnlineOrderDocketFormatter.format(profile(58), order("", "ACCEPTED", "REVIEW_REQUIRED"), null));
    assertTrue(review.contains("REVIEW REQUIRED"));
  }

  @Test public void MelbourneFormattingAppliesSummerAndWinterOffsets() {
    String summer = text(OnlineOrderDocketFormatter.format(profile(80),
        orderAt("", "ACCEPTED", "PAYMENT_DUE", "2026-01-20T01:00:00Z"), null));
    String winter = text(OnlineOrderDocketFormatter.format(profile(80),
        orderAt("", "ACCEPTED", "PAYMENT_DUE", "2026-07-20T01:00:00Z"), null));
    assertTrue(summer.contains("20 JAN 2026  12:00 PM"));
    assertTrue(winter.contains("20 JUL 2026  11:00 AM"));
  }
}
