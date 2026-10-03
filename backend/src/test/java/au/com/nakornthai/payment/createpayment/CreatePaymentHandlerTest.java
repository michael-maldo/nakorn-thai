package au.com.nakornthai.payment.createpayment;
import au.com.nakornthai.payment.infrastructure.*;
import au.com.nakornthai.notification.infrastructure.TwilioVerifyClient;
import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"PAYPAL_ENABLED=true","PAYID_ENABLED=true","PAYID_IDENTIFIER=merchant@example.com","PAYID_ACCOUNT_NAME=Test Restaurant"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class CreatePaymentHandlerTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired jakarta.persistence.EntityManager em;
 @MockitoBean PayPalPaymentProvider paypal;@MockitoBean TwilioVerifyClient verify;
 UUID id;String token="a".repeat(64);
 @BeforeEach void fixture(){id=UUID.randomUUID();jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,email,notes,total_minor,created_at,updated_at) VALUES (?,?,?,'Test Customer','0400000000','test@example.com','',1990,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,CreateOrderHandler.hash(token),"b".repeat(64));when(paypal.enabled()).thenReturn(true);when(paypal.validApprovalUrl(anyString())).thenAnswer(call->new PayPalPaymentProvider(false,"sandbox","","","http://localhost:5173/#/order-confirmation").validApprovalUrl(call.getArgument(0)));when(verify.enabled(anyString())).thenReturn(true);when(verify.start(anyString(),anyString())).thenReturn("VE"+"a".repeat(32));}
 void method(String method){jdbc.update("UPDATE restaurant_order SET payment_method=? WHERE id=?",method,id);em.clear();}
 void start(String method) throws Exception {mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content("{\"method\":\""+method+"\"}")).andExpect(status().isOk());}
 @Test void paymentRequiresTrackingAndCsrf() throws Exception {
  mvc.perform(post("/api/payments/"+id).header("X-Order-Token",token).contentType("application/json").content("{\"method\":\"PAYID\"}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token","c".repeat(64)).contentType("application/json").content("{\"method\":\"PAYID\"}")).andExpect(status().isNotFound());
  verifyNoInteractions(paypal);
 }
 @Test void payidRequiresStaffBankConfirmation() throws Exception {
  method("PAYID");start("PAYID");em.flush();em.clear();
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
  String command="{\"version\":"+jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id)+",\"bankReceiptChecked\":true,\"bankReference\":\"BANK-123\"}";
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("kitchen").roles("BOH")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isForbidden());
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true));
  em.flush();assertEquals("front",jdbc.queryForObject("SELECT confirmed_by FROM order_payment WHERE order_id=?",String.class,id));
  assertNotNull(jdbc.queryForObject("SELECT updated_at FROM order_payment WHERE order_id=?",Object.class,id));
  long version=jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id);
  mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",version,"status","ACCEPTED","pickupMinutes",20,"paymentCollected",false,"reason","")))).andExpect(status().isNoContent());
 }

 tools.jackson.databind.JsonNode provider(String state) throws Exception {return provider(state,"COMPLETED");}
 tools.jackson.databind.JsonNode provider(String state,String captureState) throws Exception {
  return json.valueToTree(Map.of("id","PP123","intent","CAPTURE","status",state,"links",List.of(Map.of("rel","payer-action","href","https://www.sandbox.paypal.com/checkoutnow?token=PP123")),"purchase_units",List.of(Map.of("custom_id",id.toString(),"amount",Map.of("currency_code","AUD","value","19.90"),"payments",Map.of("captures",List.of(Map.of("id","CAP1","final_capture",true,"status",captureState,"amount",Map.of("currency_code","AUD","value","19.90"))))))));
 }
 void paypalStart() throws Exception {method("PAYPAL");when(paypal.create(id,1990)).thenReturn(provider("CREATED"));start("PAYPAL");}
 void checkPaid(boolean paid) throws Exception {mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(paid));}
 @Test void paypalUsesServerTotalAndCaptureIsIdempotent() throws Exception {
  paypalStart();start("PAYPAL");verify(paypal,times(1)).create(id,1990);
  when(paypal.details("PP123")).thenReturn(provider("APPROVED"),provider("COMPLETED"));
  when(paypal.capture("PP123",id)).thenReturn(provider("COMPLETED"));
  checkPaid(true);checkPaid(true);verify(paypal,times(1)).capture("PP123",id);
  em.flush();assertEquals("CAP1",jdbc.queryForObject("SELECT confirmation_reference FROM order_payment WHERE order_id=?",String.class,id));
 }
 @Test void mismatchedProviderResponsesNeverCaptureOrMarkPaid() throws Exception {
  paypalStart();
  for(String bad:List.of(provider("APPROVED").toString().replace("PP123","WRONG"),provider("APPROVED").toString().replace(id.toString(),UUID.randomUUID().toString()),provider("APPROVED").toString().replace("AUD","USD"),provider("APPROVED").toString().replace("19.90","0.01"))){
   when(paypal.details("PP123")).thenReturn(json.readTree(bad));
   mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isConflict());
  }
  verify(paypal,never()).capture(anyString(),any());assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void unapprovedReturnRemainsPending() throws Exception {
  paypalStart();when(paypal.details("PP123")).thenReturn(provider("CREATED"));checkPaid(false);verify(paypal,never()).capture(anyString(),any());
 }

 @Test void incompleteProviderCaptureNeverMarksPaid() throws Exception {
  paypalStart();when(paypal.details("PP123")).thenReturn(provider("COMPLETED","PENDING"));checkPaid(false);
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void captureReceiptAlreadyAssociatedWithAnotherOrderRejected() throws Exception {
  UUID other=UUID.randomUUID();jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,created_at,updated_at) VALUES (?,?,?,'Other','0400000000','',1990,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",other,"d".repeat(64),"e".repeat(64));
  jdbc.update("INSERT INTO order_payment(order_id,method,status,confirmation_reference,updated_at) VALUES (?,'PAYPAL','PAID','CAP1',CURRENT_TIMESTAMP)",other);
  paypalStart();when(paypal.details("PP123")).thenReturn(provider("COMPLETED"));
  mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isConflict());
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void closedOrderCannotStartOrCapture() throws Exception {
  paypalStart();jdbc.update("UPDATE restaurant_order SET status='CANCELLED' WHERE id=?",id);em.clear();
  when(paypal.details("PP123")).thenReturn(provider("APPROVED"));checkPaid(false);verify(paypal,never()).capture(anyString(),any());
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isConflict());
 }
 @Test void disabledPaypalAndInvalidTrackingCannotStartPayment() throws Exception {
  method("PAYPAL");when(paypal.enabled()).thenReturn(false);
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isServiceUnavailable());
  mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token","wrong")).andExpect(status().isNotFound());
 }
 @Test void captureValidationRejectsMalformedMoneyCurrencyAndIdentifier() throws Exception {
  for(String bad:List.of(provider("COMPLETED").toString().replace("19.90","0.01"),provider("COMPLETED").toString().replace("AUD","USD"),provider("COMPLETED").toString().replace("CAP1",""),provider("COMPLETED").toString().replace("true","false"),provider("COMPLETED").toString().replace("19.90","garbage"))){
   assertThrows(org.springframework.web.server.ResponseStatusException.class,()->PayPalPaymentProvider.validatedCapture(json.readTree(bad),id,1990));
  }
  assertNull(PayPalPaymentProvider.validatedCapture(provider("APPROVED"),id,1990));
 }
 @Test void payidInstructionsNeverPayAndStaffMustCheckReceiptAndVersion() throws Exception {
  method("PAYID");
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYID")))).andExpect(status().isOk()).andExpect(jsonPath("$.payid").value("merchant@example.com")).andExpect(jsonPath("$.accountName").value("Test Restaurant")).andExpect(jsonPath("$.totalMinor").value(1990)).andExpect(jsonPath("$.reference").value(id.toString())).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.paid").value(false));
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
  String route="/api/staff/payments/"+id+"/payid-confirm";
  mvc.perform(post(route).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"bankReference","BANK1","bankReceiptChecked",false)))).andExpect(status().isBadRequest());
  mvc.perform(post(route).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",999,"bankReference","BANK1","bankReceiptChecked",true)))).andExpect(status().isConflict());
  mvc.perform(post(route).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"bankReference","BANK1","bankReceiptChecked",true)))).andExpect(status().isUnauthorized());
 }
 @Test void payidRepeatSameReceiptSafeDifferentReceiptRejected() throws Exception {
  method("PAYID");start("PAYID");String route="/api/staff/payments/"+id+"/payid-confirm";String command=json.writeValueAsString(Map.of("version",0,"bankReference","BANK1","bankReceiptChecked",true));
  for(int i=0;i<2;i++)mvc.perform(post(route).with(user("manager").roles("ADMIN")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true));
  mvc.perform(post(route).with(user("manager").roles("ADMIN")).with(csrf()).contentType("application/json").content(command.replace("BANK1","BANK2"))).andExpect(status().isConflict());
  em.flush();assertNotNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));assertEquals("manager",jdbc.queryForObject("SELECT confirmed_by FROM order_payment WHERE order_id=?",String.class,id));
 }
 @Test void payidReceiptCannotBeUsedForAnotherOrder() throws Exception {
  UUID other=UUID.randomUUID();jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,created_at,updated_at) VALUES (?,?,?,'Other','0400000000','',1990,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",other,"d".repeat(64),"e".repeat(64));
  jdbc.update("INSERT INTO order_payment(order_id,method,status,confirmation_reference,updated_at) VALUES (?,'PAYID','PAID','BANK1',CURRENT_TIMESTAMP)",other);
  method("PAYID");start("PAYID");
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"bankReference","BANK1","bankReceiptChecked",true)))).andExpect(status().isConflict());
 }

 @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void failedPaypalSetupPersistsIntentAndRetryUsesSameFoodOrder() throws Exception {
  try {
   method("PAYPAL");when(paypal.create(id,1990)).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"PayPal unavailable"));
   mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isBadGateway());
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM order_payment WHERE order_id=? AND status='PENDING'",Integer.class,id));
   doReturn(provider("CREATED")).when(paypal).create(id,1990);start("PAYPAL");
   assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
   assertEquals("PP123",jdbc.queryForObject("SELECT provider_order_id FROM order_payment WHERE order_id=?",String.class,id));
  }finally{jdbc.update("DELETE FROM order_payment WHERE order_id=?",id);jdbc.update("DELETE FROM restaurant_order WHERE id=?",id);}
 }
 @Test void staleUnresolvedPaypalSetupRequiresReviewRatherThanFreshCreate() throws Exception {
  method("PAYPAL");jdbc.update("INSERT INTO order_payment(order_id,method,status,updated_at) VALUES (?,'PAYPAL','PENDING',CURRENT_TIMESTAMP-INTERVAL '6 hours')",id);
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isConflict());
  verify(paypal,never()).create(any(),anyLong());
 }

 @Test void oldFoodOrderWithoutIntentCannotCreateFreshProviderPayment() throws Exception {
  method("PAYPAL");jdbc.update("UPDATE restaurant_order SET created_at=CURRENT_TIMESTAMP-INTERVAL '6 hours' WHERE id=?",id);em.clear();
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isConflict());
  verify(paypal,never()).create(any(),anyLong());
 }
 @Test void malformedCreateResponseRejectedWithoutMarkingPaid() throws Exception {
  method("PAYPAL");when(paypal.create(id,1990)).thenReturn(null);
  mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().isBadGateway());
  assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void payidClosedOrderCannotConfirmAndStaffCsrfRemainsRequired() throws Exception {
  method("PAYID");start("PAYID");String command=json.writeValueAsString(Map.of("version",0,"bankReference","BANK1","bankReceiptChecked",true));
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("front").roles("FOH")).contentType("application/json").content(command)).andExpect(status().isForbidden());
  jdbc.update("UPDATE restaurant_order SET status='CANCELLED' WHERE id=?",id);em.clear();
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isConflict());
 }

 @Test void startRejectsApprovalUrlsOutsideConfiguredProviderEnvironment() throws Exception {
  method("PAYPAL");
  for(String environment:List.of("sandbox","live")){
   var validator=new PayPalPaymentProvider(false,environment,"","","https://example.test/#/order-confirmation");
   when(paypal.validApprovalUrl(anyString())).thenAnswer(call->validator.validApprovalUrl(call.getArgument(0)));
   for(String url:List.of("https://www.sandbox.paypal.com/checkoutnow?token=PP123","https://www.paypal.com/checkoutnow?token=PP123","http://www.paypal.com/checkoutnow?token=PP123","https://evil.test/checkoutnow?token=PP123")){
    when(paypal.create(id,1990)).thenReturn(json.readTree(provider("CREATED").toString().replace("https://www.sandbox.paypal.com/checkoutnow?token=PP123",url)));
    mvc.perform(post("/api/payments/"+id).with(csrf()).header("X-Order-Token",token).contentType("application/json").content(json.writeValueAsString(Map.of("method","PAYPAL")))).andExpect(status().is(validator.validApprovalUrl(url)?200:502));
    em.flush();em.clear();
    assertEquals(validator.validApprovalUrl(url)?"PP123":null,jdbc.queryForObject("SELECT provider_order_id FROM order_payment WHERE order_id=?",String.class,id));
    jdbc.update("DELETE FROM order_payment WHERE order_id=?",id);
   }
  }
 }
 @Test void verifiedSmsGrantsTrackingAndCannotBeReplayed() throws Exception {
  var result=mvc.perform(post("/api/order-verification/start").with(csrf()).contentType("application/json").content("{\"orderId\":\""+id+"\",\"channel\":\"sms\"}")).andExpect(status().isOk()).andReturn();
  String challenge=json.readTree(result.getResponse().getContentAsString()).path("challengeId").asText();org.mockito.Mockito.verify(verify).start("+61400000000","sms");
  when(verify.check(anyString(),eq("123456"))).thenReturn(true);
  String body="{\"challengeId\":\""+challenge+"\",\"code\":\"123456\"}";
  var checked=mvc.perform(post("/api/order-verification/check").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andReturn();
  String recovered=json.readTree(checked.getResponse().getContentAsString()).path("trackingToken").asText();
  mvc.perform(get("/api/orders/"+id).header("X-Order-Token",recovered)).andExpect(status().isOk());
  mvc.perform(post("/api/order-verification/check").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
 }
 @Test void emailUsesSavedAddressAndSendingIsRateLimited() throws Exception {
  String body="{\"orderId\":\""+id+"\",\"channel\":\"email\"}";
  mvc.perform(post("/api/order-verification/start").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
  org.mockito.Mockito.verify(verify).start("test@example.com","email");
  mvc.perform(post("/api/order-verification/start").with(csrf()).contentType("application/json").content(body)).andExpect(status().isTooManyRequests());
 }
 @Test void wrongCodesExhaustAttemptsWithoutGrantingAccess() throws Exception {
  var result=mvc.perform(post("/api/order-verification/start").with(csrf()).contentType("application/json").content("{\"orderId\":\""+id+"\",\"channel\":\"sms\"}")).andExpect(status().isOk()).andReturn();
  String challenge=json.readTree(result.getResponse().getContentAsString()).path("challengeId").asText();
  for(int i=0;i<6;i++)mvc.perform(post("/api/order-verification/check").with(csrf()).contentType("application/json").content("{\"challengeId\":\""+challenge+"\",\"code\":\"999999\"}")).andExpect(status().isBadRequest());
  org.mockito.Mockito.verify(verify,times(5)).check(anyString(),eq("999999"));
  em.flush();assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM order_tracking_grant WHERE order_id=?",Integer.class,id));
 }

 @Test void unpaidOnlineOrdersCannotBeAccepted() throws Exception {
  for(String online:List.of("PAYPAL","PAYID")){
   method(online);
   mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"status","ACCEPTED","pickupMinutes",20,"paymentCollected",false,"reason","")))).andExpect(status().isConflict());
  }
 }
}
