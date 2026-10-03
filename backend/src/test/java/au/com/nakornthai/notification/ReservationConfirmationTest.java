package au.com.nakornthai.notification;
import au.com.nakornthai.notification.reservationconfirmation.*;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReservationConfirmationTest {
 EntityManager em=mock(EntityManager.class);
 Clock clock=Clock.fixed(Instant.parse("2026-10-03T00:00:00Z"),ZoneOffset.UTC);
 SmsSender sms=mock(SmsSender.class);EmailSender email=mock(EmailSender.class);
 SendReservationConfirmationHandler handler=new SendReservationConfirmationHandler(em,clock);
 NotificationDeliveryWorker worker=new NotificationDeliveryWorker(em,sms,email,clock,new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),mock(org.springframework.transaction.PlatformTransactionManager.class));
 List<NotificationDeliveryJpaEntity> queue(String phone,String address,int count) {
  handler.handle(new SendReservationConfirmationCommand(UUID.randomUUID(),"Guest",LocalDateTime.parse("2026-10-10T19:00:00"),4,phone,address));
  var captured=ArgumentCaptor.forClass(NotificationDeliveryJpaEntity.class);
  verify(em,times(count)).persist(captured.capture());
  var notifications=captured.getAllValues();
  for(var n:notifications) {
   assertTrue(n.getBody().contains("4 guests"));
   assertEquals(NotificationType.RESERVATION_CONFIRMED,n.getType());
  }
  return notifications;
 }
 @Test void phoneOnly() {
  var notifications=queue("+61412345678",null,1);
  assertEquals(1,notifications.size());
  assertEquals(NotificationChannel.SMS,notifications.getFirst().getChannel());
  assertEquals("+61412345678",notifications.getFirst().getRecipient());
 }
 @Test void emailOnly() {
  var notifications=queue(null,"Guest@example.com",1);
  assertEquals(1,notifications.size());
  assertEquals(NotificationChannel.EMAIL,notifications.getFirst().getChannel());
  assertEquals("Guest@example.com",notifications.getFirst().getRecipient());
 }
 @Test void both() {
  var notifications=queue("+61412345678","Guest@example.com",2);
  assertEquals(2,notifications.size());
  assertEquals(1,notifications.stream().filter(n -> n.getChannel()==NotificationChannel.SMS && n.getRecipient().equals("+61412345678")).count());
  assertEquals(1,notifications.stream().filter(n -> n.getChannel()==NotificationChannel.EMAIL && n.getRecipient().equals("Guest@example.com")).count());
 }
 @Test void noVerifiedDestinations(){queue(null,null,0);}
 NotificationDeliveryJpaEntity job(NotificationChannel channel){var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setType(NotificationType.RESERVATION_CONFIRMED);n.setChannel(channel);n.setRecipient("destination");n.setBody("body");n.setSubject("subject");return n;}
 @Test void sentAndExhaustedJobsAreNotResent(){var sent=job(NotificationChannel.SMS);sent.setStatus(DeliveryStatus.SENT);var exhausted=job(NotificationChannel.EMAIL);exhausted.setAttempts(5);worker.deliverOne(sent);worker.deliverOne(exhausted);verifyNoInteractions(sms,email);}
 @Test void successfulDelivery(){var n=job(NotificationChannel.SMS);worker.deliverOne(n);assertEquals(DeliveryStatus.SENT,n.getStatus());assertEquals(clock.instant(),n.getSentAt());}
 @Test void failureRetriesIndependently(){doThrow(new IllegalStateException("secret provider error")).when(sms).send(any(),any());var a=job(NotificationChannel.SMS);var b=job(NotificationChannel.EMAIL);worker.deliverOne(a);worker.deliverOne(b);assertEquals(DeliveryStatus.FAILED,a.getStatus());assertEquals(clock.instant().plusSeconds(60),a.getNextAttemptAt());assertEquals("Delivery unavailable",a.getLastError());assertEquals(DeliveryStatus.SENT,b.getStatus());worker.deliverOne(a);verify(email,times(1)).send(any(),any(),any());assertEquals(2,a.getAttempts());}
}
