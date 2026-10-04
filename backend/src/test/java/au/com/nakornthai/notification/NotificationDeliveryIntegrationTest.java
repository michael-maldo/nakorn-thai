package au.com.nakornthai.notification;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.notification.reservationconfirmation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class NotificationDeliveryIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
  p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
 }
 @Autowired JdbcTemplate jdbc;
 @Autowired PlatformTransactionManager transactionManager;
 @Autowired SendReservationConfirmationHandler confirmation;
 @Autowired NotificationDeliveryWorker worker;
 @Autowired au.com.nakornthai.notification.orderconfirmation.SendOrderConfirmationHandler orderingNotifications;
 @MockitoBean SmsSender sms;
 @MockitoBean EmailSender email;
 @MockitoBean au.com.nakornthai.restaurant.configuration.IntegrationDiagnostics diagnostics;
 @MockitoBean Clock clock;
 // Drive the real worker explicitly; background polling must not race assertions.
 @MockitoBean(name="taskScheduler") org.springframework.scheduling.TaskScheduler scheduler;
 final Instant now=Instant.parse("2030-10-03T00:00:00Z");
 final Set<UUID> reservationIds=new HashSet<>();
 final Set<UUID> orderIds=new HashSet<>();
 TransactionTemplate transaction;
 @BeforeEach void setup() {
  when(clock.instant()).thenReturn(now);
  when(clock.getZone()).thenReturn(ZoneOffset.UTC);
  transaction=new TransactionTemplate(transactionManager);
 }
 @AfterEach void cleanup() {
  for(UUID id:orderIds)transaction.executeWithoutResult(status -> {
   jdbc.update("DELETE FROM notification_delivery WHERE order_id=?",id);
   jdbc.update("DELETE FROM restaurant_order WHERE id=?",id);
  });
  for(UUID id:reservationIds)transaction.executeWithoutResult(status -> {
   jdbc.update("DELETE FROM notification_delivery WHERE reservation_id=?",id);
   jdbc.update("DELETE FROM reservation WHERE id=?",id);
  });
 }
 UUID committedNotifications() {
  UUID id=UUID.randomUUID();reservationIds.add(id);
  transaction.executeWithoutResult(status -> {
   jdbc.update("INSERT INTO reservation(id,customer_name,party_size,requested_at,created_at,updated_at) VALUES (?,'Guest',4,'2026-10-10 19:00',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id);
   confirmation.handle(new SendReservationConfirmationCommand(id,"Guest",LocalDateTime.parse("2026-10-10T19:00:00"),4,"+61412345678","Guest@example.com"));
  });
  // Future due dates keep other cached test application contexts' real clocks
  // from polling these committed fixtures. SMS is selected before email.
  jdbc.update("UPDATE notification_delivery SET next_attempt_at=? WHERE reservation_id=? AND channel='SMS'",java.sql.Timestamp.from(now.minusSeconds(1)),id);
  return id;
 }
 void assertStatus(UUID id,String channel,String expected) {
  assertEquals(expected,jdbc.queryForObject("SELECT status FROM notification_delivery WHERE reservation_id=? AND channel=?",String.class,id,channel));
 }

 @Test void orderingUsesSameWorkerAndRetriesSmsWithoutResendingEmail() {
  UUID id=UUID.randomUUID();orderIds.add(id);
  transaction.executeWithoutResult(status -> {
   jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,phone_verified,created_at,updated_at) VALUES (?,? ,?,'Guest','+61412345678','',1990,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,"a".repeat(64),"b".repeat(64));
   var handler=orderingNotifications;
   handler.handle(new au.com.nakornthai.notification.orderconfirmation.SendOrderConfirmationCommand(id,NotificationType.ORDER_READY,"+61412345678","Guest@example.com","Guest",1990,null,ZoneId.of("Australia/Melbourne")));
  });
  // Durable protection still applies to a caller bypassing the enqueue check.
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->transaction.executeWithoutResult(status->
   jdbc.update("INSERT INTO notification_delivery(id,order_id,type,channel,recipient,subject,body,created_at,next_attempt_at) SELECT ?,order_id,type,channel,recipient,subject,body,created_at,next_attempt_at FROM notification_delivery WHERE order_id=? AND channel='SMS'",UUID.randomUUID(),id)));
  doThrow(new IllegalStateException("Fake provider unavailable")).when(sms).send(any(),any());
  worker.deliver();
  assertEquals("SENT",jdbc.queryForObject("SELECT status FROM notification_delivery WHERE order_id=? AND channel='EMAIL'",String.class,id));
  assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM notification_delivery WHERE order_id=? AND channel='SMS'",String.class,id));
  worker.deliver();verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
  jdbc.update("UPDATE notification_delivery SET next_attempt_at=? WHERE order_id=? AND channel='SMS'",java.sql.Timestamp.from(now.minusSeconds(1)),id);
  doNothing().when(sms).send(any(),any());worker.deliver();
  assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM notification_delivery WHERE order_id=? AND status='SENT'",Integer.class,id));
  verify(sms,times(2)).send(eq("+61412345678"),any());verify(email,times(1)).send(eq("Guest@example.com"),any(),any());
 }
 @Test void reservationEmailRetriesWithoutResendingSuccessfulSms() {
  UUID id=committedNotifications();
  doThrow(new IllegalStateException("Fake provider unavailable")).when(email).send(any(),any(),any());
  worker.deliver();assertStatus(id,"SMS","SENT");assertStatus(id,"EMAIL","FAILED");
  worker.deliver();verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
  jdbc.update("UPDATE notification_delivery SET next_attempt_at=? WHERE reservation_id=? AND channel='EMAIL'",java.sql.Timestamp.from(now.minusSeconds(1)),id);
  doNothing().when(email).send(any(),any(),any());worker.deliver();
  assertStatus(id,"SMS","SENT");assertStatus(id,"EMAIL","SENT");
  verify(sms,times(1)).send(eq("+61412345678"),any());verify(email,times(2)).send(eq("Guest@example.com"),any(),any());
 }
 @Test void databaseWorkerRetriesFailedChannelWithoutResendingSuccessfulChannel() {
  UUID id=committedNotifications();
  doThrow(new IllegalStateException("Fake provider unavailable")).when(sms).send(any(),any());
  worker.deliver();
  assertStatus(id,"EMAIL","SENT");assertStatus(id,"SMS","FAILED");
  assertEquals(java.sql.Timestamp.from(now.plusSeconds(60)),jdbc.queryForObject("SELECT next_attempt_at FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",java.sql.Timestamp.class,id));
  worker.deliver(); // Failed SMS is not due yet; neither channel may resend.
  verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
  jdbc.update("UPDATE notification_delivery SET next_attempt_at=? WHERE reservation_id=? AND channel='SMS'",java.sql.Timestamp.from(now.minusSeconds(1)),id);
  doNothing().when(sms).send(any(),any());worker.deliver();
  assertStatus(id,"SMS","SENT");assertStatus(id,"EMAIL","SENT");
  assertEquals(2,jdbc.queryForObject("SELECT attempts FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",Integer.class,id));
  verify(email,times(1)).send(eq("Guest@example.com"),any(),any());verify(sms,times(2)).send(eq("+61412345678"),any());
 }
 @Test void concurrentWorkerSkipsInFlightSmsAndCommitsEmailWithoutDuplicateDelivery() throws Exception {
  UUID id=committedNotifications();
  var sending=new CountDownLatch(1);var release=new CountDownLatch(1);
  doAnswer(call -> {
   sending.countDown();
   if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Test delivery timed out");
   return null;
  }).when(sms).send(any(),any());
  var executor=Executors.newFixedThreadPool(2);
  try {
   var first=executor.submit(worker::deliver);
   assertTrue(sending.await(5,TimeUnit.SECONDS),"First worker must lock SMS and enter delivery");
   var second=executor.submit(worker::deliver);
   second.get(5,TimeUnit.SECONDS);
   assertStatus(id,"EMAIL","SENT");
   assertStatus(id,"SMS","PENDING"); // SMS outcome is still uncommitted.
   verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
   release.countDown();first.get(5,TimeUnit.SECONDS);
   assertStatus(id,"SMS","SENT");assertStatus(id,"EMAIL","SENT");
   assertEquals(1,jdbc.queryForObject("SELECT attempts FROM notification_delivery WHERE reservation_id=? AND channel='SMS'",Integer.class,id));
   verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
  }finally {
   release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));
  }
 }
 @Test void workerOutcomesCommitEvenWhenCallerTransactionRollsBack() {
  UUID id=committedNotifications();
  transaction.executeWithoutResult(status -> {
   worker.deliver();
   status.setRollbackOnly();
  });
  assertStatus(id,"SMS","SENT");assertStatus(id,"EMAIL","SENT");
  verify(sms,times(1)).send(any(),any());verify(email,times(1)).send(any(),any(),any());
 }
}
