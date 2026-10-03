package au.com.nakornthai.payment.createpayment;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import au.com.nakornthai.notification.infrastructure.TwilioVerifyClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class ProviderContractsTest {
 @Test void paypalSerializesAuthoritativeAudOrderAndOAuth() {
  var builder=RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new PayPalPaymentProvider(true,"sandbox","client","secret","http://localhost:5173/#/order-confirmation");
  ReflectionTestUtils.setField(provider,"api",builder.build());var id=UUID.randomUUID();
  server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token")).andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Basic Y2xpZW50OnNlY3JldA==")).andExpect(content().string("grant_type=client_credentials")).andRespond(withSuccess("{\"access_token\":\"access\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders")).andExpect(header("Authorization","Bearer access")).andExpect(header("PayPal-Request-Id",id.toString())).andExpect(jsonPath("$.payment_source.paypal.experience_context.return_url").value("http://localhost:5173/?paypal=return#/order-confirmation")).andExpect(jsonPath("$.payment_source.paypal.experience_context.cancel_url").value("http://localhost:5173/?paypal=cancel#/order-confirmation")).andExpect(jsonPath("$.purchase_units[0].custom_id").value(id.toString())).andExpect(jsonPath("$.purchase_units[0].amount.value").value("19.90")).andExpect(jsonPath("$.purchase_units[0].amount.currency_code").value("AUD")).andRespond(withSuccess("{\"id\":\"PP1\"}",MediaType.APPLICATION_JSON));
  assertEquals("PP1",provider.create(id,1990).path("id").asText());server.verify();
 }

 @Test void captureUsesDeterministicIdempotencyAndCorrectEndpoint() {
  var builder=RestClient.builder().baseUrl("https://api-m.sandbox.paypal.com");var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new PayPalPaymentProvider(true,"sandbox","client","secret","http://localhost:5173/#/order-confirmation");ReflectionTestUtils.setField(provider,"api",builder.build());UUID id=UUID.randomUUID();
  String key=UUID.nameUUIDFromBytes(("capture:"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
  for(int i=0;i<2;i++){
   server.expect(requestTo("https://api-m.sandbox.paypal.com/v1/oauth2/token")).andRespond(withSuccess("{\"access_token\":\"access\"}",MediaType.APPLICATION_JSON));
   server.expect(requestTo("https://api-m.sandbox.paypal.com/v2/checkout/orders/PP1/capture")).andExpect(method(HttpMethod.POST)).andExpect(header("PayPal-Request-Id",key)).andExpect(header("Prefer","return=representation")).andExpect(header("Authorization","Bearer access")).andRespond(withSuccess("{\"status\":\"COMPLETED\"}",MediaType.APPLICATION_JSON));
  }
  provider.capture("PP1",id);provider.capture("PP1",id);server.verify();
 }
 @Test void liveRequiresHttpsAndCorrectHashRoute() {
  assertThrows(IllegalArgumentException.class,()->new PayPalPaymentProvider(true,"live","client","secret","http://localhost/#/order-confirmation"));
  assertThrows(IllegalArgumentException.class,()->new PayPalPaymentProvider(true,"sandbox","client","secret","http://localhost/#/menu"));
 }

 @Test void approvalUrlsMustMatchConfiguredEnvironmentAndUseHttps() {
  var sandbox=new PayPalPaymentProvider(false,"sandbox","","","https://example.test/#/order-confirmation");
  var live=new PayPalPaymentProvider(false,"live","","","https://example.test/#/order-confirmation");
  for(String host:java.util.List.of("sandbox.paypal.com","www.sandbox.paypal.com")){
   assertTrue(sandbox.validApprovalUrl("https://"+host+"/checkoutnow"));assertFalse(live.validApprovalUrl("https://"+host+"/checkoutnow"));
  }
  for(String host:java.util.List.of("paypal.com","www.paypal.com")){
   assertTrue(live.validApprovalUrl("https://"+host+"/checkoutnow"));assertFalse(sandbox.validApprovalUrl("https://"+host+"/checkoutnow"));
  }
  for(var provider:java.util.List.of(sandbox,live)){
   for(String bad:java.util.List.of("http://www.paypal.com/checkoutnow","http://www.sandbox.paypal.com/checkoutnow","https://evil.test/","https://paypal.com.evil.test/","https://user@www.paypal.com/","https://www.sandbox.paypal.com:444/","not a URL"))assertFalse(provider.validApprovalUrl(bad),bad);
   assertFalse(provider.validApprovalUrl(null));
  }
 }
 @Test void twilioUsesVerifySidForCheckingWithoutLoggingOrReturningCode() {
  String service="VA"+"a".repeat(32),sid="VE"+"b".repeat(32);
  var builder=RestClient.builder().baseUrl("https://verify.twilio.com/v2");var server=MockRestServiceServer.bindTo(builder).build();
  var provider=new TwilioVerifyClient("AC"+"a".repeat(32),"secret",service,true,true);ReflectionTestUtils.setField(provider,"api",builder.build());
  server.expect(requestTo("https://verify.twilio.com/v2/Services/"+service+"/Verifications")).andExpect(method(HttpMethod.POST)).andExpect(content().string("To=test%40example.com&Channel=email")).andRespond(withSuccess("{\"sid\":\""+sid+"\"}",MediaType.APPLICATION_JSON));
  server.expect(requestTo("https://verify.twilio.com/v2/Services/"+service+"/VerificationCheck")).andExpect(content().string("VerificationSid="+sid+"&Code=123456")).andRespond(withSuccess("{\"status\":\"approved\"}",MediaType.APPLICATION_JSON));
  assertEquals(sid,provider.start("test@example.com","email"));
  assertTrue(provider.check(sid,"123456"));server.verify();
 }
}
