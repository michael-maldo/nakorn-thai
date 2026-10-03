package au.com.nakornthai.notification.infrastructure;
import au.com.nakornthai.notification.domain.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
@Service @RequiredArgsConstructor @Slf4j @EnableScheduling
public class NotificationDeliveryWorker {
 private final EntityManager em;
 private final SmsSender sms;
 private final EmailSender email;
 private final Clock clock;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 private final PlatformTransactionManager transactionManager;
 @Scheduled(fixedDelayString="${NOTIFICATION_POLL_MS:10000}")
 public void deliver() {
  var transaction=new TransactionTemplate(transactionManager);
  transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  for(int i=0;i<10;i++) {
   boolean processed=Boolean.TRUE.equals(transaction.execute(status -> {
    var jobs=em.createNativeQuery("SELECT * FROM notification_delivery WHERE status<>'SENT' AND attempts<5 AND next_attempt_at<=:now ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED",NotificationDeliveryJpaEntity.class).setParameter("now",clock.instant()).getResultList();
    if(jobs.isEmpty())return false;
    // Keep this one row locked through delivery; releasing it earlier would allow
    // another worker to send the same message before its outcome is recorded.
    deliverOne((NotificationDeliveryJpaEntity)jobs.getFirst());
    return true;
   }));
   if(!processed)break;
  }
 }
 public void deliverOne(NotificationDeliveryJpaEntity n) {
  if(n.getStatus()==DeliveryStatus.SENT || n.getAttempts()>=5)return;
  n.setAttempts(n.getAttempts()+1);
  try {
   if(n.getChannel()==NotificationChannel.SMS)sms.send(n.getRecipient(),n.getBody());else email.send(n.getRecipient(),n.getSubject(),n.getBody());
   n.setStatus(DeliveryStatus.SENT);n.setSentAt(clock.instant());n.setLastError(null);
   metrics.counter("notification.delivery.attempts","channel",n.getChannel().name(),"type",n.getType().name(),"result","sent").increment();
   log.info("notification_sent type={} channel={} id={}",n.getType(),n.getChannel(),n.getId());
  }catch(Exception failure) {
   n.setStatus(DeliveryStatus.FAILED);n.setLastError("Delivery unavailable");n.setNextAttemptAt(clock.instant().plusSeconds(60L*(1L<<(n.getAttempts()-1))));
   metrics.counter("notification.delivery.attempts","channel",n.getChannel().name(),"type",n.getType().name(),"result","failed").increment();
   log.warn("notification_failed type={} channel={} id={} attempt={} retry={}",n.getType(),n.getChannel(),n.getId(),n.getAttempts(),n.getAttempts()<5);
  }
 }
}
