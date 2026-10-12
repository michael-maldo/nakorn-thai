package au.com.nakornthai.payment.createpayment;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import au.com.nakornthai.notification.infrastructure.TwilioVerifyClient;
import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeAvailabilityTest {
 @Test void disabledPaypalCannotStartButExistingProviderOrderCanBeChecked() {
  var em=org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class);
  var access=org.mockito.Mockito.mock(au.com.nakornthai.ordering.infrastructure.OrderAccessService.class);
  var paypal=org.mockito.Mockito.mock(PayPalPaymentProvider.class);
  var handler=new CreatePaymentHandler(em,access,paypal,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),false,"","");
  UUID id=UUID.randomUUID();var order=new au.com.nakornthai.ordering.infrastructure.OrderJpaEntity();
  order.setId(id);order.setStatus("NEW");order.setPaymentMethod("PAYPAL");order.setTotalMinor(100);
  var payment=new au.com.nakornthai.payment.infrastructure.OrderPaymentJpaEntity();payment.setOrderId(id);payment.setMethod("PAYPAL");payment.setProviderOrderId("PP1");payment.setStatus("PENDING");
  org.mockito.Mockito.when(em.find(au.com.nakornthai.ordering.infrastructure.OrderJpaEntity.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
  org.mockito.Mockito.when(em.find(au.com.nakornthai.payment.infrastructure.OrderPaymentJpaEntity.class,id)).thenReturn(payment);
  assertEquals(503,assertThrows(ResponseStatusException.class,()->handler.start(id,"token","PAYPAL")).getStatusCode().value());
  var details=new tools.jackson.databind.json.JsonMapper().valueToTree(Map.of("id","PP1","intent","CAPTURE","status","CREATED","purchase_units",List.of(Map.of("custom_id",id.toString(),"amount",Map.of("currency_code","AUD","value","1.00")))));
  org.mockito.Mockito.when(paypal.details("PP1")).thenReturn(details);
  assertEquals(false,handler.check(id,"token",true,false).get("paid"));
  org.mockito.Mockito.verify(paypal).details("PP1");org.mockito.Mockito.verify(paypal,org.mockito.Mockito.never()).create(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyLong());
  org.mockito.Mockito.verify(paypal,org.mockito.Mockito.never()).capture(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
 }

 @Test void paypalTracksConfigurationEnablementAndLiveValidation(){
  var values=new HashMap<String,String>();RuntimeConfiguration config=()->new RuntimeConfiguration.Snapshot(values);
  var provider=new PayPalPaymentProvider(config);
  assertFalse(provider.enabled());
  values.putAll(Map.of("environment","sandbox","clientId","test-client","clientSecret","test-secret","returnUrl","https://example.test/#/order-confirmation","paypalEnabled","true"));
  assertTrue(provider.enabled());values.put("paypalEnabled","false");assertFalse(provider.enabled());
  assertEquals(503,assertThrows(ResponseStatusException.class,()->provider.create(UUID.randomUUID(),100)).getStatusCode().value());
  values.put("paypalEnabled","true");values.put("clientSecret","");assertFalse(provider.enabled());
  assertEquals(503,assertThrows(ResponseStatusException.class,()->provider.create(UUID.randomUUID(),100)).getStatusCode().value());
  values.put("clientSecret","test-secret");assertTrue(provider.enabled());
  values.put("environment","live");values.put("paypalDashboardManaged","true");assertFalse(provider.enabled());values.put("paypalValidated","true");assertTrue(provider.enabled());
 }
 @Test void twilioChannelsAreIndependentAndRefreshCredentials(){
  var values=new HashMap<String,String>();var provider=new TwilioVerifyClient(()->new RuntimeConfiguration.Snapshot(values));
  assertFalse(provider.enabled("sms"));values.putAll(Map.of("accountSid","AC"+"a".repeat(32),"authToken","test-token","verifyServiceSid","VA"+"b".repeat(32),"verifySmsEnabled","true","verifyEmailEnabled","true"));
  assertTrue(provider.enabled("sms"));assertTrue(provider.enabled("email"));values.put("verifySmsEnabled","false");assertFalse(provider.enabled("sms"));assertTrue(provider.enabled("email"));
  assertEquals(503,assertThrows(ResponseStatusException.class,()->provider.start("+61412345678","sms")).getStatusCode().value());
  values.put("authToken","");assertFalse(provider.enabled("email"));values.put("authToken","test-token");assertTrue(provider.enabled("email"));
 }
}
