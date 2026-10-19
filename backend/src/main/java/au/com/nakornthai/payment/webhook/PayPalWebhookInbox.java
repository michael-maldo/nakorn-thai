package au.com.nakornthai.payment.webhook;

import au.com.nakornthai.payment.infrastructure.*;
import au.com.nakornthai.payment.reconcile.PaymentReconciliationHandler;
import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Verified inbox stores identifiers only, never the sensitive provider payload. */
@Service @lombok.extern.slf4j.Slf4j
public class PayPalWebhookInbox {
 private final PayPalPaymentProvider paypal;private final JdbcTemplate jdbc;private final EntityManager em;
 private final PaymentReconciliationHandler reconciliation;private final ObjectMapper json;
 private final TransactionTemplate transaction;private final Clock clock;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 public PayPalWebhookInbox(PayPalPaymentProvider paypal,JdbcTemplate jdbc,EntityManager em,PaymentReconciliationHandler reconciliation,ObjectMapper json,PlatformTransactionManager manager,Clock clock,io.micrometer.core.instrument.MeterRegistry metrics){
  this.paypal=paypal;this.jdbc=jdbc;this.em=em;this.reconciliation=reconciliation;this.json=json;this.transaction=new TransactionTemplate(manager);this.clock=clock;this.metrics=metrics;
 }
 public void receive(HttpHeaders headers,String body){
  if(body.length()>131072)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  JsonNode event;try{event=json.readTree(body);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid payment event");}
  String identity=paypal.identity();
  if(!paypal.verifyWebhook(headers,event)){metrics.counter("nakorn.payment.webhook.verification","result","rejected").increment();throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid payment event signature");}
  String id=event.path("id").asText(),type=event.path("event_type").asText(),resource=event.path("resource").path("id").asText();
  if(!id.matches("[A-Za-z0-9-]{1,100}")||!type.matches("[A-Z._]{1,80}")||!resource.matches("[A-Za-z0-9]{1,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid payment event identifiers");
  jdbc.update("INSERT INTO paypal_webhook_event(event_id,event_type,resource_id,provider_identity) VALUES (?,?,?,?) ON CONFLICT(event_id) DO NOTHING",id,type,resource,identity);
  var stored=jdbc.queryForMap("SELECT event_type,resource_id,provider_identity FROM paypal_webhook_event WHERE event_id=?",id);
  if(!type.equals(stored.get("event_type"))||!resource.equals(stored.get("resource_id"))||!identity.equals(stored.get("provider_identity")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment event identifiers changed");
  metrics.counter("nakorn.payment.webhook.verification","result","verified").increment();
  log.info("payment_webhook_verified event={} type={}",id,type);
 }
 public boolean processOne(){
  UUID lease=UUID.randomUUID();
  Map<String,Object> job=transaction.execute(tx->{
   var rows=jdbc.queryForList("SELECT * FROM paypal_webhook_event WHERE processed_at IS NULL AND attempts<8 AND next_attempt_at<=? AND (lease_until IS NULL OR lease_until<=?) ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED",java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()));
   if(rows.isEmpty())return null;var row=rows.getFirst();
   jdbc.update("UPDATE paypal_webhook_event SET lease_id=?,lease_until=?,attempts=attempts+1 WHERE event_id=?",lease,java.sql.Timestamp.from(clock.instant().plusSeconds(180)),row.get("event_id"));return row;
  });
  if(job==null)return false;
  try{
   var provider=paypal.session();
   if(!Objects.equals(job.get("provider_identity"),provider.identity()))throw new IllegalStateException("IDENTITY_CHANGED");
   String type=(String)job.get("event_type"),resource=(String)job.get("resource_id");
   if(type.startsWith("CHECKOUT.ORDER.")){
    UUID order=findOrder(resource);if(!reconciliation.reconcile(order,true,true))throw new IllegalStateException("PAYMENT_BUSY");
   }else if(type.startsWith("PAYMENT.CAPTURE.")){
    if(type.equals("PAYMENT.CAPTURE.REFUNDED"))processRefund(provider,resource);
    else processCapture(provider,resource,type);
   }
   jdbc.update("UPDATE paypal_webhook_event SET processed_at=?,lease_id=NULL,lease_until=NULL,last_error=NULL WHERE event_id=? AND lease_id=?",java.sql.Timestamp.from(clock.instant()),job.get("event_id"),lease);
   metrics.counter("nakorn.payment.webhook.processing","result","processed").increment();
  }catch(Exception error){
   int attempt=((Number)job.get("attempts")).intValue()+1;
   jdbc.update("UPDATE paypal_webhook_event SET lease_id=NULL,lease_until=NULL,last_error='RECONCILIATION_REQUIRED',next_attempt_at=? WHERE event_id=? AND lease_id=?",java.sql.Timestamp.from(clock.instant().plusSeconds(Math.min(3600,30L*(1L<<Math.min(7,attempt-1))))),job.get("event_id"),lease);
   metrics.counter("nakorn.payment.webhook.processing","result","failed").increment();
   log.warn("payment_webhook_reconciliation_failed event={} attempt={} review={}",job.get("event_id"),attempt,attempt>=8);
  }
  return true;
 }
 private UUID findOrder(String providerId){var rows=jdbc.queryForList("SELECT order_id FROM order_payment WHERE method='PAYPAL' AND provider_order_id=?",UUID.class,providerId);if(rows.size()!=1)throw new IllegalStateException("ORDER_NOT_FOUND");return rows.getFirst();}
 private void processCapture(PayPalPaymentProvider provider,String ref,String type){
  var capture=provider.captureDetails(ref);String providerId=capture.path("supplementary_data").path("related_ids").path("order_id").asText();
  UUID id=findOrder(providerId);
  if(type.equals("PAYMENT.CAPTURE.PENDING")||type.equals("PAYMENT.CAPTURE.DENIED")){
   // Event ordering is irrelevant: the current order/capture state is authoritative.
   reconciliation.reconcile(id,false,true);return;
  }
  var details=provider.details(providerId);var amount=jdbc.queryForObject("SELECT total_minor FROM restaurant_order WHERE id=?",Long.class,id);
  PayPalPaymentProvider.validateOrder(details,providerId,id,amount);
  boolean reversal=type.equals("PAYMENT.CAPTURE.REVERSED");
  if(reversal)PaymentReconciliationHandler.validateCaptureLinkage(capture,ref,providerId,amount);
  else PaymentReconciliationHandler.validateCaptureResource(capture,ref,providerId,amount);
  transaction.executeWithoutResult(tx->{
   var o=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,id);requireIdentity(p,provider.identity());
   String state=capture.path("status").asText();
   if(!reversal||Set.of("COMPLETED","REFUNDED","PARTIALLY_REFUNDED").contains(state)&&capture.path("final_capture").asBoolean())reconciliation.recordCapture(o,p,ref);
   if(reversal){
    // Orders/Payments lookup cannot prove the final dispute outcome. Preserve capture, freeze fulfillment.
    p.setFinancialStatus("MANUAL_REVIEW");if(o.getPaidAt()!=null)o.setRefundStatus("MANUAL_REVIEW");reconciliation.review(o,p,"VERIFIED_REVERSAL_REQUIRES_REVIEW");
   }else reconciliation.recordFinancialState(o,p,state);
   o.setUpdatedAt(clock.instant());reconciliation.audit(id,"WEBHOOK_FINANCIAL_CHECK","PAYPAL",type);
  });
 }
 private void processRefund(PayPalPaymentProvider provider,String refundId){
  var refund=provider.refundDetails(refundId);if(!refundId.equals(refund.path("id").asText()))throw new IllegalStateException("REFUND_MISMATCH");
  String captureId=null;
  for(var link:refund.path("links"))if("up".equals(link.path("rel").asText())){
   var uri=java.net.URI.create(link.path("href").asText());
   // Extract an ID only. Never follow webhook/HATEOAS URLs with merchant credentials.
   var matcher=java.util.regex.Pattern.compile("/v2/payments/captures/([A-Za-z0-9]{1,100})").matcher(uri.getPath());
   if(matcher.matches())captureId=matcher.group(1);
  }
  if(captureId==null)throw new IllegalStateException("REFUND_LINKAGE_MISSING");
  var capture=provider.captureDetails(captureId);String providerId=capture.path("supplementary_data").path("related_ids").path("order_id").asText();
  UUID id=findOrder(providerId);long total=jdbc.queryForObject("SELECT total_minor FROM restaurant_order WHERE id=?",Long.class,id);
  var details=provider.details(providerId);PayPalPaymentProvider.validateOrder(details,providerId,id,total);
  PaymentReconciliationHandler.validateCaptureResource(capture,captureId,providerId,total);
  String state=refund.path("status").asText();if(!Set.of("PENDING","COMPLETED","FAILED","CANCELLED").contains(state))throw new IllegalStateException("REFUND_STATE_UNKNOWN");
  long minor;try{var amount=refund.path("amount");if(!"AUD".equals(amount.path("currency_code").asText())||!amount.path("value").asText().matches("[0-9]+[.][0-9]{2}"))throw new IllegalArgumentException();minor=new BigDecimal(amount.path("value").asText()).movePointRight(2).longValueExact();if(minor<=0||minor>total)throw new IllegalArgumentException();}catch(Exception e){throw new IllegalStateException("REFUND_AMOUNT_MISMATCH");}
  final String ref=captureId;
  transaction.executeWithoutResult(tx->{
   var o=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,id);requireIdentity(p,provider.identity());
   reconciliation.recordCapture(o,p,ref);
   reconciliation.recordFinancialState(o,p,capture.path("status").asText());
   var existing=jdbc.queryForList("SELECT * FROM paypal_refund WHERE provider_refund_id=?",refundId);
   if(!existing.isEmpty()&&(!id.equals(existing.getFirst().get("order_id"))||((Number)existing.getFirst().get("amount_minor")).longValue()!=minor||!ref.equals(existing.getFirst().get("capture_id"))))throw new IllegalStateException("REFUND_REFERENCE_REUSED");
   jdbc.update("INSERT INTO paypal_refund(provider_refund_id,order_id,capture_id,amount_minor,status) VALUES (?,?,?,?,?) ON CONFLICT(provider_refund_id) DO UPDATE SET status=CASE WHEN paypal_refund.status='COMPLETED' THEN 'COMPLETED' ELSE EXCLUDED.status END,updated_at=CURRENT_TIMESTAMP",refundId,id,ref,minor,state);
   long refunded=jdbc.queryForObject("SELECT coalesce(sum(amount_minor),0) FROM paypal_refund WHERE order_id=? AND status='COMPLETED'",Long.class,id);
   if(refunded>total)throw new IllegalStateException("REFUND_TOTAL_MISMATCH");
   o.setPaymentReviewRequired(true);
   if(refunded==total||capture.path("status").asText().equals("REFUNDED")){reconciliation.recordFinancialState(o,p,"REFUNDED");}
   else if(!o.getRefundStatus().equals("COMPLETED")){
    if(refunded>0)reconciliation.recordFinancialState(o,p,"PARTIALLY_REFUNDED");
    o.setRefundStatus(state.equals("PENDING")?"PENDING":"MANUAL_REVIEW");
   }
   o.setUpdatedAt(clock.instant());reconciliation.audit(id,"REFUND_RECONCILED","PAYPAL",state);
  });
  if(state.equals("PENDING"))throw new IllegalStateException("REFUND_PENDING");
 }
 private void requireIdentity(OrderPaymentJpaEntity p,String identity){if(p.getProviderIdentity()!=null&&!p.getProviderIdentity().equals(identity))throw new IllegalStateException("IDENTITY_CHANGED");if(p.getProviderIdentity()==null)p.setProviderIdentity(identity);}
}
