package au.com.nakornthai.ordering.listorders;

import static org.junit.jupiter.api.Assertions.*;

import au.com.nakornthai.ordering.infrastructure.OrderItemJpaEntity;
import au.com.nakornthai.ordering.infrastructure.OrderItemOptionJpaEntity;
import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import au.com.nakornthai.ordering.infrastructure.OrderMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderMapperStaffTest {
  private final OrderMapper mapper = new OrderMapper();

  @Test void mapsStaffOnlyDetailsAndAuthoritativePaymentAndItemAmounts() {
    OrderJpaEntity order = order();
    order.setEmail("sarah@example.test");
    order.setPaidAt(Instant.parse("2026-10-20T07:23:00Z"));
    order.setEstimatedReadyAt(Instant.parse("2026-10-20T07:45:00Z"));

    StaffOrderResponse response = mapper.mapStaff(order, true);

    assertEquals(order.getId(), response.id());
    assertEquals("PICKUP", response.fulfilment());
    assertEquals("Sarah Guest", response.customerName());
    assertEquals("+61412345678", response.phone());
    assertEquals("sarah@example.test", response.email());
    assertEquals("PAID", response.paymentStatus());
    assertEquals(3300, response.subtotalMinor());
    assertEquals(3300, response.totalMinor());
    assertEquals(3300, response.items().get(0).lineTotalMinor());
    assertEquals("Chicken", response.items().get(0).variationName());
    assertEquals("No peanuts", response.items().get(0).selectedOptions().get(0).optionName());
    assertEquals("Please include napkins", response.notes());
  }

  @Test void redactsContactsForKitchenAndHandlesNullOptionalDataWithoutInventingIt() {
    OrderJpaEntity order = order();
    order.setEmail(null);
    order.setEstimatedReadyAt(null);
    order.setPaidAt(null);
    order.setRefundStatus("NONE");

    StaffOrderResponse response = mapper.mapStaff(order, false);

    assertNull(response.customerName());
    assertNull(response.phone());
    assertNull(response.email());
    assertNull(response.estimatedReadyAt());
    assertEquals("PAYMENT_DUE", response.paymentStatus());
    assertEquals("PICKUP", response.fulfilment());
  }

  @Test void unresolvedOnlinePaymentNeverMapsToPaid() {
    OrderJpaEntity order = order();
    order.setPaymentMethod("PAYPAL");
    order.setPaidAt(null);
    order.setRefundStatus("NONE");
    assertEquals("PENDING_UNVERIFIED", mapper.mapStaff(order, true).paymentStatus());
    order.setPaymentReviewRequired(true);
    assertEquals("REVIEW_REQUIRED", mapper.mapStaff(order, true).paymentStatus());
    order.setPaymentReviewRequired(false);
    order.setRefundStatus("REQUIRED");
    assertEquals("REFUND_REQUIRED", mapper.mapStaff(order, true).paymentStatus());
  }

  @Test void existingPublicResponseStillContainsNoStaffFields() {
    var response = mapper.map(order(), false);
    assertNull(response.customerName());
    assertNull(response.phone());
    assertFalse(Arrays.stream(response.getClass().getRecordComponents())
        .anyMatch(component -> component.getName().equals("email")
            || component.getName().equals("fulfilment")
            || component.getName().equals("paymentStatus")
            || component.getName().equals("subtotalMinor")));
  }

  private OrderJpaEntity order() {
    OrderJpaEntity order = new OrderJpaEntity();
    order.setId(UUID.fromString("c25cf7b4-3a64-49c0-83d3-ed688a4a2eb7"));
    order.setVersion(3L);
    order.setStatus("ACCEPTED");
    order.setTotalMinor(3300);
    order.setCurrency("AUD");
    order.setFulfilment("PICKUP");
    order.setPaymentMethod("PAY_AT_RESTAURANT");
    order.setCustomerName("Sarah Guest");
    order.setPhone("+61412345678");
    order.setNotes("Please include napkins");
    order.setRefundStatus("NONE");
    order.setCreatedAt(Instant.parse("2026-10-20T07:24:00Z"));
    order.setUpdatedAt(Instant.parse("2026-10-20T07:25:00Z"));

    OrderItemJpaEntity item = new OrderItemJpaEntity();
    item.setId(UUID.randomUUID());
    item.setDishName("Pad Thai");
    item.setVariationName("Chicken");
    item.setQuantity(2);
    item.setUnitPriceMinor(1650);
    OrderItemOptionJpaEntity option = new OrderItemOptionJpaEntity();
    option.setOptionId(UUID.randomUUID());
    option.setOptionGroupName("Allergy note");
    option.setOptionName("No peanuts");
    option.setQuantity(1);
    option.setPriceDeltaMinor(0);
    item.setSelectedOptions(List.of(option));
    order.setItems(List.of(item));
    return order;
  }
}
