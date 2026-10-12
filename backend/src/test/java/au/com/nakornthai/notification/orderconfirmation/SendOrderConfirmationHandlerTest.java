package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.restaurant.configuration.*;
import au.com.nakornthai.ordering.infrastructure.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SendOrderConfirmationHandlerTest {
 final EntityManager em=mock(EntityManager.class);
 final Query query=mock(Query.class);
 final OrderJpaEntity order=OrderEmailDetailsTest.order();
 final SendOrderConfirmationHandler handler=new SendOrderConfirmationHandler(em,Clock.fixed(Instant.parse("2030-10-03T00:00:00Z"),ZoneOffset.UTC));
 SendOrderConfirmationHandlerTest(){when(em.find(OrderJpaEntity.class,order.getId())).thenReturn(order);when(em.createNativeQuery(anyString(),eq(Long.class))).thenReturn(query);when(query.setParameter(anyString(),any())).thenReturn(query);when(query.getSingleResult()).thenReturn(0L);}
 void config(boolean customer,boolean staff){handler.configure(()->new RuntimeConfiguration.Snapshot(Map.of("orderEmail",String.valueOf(customer),"staffOrderEmail",String.valueOf(staff),"staffOrderEmailAddress","staff@example.com","orderSms","false")));}
 SendOrderConfirmationCommand command(NotificationType type,String email){return new SendOrderConfirmationCommand(order.getId(),type,null,email,"Guest",5095,Instant.parse("2030-10-03T01:00:00Z"),ZoneId.of("Australia/Melbourne"));}
 List<NotificationDeliveryJpaEntity> jobs(){var captured=ArgumentCaptor.forClass(NotificationDeliveryJpaEntity.class);verify(em,atLeastOnce()).persist(captured.capture());return captured.getAllValues();}
 @Test void allFourEnablementCombinationsAreIndependent(){for(boolean customer:List.of(false,true))for(boolean staff:List.of(false,true)){clearInvocations(em);config(customer,staff);handler.handle(command(NotificationType.ORDER_RECEIVED,"guest@example.com"));if(!customer&&!staff)verify(em,never()).persist(any());else{var jobs=jobs();assertEquals((customer?1:0)+(staff?1:0),jobs.size());assertEquals(staff,jobs.stream().anyMatch(n->n.getType()==NotificationType.STAFF_ORDER_RECEIVED));}}}
 @Test void staffDoesNotNeedCustomerEmailOrVerifiedPhone(){config(false,true);handler.handle(command(NotificationType.ORDER_RECEIVED,null));var job=jobs().getFirst();assertEquals("staff@example.com",job.getRecipient());assertEquals(NotificationType.STAFF_ORDER_RECEIVED,job.getType());assertTrue(job.getSubject().contains("New customer order"));assertTrue(job.getBody().contains("2 x Historical curry"));assertTrue(job.getBody().contains("Phone: +61434778435"));assertEquals(0,job.getAttempts());assertEquals(DeliveryStatus.PENDING,job.getStatus());}
 @Test void existingJobIsRetained(){config(true,true);when(query.getSingleResult()).thenReturn(1L);handler.handle(command(NotificationType.ORDER_RECEIVED,"guest@example.com"));verify(em,never()).persist(any());}
 @Test void eventSpecificWordingAndSubjectsRemainWithSharedDetails(){config(true,true);var types=List.of(NotificationType.ORDER_RECEIVED,NotificationType.ORDER_ACCEPTED,NotificationType.ORDER_READY,NotificationType.ORDER_CANCELLED);var wording=List.of("not been accepted yet","Estimated pickup: 11:00 am","ready for pickup","does not automatically refund");for(int i=0;i<types.size();i++){clearInvocations(em);handler.handle(command(types.get(i),"guest@example.com"));var jobs=jobs();assertEquals(i==0?2:1,jobs.size());var customer=jobs.getFirst();assertEquals("Nakorn Thai order "+types.get(i).name().substring(6).toLowerCase(Locale.ROOT),customer.getSubject());assertTrue(customer.getBody().toLowerCase(Locale.ROOT).contains(wording.get(i).toLowerCase(Locale.ROOT)),customer.getBody());assertTrue(customer.getBody().contains("Total: AUD 50.95"));}}
 @Test void staffFailureRetriesWithoutResendingCustomer(){
  config(true,true);handler.handle(command(NotificationType.ORDER_RECEIVED,"guest@example.com"));var jobs=jobs();
  var email=mock(EmailSender.class);doThrow(new IllegalStateException("Test provider unavailable")).when(email).send(eq("staff@example.com"),any(),any());
  var worker=new NotificationDeliveryWorker(em,mock(SmsSender.class),email,Clock.fixed(Instant.parse("2030-10-03T00:00:00Z"),ZoneOffset.UTC),new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),mock(org.springframework.transaction.PlatformTransactionManager.class));
  var customer=jobs.getFirst();var staff=jobs.getLast();worker.deliverOne(customer);worker.deliverOne(staff);
  assertEquals(DeliveryStatus.SENT,customer.getStatus());assertEquals(DeliveryStatus.FAILED,staff.getStatus());assertEquals(Instant.parse("2030-10-03T00:01:00Z"),staff.getNextAttemptAt());
  doNothing().when(email).send(eq("staff@example.com"),any(),any());worker.deliverOne(customer);worker.deliverOne(staff);
  assertEquals(DeliveryStatus.SENT,staff.getStatus());assertEquals(2,staff.getAttempts());verify(email,times(1)).send(eq("guest@example.com"),any(),any());verify(email,times(2)).send(eq("staff@example.com"),any(),any());
 }

}
