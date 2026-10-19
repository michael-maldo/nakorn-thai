package au.com.nakornthai.payment.reconcile;
import au.com.nakornthai.payment.webhook.PayPalWebhookInbox;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service @Slf4j
public class PaymentReconciliationWorker {
 private final JdbcTemplate jdbc;private final PaymentReconciliationHandler handler;private final PayPalWebhookInbox inbox;
 private final boolean enabled;
 private final AtomicLong pending=new AtomicLong(),age=new AtomicLong(),review=new AtomicLong(),refunds=new AtomicLong(),webhookReview=new AtomicLong();
 public PaymentReconciliationWorker(JdbcTemplate jdbc,PaymentReconciliationHandler handler,PayPalWebhookInbox inbox,MeterRegistry metrics,@Value("${PAYPAL_RECONCILIATION_ENABLED:true}") boolean enabled){
  this.jdbc=jdbc;this.handler=handler;this.inbox=inbox;this.enabled=enabled;
  metrics.gauge("nakorn.payment.pending",pending);metrics.gauge("nakorn.payment.pending.oldest.seconds",age);
  metrics.gauge("nakorn.payment.manual.review",review);metrics.gauge("nakorn.payment.refund.obligations",refunds);metrics.gauge("nakorn.payment.webhook.manual.review",webhookReview);
 }
 @Scheduled(scheduler="paymentScheduler",fixedDelayString="${PAYPAL_RECONCILIATION_POLL_MS:30000}",initialDelayString="${PAYPAL_RECONCILIATION_INITIAL_DELAY_MS:30000}")
 public void tick(){if(!enabled)return;runBatch();}
 public void runBatch(){
  try(var ignored=MDC.putCloseable("requestId",UUID.randomUUID().toString())){
   // Five sequential events/payments per pass, across durable per-record leases.
   for(int i=0;i<5&&inbox.processOne();i++){}
   var ids=jdbc.queryForList("SELECT order_id FROM order_payment WHERE method='PAYPAL' AND review_reason IS NULL AND reconcile_after<=CURRENT_TIMESTAMP AND (lease_until IS NULL OR lease_until<=CURRENT_TIMESTAMP) ORDER BY status DESC,reconcile_after LIMIT 5",UUID.class);
   for(UUID id:ids)handler.reconcile(id,true,false);
   pending.set(jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE payment_method='PAYPAL' AND paid_at IS NULL",Long.class));
   age.set(jdbc.queryForObject("SELECT coalesce(extract(epoch FROM CURRENT_TIMESTAMP-min(created_at)),0)::bigint FROM restaurant_order WHERE payment_method='PAYPAL' AND paid_at IS NULL",Long.class));
   review.set(jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE payment_review_required",Long.class));
   refunds.set(jdbc.queryForObject("SELECT count(*) FROM restaurant_order WHERE refund_status IN ('REQUIRED','PENDING','MANUAL_REVIEW')",Long.class));
   webhookReview.set(jdbc.queryForObject("SELECT count(*) FROM paypal_webhook_event WHERE processed_at IS NULL AND attempts>=8",Long.class));
  }catch(Exception error){log.warn("payment_worker_failed reason=processing_unavailable");}
 }
}
