package au.com.nakornthai.payment.reconcile;

import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.payment.infrastructure.*;
import au.com.nakornthai.restaurant.configuration.RuntimeConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** Short claim/apply transactions surround provider I/O. A lease fences concurrent callers.
 * Capture is allowed only after a second, locked eligibility check. No worker creates PayPal orders. */
@Service @Slf4j
public class PaymentReconciliationHandler {
 private final EntityManager em;
 private final JdbcTemplate jdbc;
 private final PayPalPaymentProvider paypal;
 private final OrderAccessService access;
 private final RuntimeConfiguration configuration;
 private final TransactionTemplate transaction;
 private final Clock clock;
 private final MeterRegistry metrics;
 public PaymentReconciliationHandler(EntityManager em,JdbcTemplate jdbc,PayPalPaymentProvider paypal,OrderAccessService access,
     RuntimeConfiguration configuration,PlatformTransactionManager manager,Clock clock,MeterRegistry metrics){
  this.em=em;this.jdbc=jdbc;this.paypal=paypal;this.access=access;this.configuration=configuration;
  this.transaction=new TransactionTemplate(manager);this.clock=clock;this.metrics=metrics;
 }
 record Claim(UUID id,UUID lease,String providerId,String identity,long total,Instant createdAt){}
 public Map<String,Object> check(UUID id,String token,boolean capture,boolean staff){
  transaction.executeWithoutResult(tx->{var o=em.find(OrderJpaEntity.class,id);if(staff){if(o==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else access.require(o,token);});
  reconcile(id,capture,true);
  return transaction.execute(tx->{var o=em.find(OrderJpaEntity.class,id);var p=em.find(OrderPaymentJpaEntity.class,id);
   var result=new HashMap<String,Object>();result.put("method",o.getPaymentMethod());result.put("paid",o.getPaidAt()!=null);
   result.put("status",o.getPaidAt()!=null?"PAID":"PENDING");result.put("currency","AUD");result.put("totalMinor",o.getTotalMinor());
   result.put("refundStatus",o.getRefundStatus());result.put("paymentReviewRequired",o.isPaymentReviewRequired());
   if(p!=null){result.put("approvalUrl",p.getApprovalUrl());result.put("financialStatus",p.getFinancialStatus());}
   if("PAYID".equals(o.getPaymentMethod())){var c=configuration.snapshot();result.put("payid",c.text("identifier"));result.put("accountName",c.text("accountName"));result.put("reference",id.toString());}
   return result;
  });
 }
 public boolean reconcile(UUID id,boolean allowCapture,boolean explicit){
  Claim c=transaction.execute(tx->claim(id,explicit));if(c==null)return false;
  metrics.counter("nakorn.payment.reconciliation.attempts").increment();
  try{
   // Freeze credentials/environment for this entire operation, including capture and follow-up reads.
   var provider=paypal.session();
   if(c.identity()!=null&&!c.identity().equals(provider.identity()))throw conflict("PROVIDER_IDENTITY_CHANGED");
   var details=provider.details(c.providerId());PayPalPaymentProvider.validateOrder(details,c.providerId(),id,c.total());
   if("APPROVED".equals(details.path("status").asText())&&allowCapture&&authorizeCapture(c)){
    provider.capture(c.providerId(),id);
    details=provider.details(c.providerId());PayPalPaymentProvider.validateOrder(details,c.providerId(),id,c.total());
   }
   JsonNode capture=financialCapture(details,c);
   JsonNode authoritative=null;
   // Read the Payments API for already-recorded captures and adverse capture states.
   if(capture!=null&&(!"COMPLETED".equals(capture.path("status").asText())||hasRecordedCapture(id)))
    authoritative=provider.captureDetails(capture.path("id").asText());
   final JsonNode found=capture, financial=authoritative;
   transaction.executeWithoutResult(tx->apply(c,found,financial));
   return true;
  }catch(Exception error){
   boolean mismatch=error instanceof ResponseStatusException r&&r.getStatusCode().value()==409;
   transaction.executeWithoutResult(tx->failed(c,mismatch?"PROVIDER_STATE_MISMATCH":"PROVIDER_UNAVAILABLE",mismatch));
   metrics.counter("nakorn.payment.reconciliation.failures").increment();
   log.warn("payment_reconciliation_failed order={} reason={}",id,mismatch?"validation":"provider_unavailable");
   if(explicit)throw error instanceof ResponseStatusException r?r:new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Payment status unavailable; retry the same order");
   return false;
  }
 }
 private Claim claim(UUID id,boolean explicit){
  var o=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,id);
  Instant now=clock.instant();
  if(o==null||p==null||!"PAYPAL".equals(o.getPaymentMethod())||!"PAYPAL".equals(p.getMethod()))return null;
  if(p.getLeaseUntil()!=null&&p.getLeaseUntil().isAfter(now))return null;
  if(!explicit&&(p.getReviewReason()!=null||p.getReconcileAfter().isAfter(now)))return null;
  if(p.getProviderOrderId()==null){
   if(o.getCreatedAt().isBefore(now.minusSeconds(5*3600)))review(o,p,"UNRESOLVED_PROVIDER_CREATION");
   else p.setReconcileAfter(now.plusSeconds(60));return null;
  }
  String identity=paypal.identity();if(p.getProviderIdentity()==null)p.setProviderIdentity(identity);
  if(p.getProviderIdentity()!=null&&!p.getProviderIdentity().equals(identity)){review(o,p,"PROVIDER_IDENTITY_CHANGED");return null;}
  UUID lease=UUID.randomUUID();p.setLeaseId(lease);p.setLeaseUntil(now.plusSeconds(180));p.setReconcileAttempts(p.getReconcileAttempts()+1);
  audit(id,"RECONCILIATION_CLAIMED","SYSTEM",Integer.toString(p.getReconcileAttempts()));
  return new Claim(id,lease,p.getProviderOrderId(),identity,o.getTotalMinor(),o.getCreatedAt());
 }
 private boolean authorizeCapture(Claim c){return Boolean.TRUE.equals(transaction.execute(tx->{
  var o=em.find(OrderJpaEntity.class,c.id(),LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,c.id());
  if(!owns(p,c)||!p.getLeaseUntil().isAfter(clock.instant())||o.getPaidAt()!=null||Set.of("CANCELLED","COMPLETED").contains(o.getStatus())||o.isPaymentReviewRequired()||!"NONE".equals(o.getRefundStatus())||p.getReviewReason()!=null)return false;
  // Reuse the original capture key only within a conservative provider idempotency window.
  if(p.getCaptureAttemptedAt()==null&&o.getCreatedAt().isBefore(clock.instant().minusSeconds(5*3600))){review(o,p,"APPROVAL_OUTSIDE_CAPTURE_WINDOW");return false;}
  if(p.getCaptureAttemptedAt()!=null&&p.getCaptureAttemptedAt().isBefore(clock.instant().minusSeconds(5*3600))){review(o,p,"CAPTURE_OUTCOME_REQUIRES_REVIEW");return false;}
  if(p.getCaptureAttemptedAt()==null)p.setCaptureAttemptedAt(clock.instant());
  audit(c.id(),"CAPTURE_REQUESTED","SYSTEM","PAYPAL");return true;
 }));}
 private boolean hasRecordedCapture(UUID id){return Boolean.TRUE.equals(transaction.execute(tx->em.find(OrderPaymentJpaEntity.class,id).getConfirmationReference()!=null));}
 private JsonNode financialCapture(JsonNode details,Claim c){
  if(!"COMPLETED".equals(details.path("status").asText()))return null;
  var captures=details.path("purchase_units").get(0).path("payments").path("captures");
  if(captures.isMissingNode()||captures.isNull()||captures.isArray()&&captures.isEmpty())return null;
  if(!captures.isArray()||captures.size()!=1)throw conflict("MULTIPLE_CAPTURES");
  var capture=captures.get(0);String state=capture.path("status").asText();
  if(Set.of("PENDING","DECLINED","FAILED").contains(state))return null;
  if(!Set.of("COMPLETED","REFUNDED","PARTIALLY_REFUNDED").contains(state)||!capture.path("final_capture").asBoolean()||!capture.path("id").asText().matches("[A-Za-z0-9]{1,100}"))throw conflict("INVALID_CAPTURE");
  PayPalPaymentProvider.validateAmount(capture.path("amount"),c.total());return capture;
 }
 private void apply(Claim c,JsonNode capture,JsonNode authoritative){
  var o=em.find(OrderJpaEntity.class,c.id(),LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,c.id());if(!owns(p,c))return;
  if(capture!=null){
   String ref=capture.path("id").asText();String state=capture.path("status").asText();
   if(authoritative!=null){validateCaptureResource(authoritative,ref,c.providerId(),c.total());state=authoritative.path("status").asText();}
   recordCapture(o,p,ref);
   recordFinancialState(o,p,state);
  }
  p.setLeaseId(null);p.setLeaseUntil(null);p.setReconcileFailures(0);p.setUpdatedAt(clock.instant());
  p.setReconcileAfter(clock.instant().plusSeconds(p.getStatus().equals("PAID")?86400:60));
  if(!p.getStatus().equals("PAID")&&c.createdAt().isBefore(clock.instant().minusSeconds(5*3600)))review(o,p,"APPROVAL_EXPIRED_OR_UNRESOLVED");
  audit(c.id(),"RECONCILED","SYSTEM",p.getFinancialStatus());
 }
 public static void validateCaptureResource(JsonNode capture,String captureId,String providerId,long total){
  validateCaptureLinkage(capture,captureId,providerId,total);
  if(!Set.of("COMPLETED","REFUNDED","PARTIALLY_REFUNDED").contains(capture.path("status").asText())||!capture.path("final_capture").asBoolean())throw conflict("CAPTURE_STATE_REQUIRES_REVIEW");
 }
 public static void validateCaptureLinkage(JsonNode capture,String captureId,String providerId,long total){
  if(capture==null||!captureId.equals(capture.path("id").asText())||!providerId.equals(capture.path("supplementary_data").path("related_ids").path("order_id").asText()))throw conflict("CAPTURE_LINKAGE_MISMATCH");
  PayPalPaymentProvider.validateAmount(capture.path("amount"),total);
 }
 /** Monotonic financial facts, shared by polling and webhook processing. Caller holds the order lock. */
 public void recordFinancialState(OrderJpaEntity o,OrderPaymentJpaEntity p,String state){
  String before=p.getFinancialStatus(),refund=o.getRefundStatus();boolean held=o.isPaymentReviewRequired();
  if("REFUNDED".equals(state)){p.setFinancialStatus("REFUNDED");o.setRefundStatus("COMPLETED");o.setPaymentReviewRequired(p.getReviewReason()!=null);}
  else if("PARTIALLY_REFUNDED".equals(state)){
   if(!Set.of("REFUNDED","MANUAL_REVIEW").contains(p.getFinancialStatus()))p.setFinancialStatus("PARTIALLY_REFUNDED");
   o.setPaymentReviewRequired(true);if(!"COMPLETED".equals(o.getRefundStatus()))o.setRefundStatus("MANUAL_REVIEW");
  }else if(!Set.of("REFUNDED","PARTIALLY_REFUNDED","MANUAL_REVIEW").contains(p.getFinancialStatus()))p.setFinancialStatus("CAPTURED");
  if(!before.equals(p.getFinancialStatus())||!refund.equals(o.getRefundStatus())||held!=o.isPaymentReviewRequired()){
   o.setUpdatedAt(clock.instant());audit(o.getId(),"FINANCIAL_STATE_VERIFIED","PAYPAL",p.getFinancialStatus());
  }
 }
 public void recordCapture(OrderJpaEntity o,OrderPaymentJpaEntity p,String reference){
  if(p.getConfirmationReference()!=null&&!p.getConfirmationReference().equals(reference))throw conflict("CAPTURE_REFERENCE_CHANGED");
  String hash=au.com.nakornthai.ordering.createorder.CreateOrderHandler.hash("PAYPAL:"+reference);
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",Long.parseUnsignedLong(hash.substring(0,16),16)).getSingleResult();
  Number used=(Number)em.createNativeQuery("SELECT count(*) FROM order_payment WHERE method='PAYPAL' AND confirmation_reference=:ref AND order_id<>:id",Long.class).setParameter("ref",reference).setParameter("id",o.getId()).getSingleResult();
  if(used.longValue()>0)throw conflict("CAPTURE_REFERENCE_REUSED");
  if(p.getReviewReason()!=null&&Set.of("PROVIDER_UNAVAILABLE","CAPTURE_OUTCOME_REQUIRES_REVIEW","APPROVAL_EXPIRED_OR_UNRESOLVED","APPROVAL_OUTSIDE_CAPTURE_WINDOW").contains(p.getReviewReason())){
   // Authoritative capture evidence resolves uncertainty; adverse financial/identity holds remain.
   p.setReviewReason(null);if("NONE".equals(o.getRefundStatus()))o.setPaymentReviewRequired(false);
  }
  if(o.getPaidAt()==null){
   p.setStatus("PAID");p.setConfirmationReference(reference);p.setConfirmedBy("PAYPAL");
   o.setPaidAt(clock.instant());o.setUpdatedAt(clock.instant());
   if("CANCELLED".equals(o.getStatus())&&"NONE".equals(o.getRefundStatus()))o.setRefundStatus("REQUIRED");
   audit(o.getId(),"CAPTURE_VERIFIED","PAYPAL","AUD");metrics.counter("nakorn.payment.confirmed","method","PAYPAL").increment();
   log.info("payment_capture_verified order={}",o.getId());
  }
 }
 private void failed(Claim c,String reason,boolean review){
  var o=em.find(OrderJpaEntity.class,c.id(),LockModeType.PESSIMISTIC_WRITE);var p=em.find(OrderPaymentJpaEntity.class,c.id());if(!owns(p,c))return;
  int failures=p.getReconcileFailures()+1;p.setReconcileFailures(failures);p.setLeaseId(null);p.setLeaseUntil(null);
  p.setReconcileAfter(clock.instant().plusSeconds(Math.min(3600,30L*(1L<<Math.min(7,failures-1)))));
  if(review||failures>=8)review(o,p,reason);
  audit(c.id(),"RECONCILIATION_FAILED","SYSTEM",reason);
 }
 public void review(OrderJpaEntity o,OrderPaymentJpaEntity p,String reason){p.setReviewReason(reason);o.setPaymentReviewRequired(true);o.setUpdatedAt(clock.instant());audit(o.getId(),"MANUAL_REVIEW","SYSTEM",reason);}
 private boolean owns(OrderPaymentJpaEntity p,Claim c){return p!=null&&c.lease().equals(p.getLeaseId());}
 public void audit(UUID order,String action,String actor,String detail){jdbc.update("INSERT INTO payment_audit(id,order_id,action,actor,detail) VALUES (?,?,?,?,?)",UUID.randomUUID(),order,action,actor,detail);}
 private static ResponseStatusException conflict(String reason){return new ResponseStatusException(HttpStatus.CONFLICT,"Payment requires restaurant review: "+reason);}
}
