package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.restaurant.configuration.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BusinessWebsiteUrlTest {
 @Test void rejectsUnsafeOrAmbiguousURLsAndProductionHttp(){
  for(String url:List.of("http://localhost:5173","https://user:pass@example.test","https://example.test/?x=1","https://example.test/#/track-order","javascript:alert(1)","//example.test","https://example.test/a/../b","https://example.test/%2fother","https://example.test:99999","https://example.test//other"))assertThrows(IllegalArgumentException.class,()->BusinessWebsiteUrl.normalize(url,false));
  assertEquals("http://localhost:5173/",BusinessWebsiteUrl.normalize("http://localhost:5173",true));assertThrows(IllegalArgumentException.class,()->BusinessWebsiteUrl.normalize("http://external.test",true));
  assertEquals("https://example.test/app/",BusinessWebsiteUrl.normalize(" HTTPS://EXAMPLE.TEST/app ",false));
 }
 @Test void dashboardTakesPrecedenceAndPaymentCallbackIsNeverUsed(){
  Map<String,String> values=new HashMap<>();RuntimeConfiguration config=()->new RuntimeConfiguration.Snapshot(values);
  var env=new MockEnvironment().withProperty("nakorn.public-app-url","https://fallback.example.test/").withProperty("PAYPAL_RETURN_URL","https://payments.example.test/#/order-confirmation");env.setActiveProfiles("prod");var links=new OrderEmailLinks(config,env);var id=UUID.randomUUID();
  assertTrue(links.staff(id).startsWith("https://fallback.example.test/"));values.put("businessWebsiteUrl","https://business.example.test/app");assertTrue(links.customerToken("a".repeat(64)).startsWith("https://business.example.test/app/#/track-order?t="));
  values.put("businessWebsiteUrl","");assertTrue(links.staff(id).startsWith("https://fallback.example.test/"));
  env=new MockEnvironment().withProperty("nakorn.public-app-url","http://localhost:5173");env.setActiveProfiles("prod");var production=new OrderEmailLinks(config,env);assertThrows(IllegalArgumentException.class,()->production.staff(id));
 }
}
