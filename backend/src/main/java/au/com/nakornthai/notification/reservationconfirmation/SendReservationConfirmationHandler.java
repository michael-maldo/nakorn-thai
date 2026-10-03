package au.com.nakornthai.notification.reservationconfirmation;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryJpaEntity;
import au.com.nakornthai.notification.domain.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class SendReservationConfirmationHandler {
 private final EntityManager em;
 private final Clock clock;
 @Transactional(propagation=Propagation.MANDATORY)
 public void handle(SendReservationConfirmationCommand c) {
  String time=c.requestedAt().format(DateTimeFormatter.ofPattern("EEEE d MMM uuuu 'at' h:mm a",Locale.ENGLISH));
  String details="Your booking is confirmed for "+time+" for "+c.partySize()+" guests. Ref: "+c.reservationId();
  if(c.verifiedPhone()!=null)queue(c.reservationId(),NotificationChannel.SMS,c.verifiedPhone(),"Nakorn Thai: "+details);
  if(c.verifiedEmail()!=null)queue(c.reservationId(),NotificationChannel.EMAIL,c.verifiedEmail(),"Hello "+c.customerName()+",\n\nNakorn Thai: "+details+"\n\nWe look forward to welcoming you.");
 }
 private void queue(UUID reservationId,NotificationChannel channel,String recipient,String body) {
  var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setReservationId(reservationId);n.setType(NotificationType.RESERVATION_CONFIRMED);n.setChannel(channel);n.setRecipient(recipient);n.setSubject("Nakorn Thai booking confirmed");n.setBody(body);n.setCreatedAt(clock.instant());n.setNextAttemptAt(clock.instant());em.persist(n);
  log.info("notification_queued type={} channel={} id={}",n.getType(),channel,n.getId());
 }
}
