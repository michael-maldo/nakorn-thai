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
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class SendOrderConfirmationHandler {
 private final EntityManager em;
 private final Clock clock;
 private final OrderEmailLinks links;
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 @org.springframework.beans.factory.annotation.Autowired public void configure(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){this.configuration=configuration;}

 @Transactional(propagation=Propagation.MANDATORY)
 public void handle(SendOrderConfirmationCommand c) {
  var config=configuration==null?null:configuration.snapshot();
  String reference=OrderEmailDetails.reference(c.orderId());
  String message=switch(c.type()) {
   case ORDER_RECEIVED -> "We received your order. It has not been accepted yet. The restaurant will confirm pickup after payment/acceptance.";
   case ORDER_ACCEPTED -> "Your order has been accepted."+ (c.estimatedReadyAt()==null?"":" Estimated pickup: "+DateTimeFormatter.ofPattern("h:mm a",Locale.ENGLISH).withZone(c.timezone()).format(c.estimatedReadyAt())+".");
   case ORDER_READY -> "Your order is ready for pickup at Nakorn Thai, 233 Glenferrie Rd, Malvern.";
   case ORDER_CANCELLED -> "Your order was cancelled. Any refund must be arranged separately; cancellation does not automatically refund payment.";
   default -> throw new IllegalArgumentException("Unsupported order notification");
  };
  String sms="Nakorn Thai: "+message+" Ref: "+reference;
  if(c.verifiedPhone()!=null&&(config==null||config.flag("orderSms")))queue(c,NotificationChannel.SMS,c.verifiedPhone(),sms);
  boolean customerEmail=c.email()!=null&&(config==null||config.flag("orderEmail"));
  boolean staffEmail=c.type()==NotificationType.ORDER_RECEIVED&&config!=null&&config.flag("staffOrderEmail");
  if(customerEmail||staffEmail){
   var order=em.find(au.com.nakornthai.ordering.infrastructure.OrderJpaEntity.class,c.orderId());
   if(order==null)throw new IllegalStateException("Order snapshot unavailable for notification");
   if(customerEmail)queue(c,NotificationChannel.EMAIL,c.email(),"Hello "+order.getCustomerName()+",\n\n"+message+"\n\n"+OrderEmailDetails.format(order,c.timezone(),false)+"\nNakorn Thai\n\nVerify by SMS or email to view the current status.\nTrack your order\n"+links.customer(c.orderId())+"\n");
   if(staffEmail){
    var staff=new SendOrderConfirmationCommand(c.orderId(),NotificationType.STAFF_ORDER_RECEIVED,null,null,c.customerName(),c.totalMinor(),c.estimatedReadyAt(),c.timezone());
    queue(staff,NotificationChannel.EMAIL,ContactDestination.normalize("EMAIL",config.text("staffOrderEmailAddress")),"New customer order\n\nA new customer order needs review.\nIt has not been accepted yet.\n\n"+OrderEmailDetails.format(order,c.timezone(),true)+"\nStaff sign-in required.\nView order\n"+links.staff(c.orderId())+"\n");
   }
  }
 }
 static String subject(SendOrderConfirmationCommand c){
  String ref=OrderEmailDetails.reference(c.orderId());
  if(c.type()==NotificationType.STAFF_ORDER_RECEIVED)return "Nakorn Thai — New customer order #"+ref;
  String event=switch(c.type()){case ORDER_RECEIVED->"Received";case ORDER_ACCEPTED->"Accepted";case ORDER_READY->"Ready for pickup";case ORDER_CANCELLED->"Cancelled";default->throw new IllegalArgumentException("Unsupported order email");};
  return "Nakorn Thai — Order #"+ref+" — "+event;
 }
 private void queue(SendOrderConfirmationCommand c,NotificationChannel channel,String recipient,String body) {
  // Creation/status locks serialize this event; the unique database index is
  // the durable backstop. Existing work is retained, including exhausted jobs.
  Number existing=(Number)em.createNativeQuery("SELECT count(*) FROM notification_delivery WHERE order_id=:id AND type=:type AND channel=:channel",Long.class).setParameter("id",c.orderId()).setParameter("type",c.type().name()).setParameter("channel",channel.name()).getSingleResult();
  if(existing.longValue()>0)return;
  var n=new NotificationDeliveryJpaEntity();n.setId(UUID.randomUUID());n.setOrderId(c.orderId());n.setType(c.type());n.setChannel(channel);n.setRecipient(recipient);n.setSubject(channel==NotificationChannel.EMAIL?subject(c):"Nakorn Thai order "+c.type().name().substring(6).toLowerCase(Locale.ROOT));n.setBody(body);if(channel==NotificationChannel.EMAIL){boolean staff=c.type()==NotificationType.STAFF_ORDER_RECEIVED;n.setHtmlBody(OrderEmailHtml.render(n.getSubject(),body,staff?"View order":"Track your order",staff?links.staff(c.orderId()):links.customer(c.orderId())));}n.setCreatedAt(clock.instant());n.setNextAttemptAt(clock.instant());em.persist(n);
  log.info("notification_queued type={} channel={} id={}",n.getType(),channel,n.getId());
 }
}
