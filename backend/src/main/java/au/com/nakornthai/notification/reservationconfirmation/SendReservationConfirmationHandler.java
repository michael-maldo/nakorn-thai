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
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 @org.springframework.beans.factory.annotation.Autowired public void configure(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){this.configuration=configuration;}

 @Transactional(propagation=Propagation.MANDATORY)
 public void handle(SendReservationConfirmationCommand c) {
  String time=c.requestedAt().format(DateTimeFormatter.ofPattern("EEEE d MMM uuuu 'at' h:mm a",Locale.ENGLISH));
  String event=switch(c.type()) {
   case RESERVATION_RECEIVED -> "We received your booking request. Your table is not confirmed yet.";
   case RESERVATION_CONFIRMED -> "Your booking is confirmed.";
   case RESERVATION_DECLINED -> "Unfortunately, your booking request was declined.";
   case RESERVATION_CANCELLED -> "Your booking was cancelled.";
   default -> throw new IllegalArgumentException("Unsupported reservation notification");
  };
  String details=event+" "+time+" for "+c.partySize()+" guests. Ref: "+c.reservationId();
  if(c.verifiedPhone()!=null&&(configuration==null||configuration.snapshot().flag("reservationSms")))queue(c.reservationId(),c.type(),NotificationChannel.SMS,c.verifiedPhone(),"Nakorn Thai: "+details);
  if(c.email()!=null&&(configuration==null||configuration.snapshot().flag("reservationEmail")))queue(c.reservationId(),c.type(),NotificationChannel.EMAIL,c.email(),"Hello "+c.customerName()+",\n\nNakorn Thai: "+details+"\n\nNakorn Thai");
 }
 private void queue(UUID reservationId,NotificationType type,NotificationChannel channel,String recipient,String body) {
  // Creation/status locks serialize enqueueing; the existing unique constraint
  // durably prevents duplicate reservation event/channel work.
  Number existing=(Number)em.createNativeQuery("SELECT count(*) FROM notification_delivery WHERE reservation_id=:id AND type=:type AND channel=:channel",Long.class).setParameter("id",reservationId).setParameter("type",type.name()).setParameter("channel",channel.name()).getSingleResult();
  if(existing.longValue()>0)return;
  var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setReservationId(reservationId);n.setType(type);n.setChannel(channel);n.setRecipient(recipient);n.setSubject("Nakorn Thai booking "+type.name().substring(12).toLowerCase(Locale.ROOT));n.setBody(body);n.setCreatedAt(clock.instant());n.setNextAttemptAt(clock.instant());em.persist(n);
  log.info("notification_queued type={} channel={} id={}",n.getType(),channel,n.getId());
 }
}
