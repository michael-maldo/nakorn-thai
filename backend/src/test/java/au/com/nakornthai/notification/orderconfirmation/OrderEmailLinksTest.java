package au.com.nakornthai.notification.orderconfirmation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OrderEmailLinksTest {
 @Test void trustedBaseAndCallbackUseExistingHashRoutesWithoutCredentials(){
  var id=UUID.randomUUID();var links=new OrderEmailLinks("https://restaurant.example.test/app/");
  assertEquals("https://restaurant.example.test/app/#/track-order?order="+id,links.customer(id));
  assertEquals("https://restaurant.example.test/app/#/staff/orders?order="+id,links.staff(id));
  assertTrue(new OrderEmailLinks("http://localhost:5173").customer(id).startsWith("http://localhost:5173/#/track-order"));
 }
 @Test void unsafeConfiguredBasesAreRejected(){
  for(String base:List.of("javascript:alert(1)","//evil.test/","http://restaurant.example.test","https://user:secret@example.test","https://example.test/?redirect=https://evil.test","https://example.test:0/"))assertThrows(IllegalArgumentException.class,()->new OrderEmailLinks(base).customer(UUID.randomUUID()),base);
 }
 @Test void htmlEscapesNotesAndContainsTheSameActionUrl(){
  String url="https://restaurant.example.test/#/track-order?order="+UUID.randomUUID();String plain="Hello <script>alert('x')</script> & Guest\nOrder details\n\nTrack your order\n"+url+"\n";
  String html=OrderEmailHtml.render("Order #ABCD1234",plain,"Track your order",url);
  assertTrue(html.contains("<br>"));assertFalse(html.contains("<script>"));assertTrue(html.contains("&lt;script&gt;"));assertTrue(html.contains("href=\""+url+"\""));assertTrue(html.contains(">Track your order</a>"));
 }
}
