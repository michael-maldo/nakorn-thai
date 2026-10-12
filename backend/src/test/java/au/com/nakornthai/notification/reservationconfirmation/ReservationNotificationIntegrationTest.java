package au.com.nakornthai.notification.reservationconfirmation;

import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryWorker;
import au.com.nakornthai.restaurant.infrastructure.RestaurantSettingsJpaEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class ReservationNotificationIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv("DB_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired EntityManager em;@Autowired PlatformTransactionManager manager;@Autowired NotificationDeliveryWorker worker;
 @MockitoBean EmailSender email;@MockitoBean SmsSender sms;@MockitoBean Clock clock;
 @MockitoBean au.com.nakornthai.restaurant.configuration.IntegrationDiagnostics diagnostics;
 @MockitoBean(name="taskScheduler") org.springframework.scheduling.TaskScheduler scheduler;
 TransactionTemplate tx;Map<String,String> original;String originalTimezone;
 Set<UUID> ids=ConcurrentHashMap.newKeySet();UUID hours;
 final Instant now=Instant.parse("2030-10-03T00:00:00Z");
 @BeforeEach void fixture(){
  when(clock.instant()).thenReturn(now);when(clock.getZone()).thenReturn(ZoneOffset.UTC);tx=new TransactionTemplate(manager);
  tx.executeWithoutResult(status->{var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);original=new HashMap<>(settings.getOperationalConfiguration());originalTimezone=settings.getTimezone();var fields=new HashMap<>(original);fields.putAll(Map.of("reservationsEnabled","true","reservationPhoneRequired","false","reservationSms","false","reservationEmail","true","staffReservationEmail","true","staffReservationEmailAddress","staff@example.test","businessWebsiteUrl","https://restaurant.example.test/"));settings.setOperationalConfiguration(fields);settings.setTimezone("Australia/Melbourne");});
  hours=UUID.randomUUID();jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,4,'17:00','22:00')",hours);
 }
 @AfterEach void cleanup(){
  for(UUID id:ids){jdbc.update("DELETE FROM notification_delivery WHERE reservation_id=?",id);jdbc.update("DELETE FROM reservation WHERE id=?",id);}
  jdbc.update("DELETE FROM restaurant_opening_hours WHERE id=?",hours);
  tx.executeWithoutResult(status->{var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);settings.setOperationalConfiguration(original);settings.setTimezone(originalTimezone);});
 }
 void settings(Map<String,String> changes){tx.executeWithoutResult(status->{var settings=em.find(RestaurantSettingsJpaEntity.class,(short)1);var fields=new HashMap<>(settings.getOperationalConfiguration());fields.putAll(changes);settings.setOperationalConfiguration(fields);});}
 String body(UUID id){ids.add(id);return "{\"requestId\":\""+id+"\",\"customerName\":\"Guest <&>\",\"phone\":\"0412345678\",\"email\":\"guest@example.test\",\"partySize\":4,\"requestedAt\":\"2030-10-10T19:00:00\",\"notes\":\"Window <script>please</script>\"}";}
 int create(String body)throws Exception{return mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}
 int change(UUID id,String status,long version)throws Exception{return mvc.perform(patch("/api/staff/reservations/"+id).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version+",\"status\":\""+status+"\",\"staffNote\":\"INTERNAL PRIVATE NOTE\"}")).andReturn().getResponse().getStatus();}
 int jobs(UUID id){return jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=?",Integer.class,id);}
 @Test void committedCreationAndConcurrentReplayEnqueueOneJobPerPurpose()throws Exception{
  UUID id=UUID.randomUUID();String payload=body(id);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{var a=executor.submit(()->{start.await();return create(payload);});var b=executor.submit(()->{start.await();return create(payload);});start.countDown();assertEquals(201,a.get(10,TimeUnit.SECONDS));assertEquals(201,b.get(10,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
  assertEquals(2,jobs(id));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE id=?",Integer.class,id));
  assertEquals(409,create(payload.replace("Window","Different")));assertEquals(2,jobs(id));
  assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",Integer.class,id));
 }
 @Test void concurrentStatusUpdatesPermitOneTransitionAndOneCustomerJob()throws Exception{
  UUID id=UUID.randomUUID();assertEquals(201,create(body(id)));long version=jdbc.queryForObject("SELECT version FROM reservation WHERE id=?",Long.class,id);
  var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
  try{var a=executor.submit(()->{start.await();return change(id,"CONFIRMED",version);});var b=executor.submit(()->{start.await();return change(id,"CONFIRMED",version);});start.countDown();assertEquals(Set.of(204,409),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));}finally{executor.shutdownNow();}
  assertEquals(3,jobs(id));assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE reservation_id=? AND type='RESERVATION_CONFIRMED'",Integer.class,id));
  String plain=jdbc.queryForObject("SELECT body FROM notification_delivery WHERE reservation_id=? AND type='RESERVATION_CONFIRMED'",String.class,id);assertFalse(plain.contains("INTERNAL PRIVATE NOTE"));
 }
 @Test void rollbackRemovesReservationAndBothPurposes(){UUID id=UUID.randomUUID();tx.executeWithoutResult(status->{assertDoesNotThrow(()->assertEquals(201,create(body(id))));em.flush();assertEquals(2,jobs(id));status.setRollbackOnly();});assertEquals(0,jobs(id));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE id=?",Integer.class,id));verifyNoInteractions(email,sms);}
 @Test void capturedStaffAndCustomerMessagesRetryIndependentlyWithoutReloading()throws Exception{
  UUID id=UUID.randomUUID();assertEquals(201,create(body(id)));
  var snapshots=jdbc.queryForList("SELECT type,recipient,subject,body,html_body FROM notification_delivery WHERE reservation_id=? ORDER BY type",id);
  settings(Map.of("reservationEmail","false","staffReservationEmail","false","staffReservationEmailAddress","changed@example.test"));
  jdbc.update("UPDATE reservation SET customer_name='Changed',notes='Changed',requested_at='2030-10-11 20:00' WHERE id=?",id);
  doThrow(new IllegalStateException("secret provider failure")).when(email).send(eq("staff@example.test"),any(),any(),any());
  worker.deliver();assertEquals("SENT",jdbc.queryForObject("SELECT status FROM notification_delivery WHERE reservation_id=? AND type='RESERVATION_RECEIVED'",String.class,id));
  assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM notification_delivery WHERE reservation_id=? AND type='STAFF_RESERVATION_RECEIVED'",String.class,id));
  worker.deliver();verify(email,times(1)).send(eq("staff@example.test"),any(),any(),any());
  jdbc.update("UPDATE notification_delivery SET next_attempt_at=? WHERE reservation_id=? AND type='STAFF_RESERVATION_RECEIVED'",java.sql.Timestamp.from(now),id);
  doNothing().when(email).send(eq("staff@example.test"),any(),any(),any());worker.deliver();
  verify(email,times(1)).send(eq("guest@example.test"),any(),any(),any());verify(email,times(2)).send(eq("staff@example.test"),any(),any(),any());verify(email,never()).send(eq("changed@example.test"),any(),any(),any());verifyNoInteractions(sms);
  assertEquals(snapshots,jdbc.queryForList("SELECT type,recipient,subject,body,html_body FROM notification_delivery WHERE reservation_id=? ORDER BY type",id));
 }
 @Test void migrationEnforcesOwnerEmailChannelAndUniqueness()throws Exception{
  assertEquals(Boolean.TRUE,jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version='37'",Boolean.class));
  UUID id=UUID.randomUUID();assertEquals(201,create(body(id)));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE notification_delivery SET channel='SMS' WHERE reservation_id=? AND type='STAFF_RESERVATION_RECEIVED'",id));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE notification_delivery SET type='STAFF_ORDER_RECEIVED' WHERE reservation_id=? AND type='STAFF_RESERVATION_RECEIVED'",id));
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO notification_delivery(id,reservation_id,type,channel,recipient,subject,body,created_at,next_attempt_at) SELECT ?,reservation_id,type,channel,recipient,subject,body,created_at,next_attempt_at FROM notification_delivery WHERE reservation_id=? AND type='STAFF_RESERVATION_RECEIVED'",UUID.randomUUID(),id));
 }
 @Test void disabledEmailPurposesAndMissingOptionalEmailProduceOnlyEligibleJobs()throws Exception{
  for(boolean customer:List.of(false,true))for(boolean staff:List.of(false,true)){settings(Map.of("reservationEmail",String.valueOf(customer),"staffReservationEmail",String.valueOf(staff)));UUID id=UUID.randomUUID();assertEquals(201,create(body(id)));assertEquals((customer?1:0)+(staff?1:0),jobs(id));}
  settings(Map.of("reservationEmail","true","staffReservationEmail","true"));UUID id=UUID.randomUUID();assertEquals(201,create(body(id).replace("\"email\":\"guest@example.test\",","")));assertEquals(1,jobs(id));
 }
 @Test void staffNavigationApiRequiresAuthenticationAndFrontOfHouseRole()throws Exception{
  assertEquals(401,mvc.perform(get("/api/staff/reservations?date=2030-10-10")).andReturn().getResponse().getStatus());
  assertEquals(403,mvc.perform(get("/api/staff/reservations?date=2030-10-10").with(user("kitchen").roles("BOH"))).andReturn().getResponse().getStatus());
  assertEquals(200,mvc.perform(get("/api/staff/reservations?date=2030-10-10").with(user("front").roles("FOH"))).andReturn().getResponse().getStatus());
 }
}
