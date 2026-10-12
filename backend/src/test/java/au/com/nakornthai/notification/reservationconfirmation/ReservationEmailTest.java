package au.com.nakornthai.notification.reservationconfirmation;

import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryJpaEntity;
import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import au.com.nakornthai.reservation.infrastructure.ReservationJpaEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationEmailTest {
 final UUID id=UUID.fromString("12345678-1234-1234-1234-123456789012");
 EntityManager em=mock(EntityManager.class,RETURNS_DEEP_STUBS);
 RuntimeConfiguration configuration=mock(RuntimeConfiguration.class);
 Clock clock=Clock.fixed(Instant.parse("2030-10-03T00:00:00Z"),ZoneOffset.UTC);
 SendReservationConfirmationHandler handler=new SendReservationConfirmationHandler(em,clock);
 ReservationJpaEntity reservation=new ReservationJpaEntity();
 @BeforeEach void setup(){
  handler.configure(configuration);handler.configureLinks(new ReservationEmailLinks(configuration,new MockEnvironment()));
  when(em.createNativeQuery(anyString(),eq(Long.class)).setParameter(eq("id"),any()).setParameter(eq("type"),any()).setParameter(eq("channel"),any()).getSingleResult()).thenReturn(0L);
  reservation.setId(id);reservation.setCustomerName("Guest <&\"'>");reservation.setPhone("+61412345678");reservation.setEmail("guest@example.test");reservation.setNotes("Window <script>alert('x')</script> & please xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");reservation.setStaffNote("INTERNAL PRIVATE NOTE");
  when(em.find(ReservationJpaEntity.class,id)).thenReturn(reservation);
 }
 void config(boolean customer,boolean staff,boolean sms){when(configuration.snapshot()).thenReturn(new RuntimeConfiguration.Snapshot(Map.of("reservationEmail",String.valueOf(customer),"staffReservationEmail",String.valueOf(staff),"reservationSms",String.valueOf(sms),"staffReservationEmailAddress","staff@example.test","businessWebsiteUrl","https://restaurant.example.test/")));}
 SendReservationConfirmationCommand command(NotificationType type,String email,String phone){return new SendReservationConfirmationCommand(id,reservation.getCustomerName(),LocalDateTime.parse("2030-10-10T19:00:00"),4,phone,email,type,ZoneId.of("Australia/Melbourne"));}
 List<NotificationDeliveryJpaEntity> jobs(int count){var capture=ArgumentCaptor.forClass(NotificationDeliveryJpaEntity.class);verify(em,times(count)).persist(capture.capture());return capture.getAllValues();}
 @Test void allCustomerEventsHaveCapturedEscapedAlternativesWithoutLinks(){
  config(true,false,false);
  for(var type:List.of(NotificationType.RESERVATION_RECEIVED,NotificationType.RESERVATION_CONFIRMED,NotificationType.RESERVATION_DECLINED,NotificationType.RESERVATION_CANCELLED))handler.handle(command(type,"guest@EXAMPLE.TEST",null));
  for(var n:jobs(4)){
   assertEquals("guest@example.test",n.getRecipient());assertTrue(n.getSubject().contains("12345678"));assertTrue(n.getBody().contains("Australia/Melbourne"));assertTrue(n.getBody().contains("Thursday 10 Oct 2030 at 7:00 pm"));assertTrue(n.getBody().contains("4 guests"));
   assertTrue(n.getHtmlBody().contains("&lt;&amp;&quot;&#39;&gt;"));assertFalse(n.getHtmlBody().contains("<a "));assertFalse(n.getBody().contains("INTERNAL PRIVATE NOTE"));
   assertTrue(n.getHtmlBody().contains(n.getBody().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;").replace("\n","<br>")));
   assertEquals(clock.instant(),n.getCreatedAt());sample(n);
   if(n.getType()==NotificationType.RESERVATION_RECEIVED)assertTrue(n.getBody().contains("not confirmed yet"));
   if(n.getType()==NotificationType.RESERVATION_CONFIRMED)assertTrue(n.getBody().contains("is confirmed"));
  }
 }
 @Test void staffReceiptIsIndependentAndIncludesOnlyGuestNotesAndAuthenticatedRoute(){
  config(false,true,false);handler.handle(command(NotificationType.RESERVATION_RECEIVED,null,null));
  var n=jobs(1).getFirst();assertEquals(NotificationType.STAFF_RESERVATION_RECEIVED,n.getType());assertEquals("staff@example.test",n.getRecipient());
  assertTrue(n.getBody().contains("REQUESTED — not yet confirmed"));assertTrue(n.getBody().contains(reservation.getPhone()));assertTrue(n.getBody().contains(reservation.getEmail()));assertTrue(n.getBody().contains(reservation.getNotes()));assertFalse(n.getBody().contains(reservation.getStaffNote()));
  assertTrue(n.getHtmlBody().contains("&lt;script&gt;"));assertFalse(n.getHtmlBody().contains("<script>"));assertTrue(n.getBody().endsWith("https://restaurant.example.test/#/staff/reservations\n"));assertFalse(n.getBody().contains("?"));sample(n);
 }
 @Test void toggleCombinationsAndMissingEmail(){
  for(boolean customer:List.of(false,true))for(boolean staff:List.of(false,true)){
   clearInvocations(em);config(customer,staff,false);handler.handle(command(NotificationType.RESERVATION_RECEIVED,"guest@example.test",null));jobs((customer?1:0)+(staff?1:0));
  }
  clearInvocations(em);config(true,true,false);handler.handle(command(NotificationType.RESERVATION_RECEIVED,null,null));jobs(1);
 }
 @Test void staffReceivesOnlyCreationAndUnverifiedPhonesNeverProduceSms(){config(true,true,true);handler.handle(command(NotificationType.RESERVATION_CONFIRMED,"guest@example.test",null));assertEquals(NotificationType.RESERVATION_CONFIRMED,jobs(1).getFirst().getType());}
 @Test void invalidHistoricalEmailDoesNotBlockTransition(){config(true,false,true);handler.handle(command(NotificationType.RESERVATION_CANCELLED,"malformed",null));jobs(0);}
 @Test void capturedContentAndRecipientsAreRetainedByDuplicateCheck(){config(true,true,false);when(em.createNativeQuery(anyString(),eq(Long.class)).setParameter(eq("id"),any()).setParameter(eq("type"),any()).setParameter(eq("channel"),any()).getSingleResult()).thenReturn(1L);handler.handle(command(NotificationType.RESERVATION_RECEIVED,"guest@example.test",null));jobs(0);}
 @Test void alternateRestaurantTimezoneUsesLocalWallTime(){var c=new SendReservationConfirmationCommand(id,"Guest",LocalDateTime.parse("2030-07-01T18:30:00"),2,null,null,NotificationType.RESERVATION_CONFIRMED,ZoneId.of("Australia/Perth"));assertTrue(ReservationEmailDetails.time(c).contains("6:30 pm (Australia/Perth)"));}
 @Test void staffLinkRejectsUntrustedConfiguredUrl(){when(configuration.snapshot()).thenReturn(new RuntimeConfiguration.Snapshot(Map.of("businessWebsiteUrl","https://user:password@example.test/")));assertThrows(IllegalArgumentException.class,()->new ReservationEmailLinks(configuration,new MockEnvironment()).staff());}
 @Test void staffFailureExhaustsFiveAttemptsAndRetainsCapturedAlternatives(){
  config(false,true,false);handler.handle(command(NotificationType.RESERVATION_RECEIVED,null,null));var n=jobs(1).getFirst();
  String body=n.getBody(),html=n.getHtmlBody(),subject=n.getSubject(),recipient=n.getRecipient();
  var sender=mock(EmailSender.class);var sms=mock(SmsSender.class);
  doThrow(new IllegalStateException("secret provider details")).when(sender).send(any(),any(),any(),any());
  var worker=new au.com.nakornthai.notification.infrastructure.NotificationDeliveryWorker(em,sms,sender,clock,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),mock(org.springframework.transaction.PlatformTransactionManager.class));
  for(int attempt=1;attempt<=5;attempt++){worker.deliverOne(n);assertEquals(attempt,n.getAttempts());assertEquals(clock.instant().plusSeconds(60L*(1L<<(attempt-1))),n.getNextAttemptAt());}
  worker.deliverOne(n);verify(sender,times(5)).send(recipient,subject,body,html);verifyNoInteractions(sms);
  assertEquals(DeliveryStatus.FAILED,n.getStatus());assertEquals("Delivery unavailable",n.getLastError());assertEquals(body,n.getBody());assertEquals(html,n.getHtmlBody());
 }

 private static void sample(NotificationDeliveryJpaEntity n){
  String directory=System.getProperty("reservation.email.samples.dir");if(directory==null)return;
  assertDoesNotThrow(()->{var root=java.nio.file.Path.of(directory);java.nio.file.Files.createDirectories(root);java.nio.file.Files.writeString(root.resolve(n.getType()+".html"),n.getHtmlBody());java.nio.file.Files.writeString(root.resolve(n.getType()+".txt"),"Subject: "+n.getSubject()+"\n\n"+n.getBody());});
 }

}
