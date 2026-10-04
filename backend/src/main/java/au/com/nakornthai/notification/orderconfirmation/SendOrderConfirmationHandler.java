package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.NotificationDeliveryJpaEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class SendOrderConfirmationHandler {
 private final EntityManager em;
 private final Clock clock;
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 @org.springframework.beans.factory.annotation.Autowired public void configure(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){this.configuration=configuration;}

 @Transactional(propagation=Propagation.MANDATORY)
 public void handle(SendOrderConfirmationCommand c) {
  String reference=c.orderId().toString().substring(0,8).toUpperCase(Locale.ROOT);
  String message=switch(c.type()) {
   case ORDER_RECEIVED -> "We received your order. It has not been accepted yet. The restaurant will confirm pickup after payment/acceptance.";
   case ORDER_ACCEPTED -> "Your order has been accepted."+ (c.estimatedReadyAt()==null?"":" Estimated pickup: "+DateTimeFormatter.ofPattern("h:mm a",Locale.ENGLISH).withZone(c.timezone()).format(c.estimatedReadyAt())+".");
   case ORDER_READY -> "Your order is ready for pickup at Nakorn Thai, 233 Glenferrie Rd, Malvern.";
   case ORDER_CANCELLED -> "Your order was cancelled. Any refund must be arranged separately; cancellation does not automatically refund payment.";
   default -> throw new IllegalArgumentException("Unsupported order notification");
  };
  String sms="Nakorn Thai: "+message+" Ref: "+reference;
  if(c.verifiedPhone()!=null&&(configuration==null||configuration.snapshot().flag("orderSms")))queue(c,NotificationChannel.SMS,c.verifiedPhone(),sms);
  if(c.email()!=null&&(configuration==null||configuration.snapshot().flag("orderEmail")))queue(c,NotificationChannel.EMAIL,c.email(),"Hello "+c.customerName()+",\n\n"+sms+"\nTotal: AUD "+BigDecimal.valueOf(c.totalMinor(),2).toPlainString()+"\n\nNakorn Thai");
 }
 private void queue(SendOrderConfirmationCommand c,NotificationChannel channel,String recipient,String body) {
  // Creation/status locks serialize this event; the unique database index is
  // the durable backstop. Existing work is retained, including exhausted jobs.
  Number existing=(Number)em.createNativeQuery("SELECT count(*) FROM notification_delivery WHERE order_id=:id AND type=:type AND channel=:channel",Long.class).setParameter("id",c.orderId()).setParameter("type",c.type().name()).setParameter("channel",channel.name()).getSingleResult();
  if(existing.longValue()>0)return;
  var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setOrderId(c.orderId());n.setType(c.type());n.setChannel(channel);n.setRecipient(recipient);n.setSubject("Nakorn Thai order "+c.type().name().substring(6).toLowerCase(Locale.ROOT));n.setBody(body);n.setCreatedAt(clock.instant());n.setNextAttemptAt(clock.instant());em.persist(n);
  log.info("notification_queued type={} channel={} id={}",n.getType(),channel,n.getId());
 }
}
