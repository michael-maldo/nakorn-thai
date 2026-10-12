package au.com.nakornthai.notification.reservationconfirmation;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryJpaEntity;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.reservation.infrastructure.ReservationJpaEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class SendReservationConfirmationHandler {
 private final EntityManager em;
 private final Clock clock;
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 private ReservationEmailLinks links;
 @org.springframework.beans.factory.annotation.Autowired public void configure(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){this.configuration=configuration;}
 @org.springframework.beans.factory.annotation.Autowired public void configureLinks(ReservationEmailLinks links){this.links=links;}

 @Transactional(propagation=Propagation.MANDATORY)
 public void handle(SendReservationConfirmationCommand c) {
  var config=configuration==null?null:configuration.snapshot();
  String event=ReservationEmailDetails.event(c);
  if(c.verifiedPhone()!=null&&(config==null||config.flag("reservationSms")))queue(c,c.type(),NotificationChannel.SMS,c.verifiedPhone(),"Nakorn Thai booking "+c.type().name().substring(12).toLowerCase(Locale.ROOT),"Nakorn Thai: "+event+" "+ReservationEmailDetails.time(c)+" for "+c.partySize()+" guests. Ref: "+c.reservationId(),null);
  String email=validEmail(c.email());
  if(email!=null&&(config==null||config.flag("reservationEmail"))){
   String subject=ReservationEmailDetails.subject(c,false),plain=ReservationEmailDetails.customer(c);
   queue(c,c.type(),NotificationChannel.EMAIL,email,subject,plain,ReservationEmailHtml.render(subject,plain,null));
  }
  if(c.type()==NotificationType.RESERVATION_RECEIVED&&config!=null&&config.flag("staffReservationEmail")){
   var reservation=em.find(ReservationJpaEntity.class,c.reservationId());
   if(reservation==null)throw new IllegalStateException("Reservation snapshot unavailable for notification");
   String url=links.staff(),subject=ReservationEmailDetails.subject(c,true),plain=ReservationEmailDetails.staff(c,reservation,url);
   queue(c,NotificationType.STAFF_RESERVATION_RECEIVED,NotificationChannel.EMAIL,ContactDestination.normalize("EMAIL",config.text("staffReservationEmailAddress")),subject,plain,ReservationEmailHtml.render(subject,plain,url));
  }
 }
 // Historical records can contain unusable destinations; keep staff actions available.
 private static String validEmail(String value){try{return ContactDestination.normalize("EMAIL",value);}catch(org.springframework.web.server.ResponseStatusException invalid){return null;}}
 private void queue(SendReservationConfirmationCommand c,NotificationType type,NotificationChannel channel,String recipient,String subject,String body,String html) {
  // Creation/status locks serialize enqueueing; UNIQUE(reservation_id,type,channel)
  // retains existing jobs, including exhausted work and captured recipients.
  Number existing=(Number)em.createNativeQuery("SELECT count(*) FROM notification_delivery WHERE reservation_id=:id AND type=:type AND channel=:channel",Long.class).setParameter("id",c.reservationId()).setParameter("type",type.name()).setParameter("channel",channel.name()).getSingleResult();
  if(existing.longValue()>0)return;
  var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setReservationId(c.reservationId());n.setType(type);n.setChannel(channel);n.setRecipient(recipient);n.setSubject(subject);n.setBody(body);n.setHtmlBody(html);n.setCreatedAt(clock.instant());n.setNextAttemptAt(clock.instant());em.persist(n);
  log.info("notification_queued type={} channel={} id={}",n.getType(),channel,n.getId());
 }
}
