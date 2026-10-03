package au.com.nakornthai.reservation.createreservation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class CreateReservationIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
 p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));}
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.notification.infrastructure.TwilioVerifyClient provider;

 String time=LocalDate.now(ZoneId.of("Australia/Melbourne")).plusDays(2)+"T18:00:00";
 String body(UUID id){
 UUID challenge=UUID.nameUUIDFromBytes((id+"verification").getBytes(java.nio.charset.StandardCharsets.UTF_8));
 if(jdbc.queryForObject("SELECT count(*) FROM contact_verification WHERE id=?",Integer.class,challenge)==0)
 jdbc.update("INSERT INTO contact_verification(id,channel,destination_hash,created_at,expires_at,verified_at) VALUES (?,'SMS',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '10 minutes',CURRENT_TIMESTAMP)",challenge,au.com.nakornthai.ordering.createorder.CreateOrderHandler.hash("SMS:+61400000000"));
 return "{\"requestId\":\""+id+"\",\"phoneVerificationId\":\""+challenge+"\",\"customerName\":\"Booking Test\",\"phone\":\"0400000000\",\"partySize\":4,\"requestedAt\":\""+time+"\",\"notes\":\"Window please\"}";}
 @org.junit.jupiter.api.BeforeEach void openingHours() {
  jdbc.update("DELETE FROM restaurant_closed_date");jdbc.update("DELETE FROM restaurant_opening_hours");
  for(int day=1;day<=7;day++)jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,?,'17:00','22:00')",UUID.randomUUID(),day);
 }
 @Test void closedBookingIsRejectedButSuccessfulReplayIsReturned() throws Exception {
  var id=UUID.randomUUID();
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated());
  jdbc.update("INSERT INTO restaurant_closed_date(id,closed_date) VALUES (?,?::date)",UUID.randomUUID(),time.substring(0,10));
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated());
  var next=UUID.randomUUID();
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(next)))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESTAURANT_CLOSED"));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE id=?",Integer.class,next));
 }
 @Test void persistsRetriesAndRestrictsAccess() throws Exception {
 var id=UUID.randomUUID();
 mvc.perform(post("/api/reservations").contentType("application/json").content(body(id))).andExpect(status().isForbidden());
 for(int i=0;i<2;i++)mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated()).andExpect(jsonPath("$.reference").value(id.toString())).andExpect(jsonPath("$.phone").doesNotExist());
 assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE id=?",Integer.class,id));
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id).replace("Window please","Different"))).andExpect(status().isConflict());
 String url="/api/staff/reservations?date="+time.substring(0,10);
 mvc.perform(get(url)).andExpect(status().isUnauthorized());
 mvc.perform(get(url).with(user("kitchen").roles("BOH"))).andExpect(status().isForbidden());
 mvc.perform(get(url).with(user("front").roles("FOH"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].customerName").value("Booking Test"));
 }
 @Test void validatesTimeAndPartySize() throws Exception {
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(UUID.randomUUID()).replace(time,"2020-01-01T18:00:00"))).andExpect(status().isBadRequest());
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(UUID.randomUUID()).replace("\"partySize\":4","\"partySize\":21"))).andExpect(status().isBadRequest());
 }
 @Test void staffWorkflowRejectsStaleAndInvalidTransitions() throws Exception {
 var id=UUID.randomUUID();mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated());
 var url="/api/staff/reservations/"+id;
 String confirmed="{\"version\":0,\"status\":\"CONFIRMED\",\"staffNote\":\"Called guest\"}";
 mvc.perform(patch(url).with(user("kitchen").roles("BOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isForbidden());
 mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isNoContent());
 // Flush the transaction before the next request to model separate HTTP transactions.
 em.flush();em.clear();
 mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isConflict());
 mvc.perform(patch(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(confirmed.replace("\"version\":0","\"version\":1").replace("CONFIRMED","SEATED"))).andExpect(status().isNoContent());
 em.flush();em.clear();
 assertEquals("SEATED",jdbc.queryForObject("SELECT status FROM reservation WHERE id=?",String.class,id));
 assertEquals("admin",jdbc.queryForObject("SELECT updated_by FROM reservation WHERE id=?",String.class,id));
 }
 @Test void confirmationQueuesOnlyVerifiedPhoneAndOnlyOnce() throws Exception {
  var id=UUID.randomUUID();String payload=body(id).replace("\"partySize\":4","\"email\":\"unverified@example.com\",\"partySize\":4");
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
  em.flush();assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=?",Integer.class,id));
  String update="{\"version\":0,\"status\":\"CONFIRMED\",\"staffNote\":\"\"}";
  mvc.perform(patch("/api/staff/reservations/"+id).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(update)).andExpect(status().isNoContent());
  em.flush();em.clear();
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",Integer.class,id));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND channel='EMAIL'",Integer.class,id));
  mvc.perform(patch("/api/staff/reservations/"+id).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(update.replace("\"version\":0","\"version\":1"))).andExpect(status().isConflict());
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=?",Integer.class,id));
 }
 @Test void consumedChallengeCannotCreateAnotherReservation() throws Exception {
  var id=UUID.randomUUID();String payload=body(id);
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload.replace(id.toString(),UUID.randomUUID().toString()))).andExpect(status().isBadRequest());
 }
 @Test void noProofIsRejectedByServer() throws Exception {
  var id=UUID.randomUUID();String payload=body(id).replaceAll("\"phoneVerificationId\":\"[^\"]+\",", "");
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isBadRequest());
 }
 @Test void publicVerificationCodeFlowAndCooldown() throws Exception {
  org.mockito.Mockito.when(provider.enabled("sms")).thenReturn(true);
  org.mockito.Mockito.when(provider.start("+61415998877","sms")).thenReturn("managed-reference");
  org.mockito.Mockito.when(provider.check("managed-reference","123456")).thenReturn(true);
  String start="{\"channel\":\"SMS\",\"destination\":\"0415 998 877\"}";
  String url="/api/reservations/contact-verifications";
  mvc.perform(post(url).contentType("application/json").content(start)).andExpect(status().isForbidden());
  var response=mvc.perform(post(url).with(csrf()).contentType("application/json").content(start)).andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt").exists()).andReturn();
  var json=new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString());String challenge=json.path("id").asText();
  mvc.perform(post(url).with(csrf()).contentType("application/json").content(start)).andExpect(status().isTooManyRequests());
  mvc.perform(post(url+"/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
  UUID id=UUID.randomUUID();String payload=body(id).replace("0400000000","0415 998 877").replace(UUID.nameUUIDFromBytes((id+"verification").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),challenge);
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
  em.flush();assertEquals(id,jdbc.queryForObject("SELECT consumed_by FROM contact_verification WHERE id=?",UUID.class,UUID.fromString(challenge)));
 }
 @Test void invalidCodesPersistAttemptLimit() throws Exception {
  org.mockito.Mockito.when(provider.enabled("email")).thenReturn(true);
  org.mockito.Mockito.when(provider.start("Case@example.com","email")).thenReturn("email-reference");
  String url="/api/reservations/contact-verifications";
  var response=mvc.perform(post(url).with(csrf()).contentType("application/json").content("{\"channel\":\"EMAIL\",\"destination\":\"Case@EXAMPLE.COM\"}")).andExpect(status().isOk()).andReturn();
  String challenge=new tools.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("id").asText();
  for(int i=0;i<6;i++)mvc.perform(post(url+"/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"123456\"}")).andExpect(status().isBadRequest());
  em.flush();assertEquals(5,jdbc.queryForObject("SELECT attempts FROM contact_verification WHERE id=?",Integer.class,UUID.fromString(challenge)));
  org.mockito.Mockito.verify(provider,org.mockito.Mockito.times(5)).check("email-reference","123456");
 }
 @Test void verifiedEmailCreatesRequestedReservationWithoutPhone() throws Exception {
  org.mockito.Mockito.when(provider.enabled("email")).thenReturn(true);
  org.mockito.Mockito.when(provider.start("EmailGuest@example.com","email")).thenReturn("email-booking-reference");
  org.mockito.Mockito.when(provider.check("email-booking-reference","123456")).thenReturn(true);
  String url="/api/reservations/contact-verifications";
  var response=mvc.perform(post(url).with(csrf()).contentType("application/json").content("{\"channel\":\"EMAIL\",\"destination\":\"EmailGuest@EXAMPLE.COM\"}"))
   .andExpect(status().isOk()).andReturn();
  var mapper=new tools.jackson.databind.ObjectMapper();
  UUID challenge=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("id").asText());
  mvc.perform(post(url+"/"+challenge+"/verify").with(csrf()).contentType("application/json").content("{\"code\":\"123456\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
  UUID id=UUID.randomUUID();
  String payload=mapper.writeValueAsString(java.util.Map.of("requestId",id,"customerName","Email Guest","email","EmailGuest@EXAMPLE.COM",
   "emailVerificationId",challenge,"partySize",4,"requestedAt",time,"notes",""));
  mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(payload)).andExpect(status().isCreated());
  em.flush();
  assertEquals("REQUESTED",jdbc.queryForObject("SELECT status FROM reservation WHERE id=?",String.class,id));
  assertEquals(true,jdbc.queryForObject("SELECT email_verified FROM reservation WHERE id=?",Boolean.class,id));
  assertEquals(false,jdbc.queryForObject("SELECT phone_verified FROM reservation WHERE id=?",Boolean.class,id));
  assertNull(jdbc.queryForObject("SELECT phone FROM reservation WHERE id=?",String.class,id));
  assertEquals("EmailGuest@example.com",jdbc.queryForObject("SELECT email FROM reservation WHERE id=?",String.class,id));
  assertEquals(id,jdbc.queryForObject("SELECT consumed_by FROM contact_verification WHERE id=?",UUID.class,challenge));
  assertNotNull(jdbc.queryForObject("SELECT verified_at FROM contact_verification WHERE id=?",java.sql.Timestamp.class,challenge));
  org.mockito.Mockito.verify(provider).check("email-booking-reference","123456");
 }
 @Autowired jakarta.persistence.EntityManager em;
}
