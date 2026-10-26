package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;

import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.ordering.monitoring.*;
import com.google.gson.Gson;
import java.util.*;
import org.junit.Test;

public class OrderWorkflowTest {
  @Test public void historicalPaymentDoesNotOverrideFinancialReview() {
    Order paid = new Order("review", "REVIEW", "NEW", 0, "PAYPAL", "2026-10-19T00:00:00Z", java.util.Collections.emptyList());
    assertFalse(paid.needsPayment());
    paid.paymentReviewRequired = true;
    assertTrue(paid.needsPayment());
    paid.paymentReviewRequired = false;
    paid.refundStatus = "COMPLETED";
    assertTrue(paid.needsPayment());
    assertTrue(au.com.nakornthai.staffapp.ordering.ui.OrderPresentation.payment(paid).contains("Refund: COMPLETED"));
  }
  static Order order(String id, String state, long version) {
    return new Order(id, id, state, version, "PAY_AT_RESTAURANT", null, Collections.emptyList());
  }

  @Test
  public void rolesPreserveServerWorkflow() {
    for (String role : List.of("FOH", "BOH")) {
      assertEquals(List.of("ACCEPTED"), order("a", "NEW", 1).actions(role));
      assertEquals(List.of("PREPARING"), order("a", "ACCEPTED", 1).actions(role));
      assertEquals(List.of("READY"), order("a", "PREPARING", 1).actions(role));
      assertEquals(List.of("COMPLETED"), order("a", "READY", 1).actions(role));
    }
    assertTrue(order("a", "COMPLETED", 1).actions("ADMIN").isEmpty());
    assertEquals(List.of("ACCEPTED", "CANCELLED"), order("a", "NEW", 1).actions("ADMIN"));
  }

  @Test
  public void draftsKeepIndependentVersionsAndValidation() {
    Order source = order("a", "NEW", 4);
    OrderDraft accept = new OrderDraft(source, "ACCEPTED"),
        payment = new OrderDraft(source, "PAYID");
    accept.pickupMinutes = "4";
    assertNotNull(accept.validate());
    accept.pickupMinutes = "180";
    assertNull(accept.validate());
    accept.pickupMinutes = "1.5";
    assertNotNull(accept.validate());
    accept.pickupMinutes = "20";
    assertEquals(4L, accept.command().get("version"));
    assertEquals(4, payment.version);
    assertTrue(accept.conflict(order("a", "NEW", 5)));
    assertEquals(4L, accept.command().get("version"));
    payment.bankReference = " BANK ";
    assertNotNull(payment.validate());
    payment.bankReceiptChecked = true;
    assertNull(payment.validate());
    assertEquals("BANK", payment.command().get("bankReference"));
    OrderDraft cancel = new OrderDraft(source, "CANCELLED");
    assertNotNull(cancel.validate());
    cancel.reason = "Sold out";
    assertNull(cancel.validate());
    OrderDraft handover = new OrderDraft(source, "COMPLETED");
    assertNotNull(handover.validate());
    handover.paymentCollected = true;
    assertNull(handover.validate());
  }

  @Test
  public void paymentPrerequisites() {
    assertTrue(new Order("a", "a", "NEW", 0, "PAYPAL", null, List.of()).needsPayment());
    assertTrue(new Order("a", "a", "NEW", 0, "PAYID", null, List.of()).needsPayment());
    assertFalse(
        new Order("a", "a", "NEW", 0, "PAYPAL", "2026-10-17T00:00:00Z", List.of()).needsPayment());
    assertFalse(order("a", "NEW", 0).needsPayment());
  }

  @Test
  public void groupingCountsAndEmptyColumns() {
    BoardState board =
        new BoardState(
            List.of(order("a", "NEW", 0), order("b", "NEW", 0), order("c", "READY", 0)),
            false,
            false,
            true,
            false,
            1,
            0,
            0,
            null,
            null);
    assertEquals(2, board.column("NEW").size());
    assertEquals(1, board.column("READY").size());
    assertTrue(board.column("ACCEPTED").isEmpty());
    assertTrue(board.column("PREPARING").isEmpty());
  }

  @Test
  public void parsesRealSnapshotAndRedactedContacts() {
    String json =
        "{\"id\":\"a\",\"reference\":\"A\",\"status\":\"ACCEPTED\",\"version\":7,\"paymentMethod\":\"PAYID\",\"items\":[{\"dishName\":\"Curry\",\"variationName\":\"Large\",\"quantity\":2,\"selectedOptions\":[{\"optionName\":\"Tofu\",\"quantity\":1}]}]}";
    Order order = new Gson().fromJson(json, Order.class);
    assertEquals(7, order.version);
    assertNull(order.customerName);
    assertEquals("Tofu", order.items.get(0).selectedOptions.get(0).optionName);
    assertEquals(2, order.items.get(0).quantity);
  }

  @Test
  public void firstSnapshotIsSilentAndAlertsDeduplicate() {
    NewOrderTracker tracker = new NewOrderTracker();
    assertFalse(tracker.accept(List.of(order("a", "NEW", 0))));
    assertTrue(tracker.accept(List.of(order("a", "NEW", 0), order("b", "NEW", 0))));
    assertEquals(1, tracker.count());
    assertFalse(tracker.accept(List.of(order("a", "NEW", 0), order("b", "NEW", 0))));
    tracker.acknowledge();
    assertEquals(0, tracker.count());
    assertFalse(tracker.accept(List.of(order("b", "NEW", 0))));
    assertTrue(tracker.accept(List.of(order("b", "NEW", 0), order("c", "NEW", 0))));
    tracker.accept(List.of(order("c", "ACCEPTED", 1)));
    assertEquals(0, tracker.count());
  }

  @Test
  public void detailsUseSnapshotsAndSuppressContactsForKitchen() {
    Order order =
        new Gson()
            .fromJson(
                "{\"id\":\"a\",\"reference\":\"A\",\"status\":\"ACCEPTED\",\"customerName\":\"Sensitive"
                    + " Name\",\"phone\":\"0412345678\",\"paymentMethod\":\"PAYID\",\"items\":[{\"dishName\":\"Historical"
                    + " curry\",\"quantity\":2,\"selectedOptions\":[]}]}",
                Order.class);
    String kitchen = au.com.nakornthai.staffapp.ordering.ui.OrderPresentation.detail(order, "BOH");
    assertTrue(kitchen.contains("2 × Historical curry"));
    assertFalse(kitchen.contains("Sensitive Name"));
    assertFalse(kitchen.contains("0412345678"));
    assertTrue(
        au.com.nakornthai.staffapp.ordering.ui.OrderPresentation.detail(order, "FOH")
            .contains("Sensitive Name"));
  }
}
