package au.com.nakornthai.payment.refund;
import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import au.com.nakornthai.payment.reconcile.PaymentReconciliationHandler;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

/** Operational tracking only. This controller never issues provider refunds. */
@RestController @RequiredArgsConstructor
public class RefundObligationController {
 private final JdbcTemplate jdbc;private final EntityManager em;private final PaymentReconciliationHandler reconciliation;
 @GetMapping("/api/staff/payments/review") ResponseEntity<?> review(){
  var orders=jdbc.queryForList("SELECT o.id,o.status,o.payment_method,o.total_minor,o.paid_at,o.refund_status,o.payment_review_required,p.financial_status,p.review_reason FROM restaurant_order o LEFT JOIN order_payment p ON p.order_id=o.id WHERE o.refund_status IN ('REQUIRED','PENDING','MANUAL_REVIEW') OR o.payment_review_required OR (o.payment_method='PAYPAL' AND o.paid_at IS NULL AND o.created_at<CURRENT_TIMESTAMP-interval '5 hours') ORDER BY o.updated_at LIMIT 200");
  var events=jdbc.queryForList("SELECT event_id,event_type,received_at,attempts,last_error FROM paypal_webhook_event WHERE processed_at IS NULL AND attempts>=8 ORDER BY received_at LIMIT 100");
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("orders",orders,"webhookEvents",events));
 }
 public record Update(@Min(0) long version,@NotBlank @Pattern(regexp="REQUIRED|PENDING|COMPLETED|MANUAL_REVIEW") String status,@NotBlank @Size(max=100) String reference){}
 @PostMapping("/api/staff/payments/{id}/refund-obligation") @Transactional
 ResponseEntity<?> update(@PathVariable UUID id,@Valid @RequestBody Update request,Authentication actor){
  var o=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(o==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(o.getVersion()!=request.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"Order changed; refresh before recording refund progress");
  if(o.getPaidAt()==null||o.getRefundStatus().equals("NONE"))throw new ResponseStatusException(HttpStatus.CONFLICT,"No refund obligation to update");
  if(o.getPaymentMethod().equals("PAYPAL")&&request.status().equals("COMPLETED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"PayPal refunds require provider verification");
  if(o.getRefundStatus().equals("COMPLETED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Refund already completed");
  o.setRefundStatus(request.status());o.setUpdatedAt(Instant.now());
  reconciliation.audit(id,"REFUND_OBLIGATION_"+request.status(),actor.getName(),request.reference().trim());
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("refundStatus",o.getRefundStatus()));
 }
}
