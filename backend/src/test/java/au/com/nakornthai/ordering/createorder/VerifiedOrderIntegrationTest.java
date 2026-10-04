package au.com.nakornthai.ordering.createorder;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.notification.orderconfirmation.*;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "ONLINE_ORDERING_ENABLED=true",
    "PAYPAL_ENABLED=true",
    "PAYPAL_ENV=sandbox",
    "PAYPAL_CLIENT_ID=test-client-id",
    "PAYPAL_CLIENT_SECRET=test-client-secret",
    "PAYID_ENABLED=true",
    "PAYID_IDENTIFIER=merchant@example.test",
    "PAYID_ACCOUNT_NAME=Test"
})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class VerifiedOrderIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){
  p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
 }
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired jakarta.persistence.EntityManager em;
 @Autowired SendOrderConfirmationHandler notifications;
 @Autowired PlatformTransactionManager transactionManager;
 @MockitoBean TwilioVerifyClient provider;@MockitoBean PayPalPaymentProvider paypal;
 @MockitoBean Clock clock;
 UUID id,variation,collection;final String token="a".repeat(64);
 final Instant now=Instant.parse("2030-10-01T08:00:00Z");
 @BeforeEach void fixture(){
  when(clock.instant()).thenReturn(now);when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  when(provider.enabled(anyString())).thenReturn(true);when(provider.start(anyString(),anyString())).thenReturn("test-provider-reference");when(provider.check(anyString(),eq("123456"))).thenReturn(true);
  jdbc.update("DELETE FROM restaurant_closed_date");jdbc.update("DELETE FROM restaurant_opening_hours");
  for(int day=1;day<=7;day++){
   jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,?,'00:00','12:00')",UUID.randomUUID(),day);
   jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,?,'12:00','00:00')",UUID.randomUUID(),day);
  }
  id=UUID.randomUUID();variation=UUID.randomUUID();collection=UUID.randomUUID();UUID category=UUID.randomUUID(),item=UUID.randomUUID();
  jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'Verification',?)",category,"verified-"+category);
  jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Curry',?,'Test','PUBLISHED')",item,category,"verified-"+item);
  jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Standard',1990,true)",variation,item);
  jdbc.update("INSERT INTO menu_collection(id,name,slug,status) VALUES (?,'Pickup',?,'PUBLISHED')",collection,"verified-"+collection);
  jdbc.update("INSERT INTO menu_collection_item(collection_id,menu_item_id) VALUES (?,?)",collection,item);
 }
 UUID challenge(String channel,String destination,boolean success,Instant expiry){
  UUID challenge=UUID.randomUUID();
  jdbc.update("INSERT INTO contact_verification(id,channel,destination_hash,provider_reference,created_at,expires_at,verified_at) VALUES (?,?,?,'fake',?,?,?)",challenge,channel,ContactDestination.hash(channel+":"+ContactDestination.normalize(channel,destination)),java.sql.Timestamp.from(now),java.sql.Timestamp.from(expiry),success?java.sql.Timestamp.from(now):null);
  return challenge;
 }
 Map<String,Object> request(UUID challenge,String phone,String email,String method){
  var body=new HashMap<String,Object>();body.put("requestId",id);body.put("trackingToken",token);body.put("customerName","Guest");body.put("phone",phone);body.put("phoneVerificationId",challenge);body.put("email",email);body.put("paymentMethod",method);body.put("notes","");
  body.put("items",List.of(Map.of("variationId",variation,"collectionId",collection,"quantity",1,"expectedUnitPriceMinor",1990,"selectedOptions",List.of())));
  return body;
 }
 ResultActions create(UUID challenge,String phone,String email,String method) throws Exception {
  return mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(request(challenge,phone,email,method))));
 }
 UUID verified(){return challenge("SMS","0412 345 678",true,now.plusSeconds(600));}
 void flush(){em.flush();em.clear();}
 int jobs(String type){flush();return jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE order_id=? AND type=?",Integer.class,id,type);}
 void transition(String role,String next,int expected) throws Exception {
  flush();long version=jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id);
  mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("staff").roles(role)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("version",version,"status",next,"pickupMinutes",20,"paymentCollected",true,"reason","Restaurant unable to fulfil")))).andExpect(status().is(expected));
 }

 @Test void noVerificationAndForgedBooleanCannotCreateOrder() throws Exception {
  var forged=request(null,"0412345678",null,"PAY_AT_RESTAURANT");forged.put("phoneVerified",true);
  mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(forged))).andExpect(status().isBadRequest());
  assertEquals(0,jobs("ORDER_RECEIVED"));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
 }
 @Test void rejectsMissingUnverifiedExpiredWrongDestinationAndEmailChallenges() throws Exception {
  for(UUID challenge:List.of(UUID.randomUUID(),challenge("SMS","0412345678",false,now.plusSeconds(600)),challenge("SMS","0412345678",true,now.minusSeconds(1)),challenge("SMS","0499999999",true,now.plusSeconds(600)),challenge("EMAIL","Guest@example.test",true,now.plusSeconds(600)))){
   create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());
  }
  assertEquals(0,jobs("ORDER_RECEIVED"));
 }
 @Test void verificationApiSmsThenOrderIsNormalizedAndSingleUse() throws Exception {
  mvc.perform(get("/api/orders/contact-verifications/options")).andExpect(status().isOk()).andExpect(jsonPath("$.sms").value(true));
  String result=mvc.perform(post("/api/orders/contact-verifications").with(csrf()).contentType("application/json").content("{\"channel\":\"SMS\",\"destination\":\"0412 345 678\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  UUID challenge=UUID.fromString(json.readTree(result).path("id").asText());
  verify(provider).start("+61412345678","sms");
  mvc.perform(post("/api/orders/contact-verifications/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
  create(challenge,"(0412) 345-678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.paidAt").doesNotExist());
  flush();assertEquals("+61412345678",jdbc.queryForObject("SELECT phone FROM restaurant_order WHERE id=?",String.class,id));
  assertTrue(jdbc.queryForObject("SELECT phone_verified FROM restaurant_order WHERE id=?",Boolean.class,id));
  assertEquals(id,jdbc.queryForObject("SELECT consumed_order_id FROM contact_verification WHERE id=?",UUID.class,challenge));
  assertEquals(1,jobs("ORDER_RECEIVED"));
  UUID original=id;id=UUID.randomUUID();create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());id=original;
  mvc.perform(post("/api/orders/contact-verifications/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isBadRequest());
 }
 @Test void changedPhoneOrAustralianLandlineCannotUseVerifiedMobile() throws Exception {
  UUID challenge=verified();create(challenge,"0499999999",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());
  create(challenge,"03 9123 4567",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());
  create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());
 }
 @Test void optionalUnverifiedEmailGetsNotificationAndReplayDoesNotDuplicate() throws Exception {
  UUID challenge=verified();
  create(challenge,"0412345678","Guest@EXAMPLE.TEST","PAY_AT_RESTAURANT").andExpect(status().isCreated());
  assertEquals(2,jobs("ORDER_RECEIVED"));
  assertEquals("Guest@example.test",jdbc.queryForObject("SELECT recipient FROM notification_delivery WHERE order_id=? AND channel='EMAIL'",String.class,id));
  assertEquals("+61412345678",jdbc.queryForObject("SELECT recipient FROM notification_delivery WHERE order_id=? AND channel='SMS'",String.class,id));
  jdbc.update("UPDATE contact_verification SET expires_at=? WHERE id=?",java.sql.Timestamp.from(now.minusSeconds(1)),challenge);
  create(challenge,"0412345678","Guest@EXAMPLE.TEST","PAY_AT_RESTAURANT").andExpect(status().isCreated());
  assertEquals(2,jobs("ORDER_RECEIVED"));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
  String message=jdbc.queryForObject("SELECT body FROM notification_delivery WHERE order_id=? AND channel='SMS'",String.class,id);
  assertTrue(message.contains("not been accepted"));assertFalse(message.contains(token));assertFalse(message.contains(id.toString()));
  mvc.perform(get("/api/orders/"+id).header("X-Order-Token","b".repeat(64))).andExpect(status().isNotFound());
  mvc.perform(get("/api/orders/"+id).header("X-Order-Token",token)).andExpect(status().isOk());
 }
 @Test void successfulTransitionsQueueOnlyAcceptedReadyAndCancelled() throws Exception {
  create(verified(),"0412345678","guest@example.test","PAY_AT_RESTAURANT").andExpect(status().isCreated());
  transition("BOH","ACCEPTED",403);assertEquals(0,jobs("ORDER_ACCEPTED"));
  transition("FOH","ACCEPTED",204);assertEquals(2,jobs("ORDER_ACCEPTED"));
  String message=jdbc.queryForObject("SELECT body FROM notification_delivery WHERE order_id=? AND type='ORDER_ACCEPTED' AND channel='SMS'",String.class,id);assertTrue(message.contains("Estimated pickup:"));
  transition("FOH","ACCEPTED",409);assertEquals(2,jobs("ORDER_ACCEPTED"));
  transition("BOH","PREPARING",204);assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE order_id=?",Integer.class,id));
  transition("BOH","READY",204);assertEquals(2,jobs("ORDER_READY"));
  transition("FOH","CANCELLED",204);assertEquals(2,jobs("ORDER_CANCELLED"));
  assertTrue(jdbc.queryForObject("SELECT body FROM notification_delivery WHERE order_id=? AND type='ORDER_CANCELLED' AND channel='SMS'",String.class,id).contains("does not automatically refund"));
  transition("FOH","CANCELLED",409);assertEquals(2,jobs("ORDER_CANCELLED"));
 }
 @Test void completedHasNoAdditionalNotification() throws Exception {
  create(verified(),"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());
  transition("FOH","ACCEPTED",204);transition("BOH","PREPARING",204);transition("BOH","READY",204);transition("FOH","COMPLETED",204);
  assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE order_id=?",Integer.class,id));
 }
 @Test void contactVerificationDoesNotPayOrPermitAcceptanceOfOnlineMethods() throws Exception {
  for(String method:List.of("PAYPAL","PAYID")){
   id=UUID.randomUUID();create(verified(),"0412345678",null,method).andExpect(status().isCreated()).andExpect(jsonPath("$.paidAt").doesNotExist());
   transition("FOH","ACCEPTED",409);assertEquals(0,jobs("ORDER_ACCEPTED"));assertEquals(1,jobs("ORDER_RECEIVED"));
  }
 }
 @Test void invalidCheckoutDoesNotConsumeChallenge() throws Exception {
  UUID challenge=verified();var body=request(challenge,"0412345678",null,"PAY_AT_RESTAURANT");body.put("items",List.of(Map.of("variationId",variation,"collectionId",collection,"quantity",1,"expectedUnitPriceMinor",1)));
  mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
  assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM contact_verification WHERE id=?",UUID.class,challenge));
  create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());
 }
 @Test void reservationConsumedChallengeCannotCreateOrder() throws Exception {
  UUID reservation=UUID.randomUUID(),challenge=verified();
  jdbc.update("INSERT INTO reservation(id,customer_name,party_size,requested_at,created_at,updated_at) VALUES (?,'Guest',2,'2030-10-02 19:00',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",reservation);
  jdbc.update("UPDATE contact_verification SET consumed_by=? WHERE id=?",reservation,challenge);
  create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());
 }
 @Test void orderConsumedChallengeCannotCreateReservation() throws Exception {
  UUID challenge=verified();create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());flush();
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"customerName","Guest","phone","0412345678","phoneVerificationId",challenge,"partySize",2,"requestedAt","2030-10-02T19:00:00","notes","")))).andExpect(status().isBadRequest());
 }
 @Test void repeatedEnqueueIsIdempotentAndVerificationEndpointsRequireCsrf() throws Exception {
  create(verified(),"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());
  notifications.handle(new SendOrderConfirmationCommand(id,NotificationType.ORDER_RECEIVED,"+61412345678",null,"Guest",1990,null,ZoneId.of("Australia/Melbourne")));
  assertEquals(1,jobs("ORDER_RECEIVED"));
  mvc.perform(post("/api/orders/contact-verifications").contentType("application/json").content("{\"channel\":\"SMS\",\"destination\":\"0412345678\"}")).andExpect(status().isForbidden());
 }
 @Test void orderingAndReservationAliasesShareRequestCooldown() throws Exception {
  mvc.perform(post("/api/orders/contact-verifications").with(csrf()).contentType("application/json").content("{\"channel\":\"SMS\",\"destination\":\"0412345678\"}")).andExpect(status().isOk());
  mvc.perform(post("/api/reservations/contact-verifications").with(csrf()).contentType("application/json").content("{\"channel\":\"SMS\",\"destination\":\"+61412345678\"}")).andExpect(status().isTooManyRequests());
  verify(provider,times(1)).start(anyString(),anyString());
 }
 @Test void orderingCodeChecksPersistFiveAttemptLimit() throws Exception {
  UUID challenge=challenge("SMS","0412345678",false,now.plusSeconds(600));
  for(int i=0;i<6;i++)mvc.perform(post("/api/orders/contact-verifications/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"111111\"}")).andExpect(status().isBadRequest());
  flush();assertEquals(5,jdbc.queryForObject("SELECT attempts FROM contact_verification WHERE id=?",Integer.class,challenge));
  verify(provider,times(5)).check("fake","111111");
  create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isBadRequest());
 }
 @Test void rollbackRestoresChallengeAndRemovesOrderAndNotificationWork() throws Exception {
  // Commit only the challenge independently, then roll back the actual creation
  // transaction and inspect PostgreSQL outside it (not the persistence context).
  var independent=new TransactionTemplate(transactionManager);
  independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  UUID challenge=independent.execute(status->verified());
  try {
   create(challenge,"0412345678",null,"PAY_AT_RESTAURANT").andExpect(status().isCreated());
   assertEquals(1,jobs("ORDER_RECEIVED"));
   assertEquals(id,jdbc.queryForObject("SELECT consumed_order_id FROM contact_verification WHERE id=?",UUID.class,challenge));
   TestTransaction.flagForRollback();TestTransaction.end();
   assertNull(jdbc.queryForObject("SELECT consumed_order_id FROM contact_verification WHERE id=?",UUID.class,challenge));
   assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE id=?",Integer.class,id));
   assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE order_id=?",Integer.class,id));
  } finally {
   if(TestTransaction.isActive()){TestTransaction.flagForRollback();TestTransaction.end();}
   jdbc.update("DELETE FROM contact_verification WHERE id=?",challenge);
  }
 }
}
