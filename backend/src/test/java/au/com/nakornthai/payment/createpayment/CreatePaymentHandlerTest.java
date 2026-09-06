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
 @BeforeEach void fixture(){id=UUID.randomUUID();jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,email,notes,total_minor,created_at,updated_at) VALUES (?,?,?,'Test Customer','0400000000','test@example.com','',1990,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,CreateOrderHandler.hash(token),"b".repeat(64));when(paypal.enabled()).thenReturn(true);when(verify.enabled(anyString())).thenReturn(true);when(verify.start(anyString(),anyString())).thenReturn("VE"+"a".repeat(32));}
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
  String command="{\"version\":"+jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id)+",\"bankReference\":\"BANK-123\"}";
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("kitchen").roles("BOH")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isForbidden());
  mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(command)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true));
  em.flush();assertEquals("front",jdbc.queryForObject("SELECT confirmed_by FROM order_payment WHERE order_id=?",String.class,id));
 }
 @Test void paypalUsesServerTotalAndCaptureIsIdempotent() throws Exception {
  method("PAYPAL");when(paypal.create(id,1990)).thenReturn(json.readTree("{\"id\":\"PP123\",\"links\":[{\"rel\":\"payer-action\",\"href\":\"https://www.sandbox.paypal.com/checkoutnow?token=PP123\"}]}"));
  start("PAYPAL");start("PAYPAL");verify(paypal,times(1)).create(id,1990);
  when(paypal.details("PP123")).thenReturn(json.readTree("{\"status\":\"APPROVED\"}"));
  when(paypal.capture("PP123",id)).thenReturn(json.readTree("{\"status\":\"COMPLETED\",\"purchase_units\":[{\"custom_id\":\""+id+"\",\"payments\":{\"captures\":[{\"id\":\"CAP1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"AUD\",\"value\":\"19.90\"}}]}}]}"));
  when(paypal.details("PP123")).thenReturn(json.readTree("{\"status\":\"APPROVED\"}"),json.readTree("{\"status\":\"COMPLETED\",\"purchase_units\":[{\"custom_id\":\""+id+"\",\"payments\":{\"captures\":[{\"id\":\"CAP1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"AUD\",\"value\":\"19.90\"}}]}}]}"));
  for(int i=0;i<2;i++)mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true));
  verify(paypal,times(1)).capture("PP123",id);
 }
 @Test void incorrectPaypalAmountNeverMarksPaid() throws Exception {
  var response=json.readTree("{\"status\":\"COMPLETED\",\"purchase_units\":[{\"custom_id\":\""+id+"\",\"payments\":{\"captures\":[{\"id\":\"CAP1\",\"status\":\"COMPLETED\",\"amount\":{\"currency_code\":\"AUD\",\"value\":\"0.01\"}}]}}]}");
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->PayPalPaymentProvider.validatedCapture(response,id,1990));
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
  method("PAYID");
  mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":0,\"status\":\"ACCEPTED\",\"pickupMinutes\":20,\"paymentCollected\":false,\"reason\":\"\"}")).andExpect(status().isConflict());
 }

}
