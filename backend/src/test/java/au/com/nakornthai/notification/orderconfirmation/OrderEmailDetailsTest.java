package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.ordering.infrastructure.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderEmailDetailsTest {
 static OrderJpaEntity order(){
  var order=new OrderJpaEntity();order.setId(UUID.fromString("12345678-1234-1234-1234-123456789012"));order.setCustomerName("Guest");order.setPhone("+61434778435");order.setEmail("guest@example.com");order.setCreatedAt(Instant.parse("2030-10-03T00:00:00Z"));order.setTotalMinor(5095);order.setNotes("No cutlery, please");
  var first=new OrderItemJpaEntity();first.setDishName("Historical curry");first.setVariationName("Chicken");first.setQuantity(2);first.setUnitPriceMinor(1995);
  var option=new OrderItemOptionJpaEntity();option.setOptionGroupName("Extras");option.setOptionName("Tofu");option.setQuantity(2);option.setPriceDeltaMinor(150);first.getSelectedOptions().add(option);
  var second=new OrderItemJpaEntity();second.setDishName("Rice");second.setVariationName("Large");second.setQuantity(1);second.setUnitPriceMinor(1105);order.getItems().addAll(List.of(first,second));return order;
 }
 @Test void rendersSnapshotsAndLocalTimeWithoutSecrets(){
  var order=order();order.setTrackingHash("secret-hash");var body=OrderEmailDetails.format(order,ZoneId.of("Australia/Melbourne"),false);
  for(String text:List.of("Reference: 12345678","3 Oct 2030, 10:00 am AEST","Customer: Guest","Fulfilment: PICKUP","PAY AT RESTAURANT","Not recorded as paid","2 x Historical curry","Variation: Chicken","AUD 19.95","Extras: Tofu x 2 (AUD 1.50 each per item)","Line total: AUD 39.90","1 x Rice","Line total: AUD 11.05","Total: AUD 50.95","No cutlery, please"))assertTrue(body.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT)),text+" missing: "+body);
  assertFalse(body.contains("secret-hash"));assertFalse(body.contains(order.getId().toString()));assertFalse(body.contains("guest@example.com"));
  assertTrue(OrderEmailDetails.format(order,ZoneOffset.UTC,true).contains("Phone: +61434778435"));
 }
 @Test void optionalValuesAndPaidStatus(){var order=order();order.setNotes(null);order.setPhone(null);order.setEmail(null);order.setPaidAt(Instant.now());order.getItems().getFirst().setVariationName(null);var body=OrderEmailDetails.format(order,ZoneOffset.UTC,true);assertTrue(body.contains("Payment: Paid"));assertFalse(body.contains("null"));assertFalse(body.contains("Customer notes"));assertFalse(body.contains("Phone:"));}
}
