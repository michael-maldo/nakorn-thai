package au.com.nakornthai.payment.createpayment;

import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import au.com.nakornthai.payment.infrastructure.PayPalPaymentProvider;
import au.com.nakornthai.payment.reconcile.*;
import au.com.nakornthai.payment.webhook.PayPalWebhookInbox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import tools.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class CustomerPaymentReliabilityIntegrationTest {
 @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv("DB_TEST_USERNAME"));p.add("spring.datasource.password",()->System.getenv("DB_TEST_PASSWORD"));}
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 @Autowired PaymentReconciliationHandler reconciliation;@Autowired PaymentReconciliationWorker worker;@Autowired PayPalWebhookInbox inbox;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("paymentScheduler") org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler paymentScheduler;
 @Autowired @org.springframework.beans.factory.annotation.Qualifier("taskScheduler") org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler operationsScheduler;
 @MockitoBean PayPalPaymentProvider paypal;
 UUID id;String providerId,reference,eventPrefix;final String token="a".repeat(64),identity="b".repeat(64);
 volatile String orderState,captureState,refundState;
 final List<UUID> orders=new ArrayList<>();
 @BeforeEach void fixture(){
  id=UUID.randomUUID();orders.add(id);providerId="PP"+id.toString().replace("-","");reference="CAP"+id.toString().replace("-","");eventPrefix="WH-"+id;
  orderState="APPROVED";captureState="COMPLETED";refundState="COMPLETED";
  jdbc.update("INSERT INTO restaurant_order(id,tracking_hash,request_hash,customer_name,phone,notes,total_minor,payment_method,created_at,updated_at) VALUES (?,?,?,'Test','0400000000','',1990,'PAYPAL',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,CreateOrderHandler.hash(token),"c".repeat(64));
  jdbc.update("INSERT INTO order_payment(order_id,method,provider_order_id,provider_identity,updated_at) VALUES (?,'PAYPAL',?,?,CURRENT_TIMESTAMP)",id,providerId,identity);
  when(paypal.session()).thenReturn(paypal);when(paypal.identity()).thenReturn(identity);
  when(paypal.verifyWebhook(any(),any())).thenReturn(true);
  when(paypal.details(providerId)).thenAnswer(call->details());
  when(paypal.capture(providerId,id)).thenAnswer(call->{orderState="COMPLETED";return details();});
  when(paypal.captureDetails(reference)).thenAnswer(call->capture());
 }
 @AfterEach void clean(){
  jdbc.update("DELETE FROM paypal_webhook_event WHERE event_id LIKE ?",eventPrefix+"%");
  for(UUID order:orders){jdbc.update("DELETE FROM restaurant_order_event WHERE order_id=?",order);jdbc.update("DELETE FROM order_payment WHERE order_id=?",order);jdbc.update("DELETE FROM restaurant_order WHERE id=?",order);}orders.clear();
 }
 JsonNode details(){
  var unit=new HashMap<String,Object>();unit.put("custom_id",id.toString());unit.put("amount",Map.of("currency_code","AUD","value","19.90"));
  if(orderState.equals("COMPLETED"))unit.put("payments",Map.of("captures",List.of(capture())));
  return json.valueToTree(Map.of("id",providerId,"intent","CAPTURE","status",orderState,"purchase_units",List.of(unit)));
 }
 JsonNode capture(){return json.valueToTree(Map.of("id",reference,"status",captureState,"final_capture",true,"amount",Map.of("currency_code","AUD","value","19.90"),"supplementary_data",Map.of("related_ids",Map.of("order_id",providerId))));}
 JsonNode refund(String ref,String amount){return json.valueToTree(Map.of("id",ref,"status",refundState,"amount",Map.of("currency_code","AUD","value",amount),"links",List.of(Map.of("rel","up","href","https://api-m.sandbox.paypal.com/v2/payments/captures/"+reference))));}
 void event(String suffix,String type,String resource) throws Exception {
  mvc.perform(post("/api/payments/paypal/webhook").contentType("application/json").content(json.writeValueAsString(Map.of("id",eventPrefix+suffix,"event_type",type,"resource",Map.of("id",resource))))).andExpect(status().isNoContent());
 }
 void due(){jdbc.update("UPDATE order_payment SET reconcile_after=CURRENT_TIMESTAMP WHERE order_id=?",id);}
 String payment(String field){return jdbc.queryForObject("SELECT "+field+" FROM order_payment WHERE order_id=?",String.class,id);}
 String order(String field){return jdbc.queryForObject("SELECT "+field+" FROM restaurant_order WHERE id=?",String.class,id);}
 void assertPaid(){assertEquals("PAID",payment("status"));assertNotNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));assertEquals(reference,payment("confirmation_reference"));}
 @Test void customerCheckCapturesAndVerifiesServerTotal() throws Exception {
  mvc.perform(post("/api/payments/"+id+"/check").with(csrf()).header("X-Order-Token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.paid").value(true));assertPaid();
 }
 @Test void cancelledApprovalNeverBecomesCashOrPaid(){orderState="CREATED";worker.runBatch();assertEquals("PENDING",payment("status"));assertEquals("PAYPAL",order("payment_method"));verify(paypal,never()).capture(anyString(),any());}
 @Test void browserClosedAfterApprovalWorkerCapturesWithoutCustomerToken(){worker.runBatch();assertPaid();assertEquals("NEW",order("status"));}
 @Test void crashAfterRemoteCaptureRecoveredAfterLeaseExpiryWithoutRecapture(){orderState="COMPLETED";jdbc.update("UPDATE order_payment SET lease_id=?,lease_until=?,capture_attempted_at=? WHERE order_id=?",UUID.randomUUID(),java.sql.Timestamp.from(Instant.now().minusSeconds(1)),java.sql.Timestamp.from(Instant.now().minusSeconds(180)),id);worker.runBatch();assertPaid();verify(paypal,never()).capture(anyString(),any());}
 @Test void duplicateChecksNeverCaptureTwice(){assertTrue(reconciliation.reconcile(id,true,true));assertTrue(reconciliation.reconcile(id,true,true));assertPaid();verify(paypal,times(1)).capture(providerId,id);}
 @Test void duplicateWebhookIsDeduplicated() throws Exception {event("1","CHECKOUT.ORDER.APPROVED",providerId);event("1","CHECKOUT.ORDER.APPROVED",providerId);assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM paypal_webhook_event WHERE event_id=?",Integer.class,eventPrefix+"1"));inbox.processOne();assertPaid();assertFalse(inbox.processOne());verify(paypal,times(1)).capture(providerId,id);}
 @Test void invalidSignatureIsRejectedWithoutPersistence() throws Exception {when(paypal.verifyWebhook(any(),any())).thenReturn(false);mvc.perform(post("/api/payments/paypal/webhook").contentType("application/json").content("{\"id\":\""+eventPrefix+"1\",\"event_type\":\"CHECKOUT.ORDER.APPROVED\",\"resource\":{\"id\":\""+providerId+"\"}}")).andExpect(status().isUnauthorized());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM paypal_webhook_event WHERE event_id=?",Integer.class,eventPrefix+"1"));verify(paypal,never()).details(anyString());}
 @Test void unverifiableWebhookIsRetryableAndNotAcknowledged() throws Exception {when(paypal.verifyWebhook(any(),any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));mvc.perform(post("/api/payments/paypal/webhook").contentType("application/json").content("{}")).andExpect(status().isBadGateway());}
 @Test void timeoutBackoffThenLookupRecoversWithoutRecapture(){doAnswer(call->{orderState="COMPLETED";throw new ResponseStatusException(HttpStatus.BAD_GATEWAY);}).when(paypal).capture(providerId,id);worker.runBatch();assertEquals("PENDING",payment("status"));assertEquals(1,jdbc.queryForObject("SELECT reconcile_failures FROM order_payment WHERE order_id=?",Integer.class,id));worker.runBatch();verify(paypal,times(1)).capture(providerId,id);due();worker.runBatch();assertPaid();verify(paypal,times(1)).capture(providerId,id);}
 @Test void failuresAreBoundedAndBecomeReview(){when(paypal.details(providerId)).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY));for(int i=0;i<8;i++){due();worker.runBatch();}assertEquals("PROVIDER_UNAVAILABLE",payment("review_reason"));due();worker.runBatch();verify(paypal,times(8)).details(providerId);}
 @Test void ambiguousCreateIsNeverReplacedByWorker(){jdbc.update("UPDATE order_payment SET provider_order_id=NULL WHERE order_id=?",id);jdbc.update("UPDATE restaurant_order SET created_at=CURRENT_TIMESTAMP-interval '6 hours' WHERE id=?",id);worker.runBatch();assertEquals("UNRESOLVED_PROVIDER_CREATION",payment("review_reason"));verify(paypal,never()).create(any(),anyLong());}
 @Test void fullRefundBeforeCaptureNotificationPreservesCaptureAndRefund() throws Exception {
  orderState="COMPLETED";captureState="REFUNDED";String refundId="RF"+id.toString().replace("-","");when(paypal.refundDetails(refundId)).thenAnswer(call->refund(refundId,"19.90"));
  event("1","PAYMENT.CAPTURE.REFUNDED",refundId);inbox.processOne();assertPaid();assertEquals("COMPLETED",order("refund_status"));
  event("2","PAYMENT.CAPTURE.COMPLETED",reference);inbox.processOne();assertEquals("REFUNDED",payment("financial_status"));assertEquals("COMPLETED",order("refund_status"));
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM paypal_refund WHERE order_id=?",Integer.class,id));
 }
 @Test void pendingExternalRefundRechecksToCompletion() throws Exception {orderState="COMPLETED";String refundId="RF"+id.toString().replace("-","");refundState="PENDING";when(paypal.refundDetails(refundId)).thenAnswer(call->refund(refundId,"19.90"));event("1","PAYMENT.CAPTURE.REFUNDED",refundId);inbox.processOne();assertEquals("PENDING",order("refund_status"));refundState="COMPLETED";captureState="REFUNDED";jdbc.update("UPDATE paypal_webhook_event SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?",eventPrefix+"1");inbox.processOne();assertEquals("COMPLETED",order("refund_status"));assertPaid();}
 @Test void duplicateRefundEventsCannotDoubleRefundLedger() throws Exception {orderState="COMPLETED";captureState="PARTIALLY_REFUNDED";String ref="RF"+id.toString().replace("-","");when(paypal.refundDetails(ref)).thenAnswer(call->refund(ref,"5.00"));event("1","PAYMENT.CAPTURE.REFUNDED",ref);event("2","PAYMENT.CAPTURE.REFUNDED",ref);inbox.processOne();inbox.processOne();assertEquals(500,jdbc.queryForObject("SELECT sum(amount_minor) FROM paypal_refund WHERE order_id=?",Long.class,id));assertEquals("MANUAL_REVIEW",order("refund_status"));}
 @Test void verifiedReversalRequiresReviewAndCannotBeUndoneByLateCompletedEvent() throws Exception {orderState="COMPLETED";event("1","PAYMENT.CAPTURE.REVERSED",reference);inbox.processOne();assertPaid();assertEquals("MANUAL_REVIEW",payment("financial_status"));event("2","PAYMENT.CAPTURE.COMPLETED",reference);inbox.processOne();assertEquals("MANUAL_REVIEW",payment("financial_status"));mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version()+",\"status\":\"ACCEPTED\",\"pickupMinutes\":20,\"paymentCollected\":false}")).andExpect(status().isConflict());}
 @Test void paidCancellationCreatesRefundObligation() throws Exception {reconciliation.reconcile(id,true,true);mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version()+",\"status\":\"CANCELLED\",\"reason\":\"Cannot fulfil\",\"paymentCollected\":false}")).andExpect(status().isNoContent());assertEquals("REQUIRED",order("refund_status"));assertPaid();}
 long version(){return jdbc.queryForObject("SELECT version FROM restaurant_order WHERE id=?",Long.class,id);}
 @Test void cancelledFoodOrderNeverAutoCaptured(){jdbc.update("UPDATE restaurant_order SET status='CANCELLED' WHERE id=?",id);worker.runBatch();assertEquals("PENDING",payment("status"));verify(paypal,never()).capture(anyString(),any());}
 @Test void lateCompletedCaptureOnCancelledOrderCreatesRefundObligation(){jdbc.update("UPDATE restaurant_order SET status='CANCELLED' WHERE id=?",id);orderState="COMPLETED";worker.runBatch();assertPaid();assertEquals("REQUIRED",order("refund_status"));}
 @Test void payAtRestaurantAcceptanceNeverCallsPaypal() throws Exception {jdbc.update("DELETE FROM order_payment WHERE order_id=?",id);jdbc.update("UPDATE restaurant_order SET payment_method='PAY_AT_RESTAURANT' WHERE id=?",id);clearInvocations(paypal);mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":0,\"status\":\"ACCEPTED\",\"pickupMinutes\":20,\"paymentCollected\":false}")).andExpect(status().isNoContent());worker.runBatch();verifyNoInteractions(paypal);assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));}
 @Test void unpaidPaypalCannotBeAccepted() throws Exception {mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":0,\"status\":\"ACCEPTED\",\"pickupMinutes\":20,\"paymentCollected\":false}")).andExpect(status().isConflict());}
 @Test void staffCannotForgePaypalPaymentOrCompleteRefund() throws Exception {mvc.perform(post("/api/staff/payments/"+id+"/payid-confirm").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":0,\"bankReference\":\"fake\",\"bankReceiptChecked\":true}")).andExpect(status().isConflict());reconciliation.reconcile(id,true,true);jdbc.update("UPDATE restaurant_order SET refund_status='REQUIRED' WHERE id=?",id);mvc.perform(post("/api/staff/payments/"+id+"/refund-obligation").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version()+",\"status\":\"COMPLETED\",\"reference\":\"fake\"}")).andExpect(status().isConflict());}
 @Test void financialReviewRequiresStaffAndRetainsCsrf() throws Exception {mvc.perform(get("/api/staff/payments/review")).andExpect(status().isUnauthorized());mvc.perform(get("/api/staff/payments/review").with(user("boh").roles("BOH"))).andExpect(status().isForbidden());mvc.perform(get("/api/staff/payments/review").with(user("foh").roles("FOH"))).andExpect(status().isOk()).andExpect(jsonPath("$.orders").isArray());mvc.perform(post("/api/staff/payments/"+id+"/refund-obligation").with(user("foh").roles("FOH")).contentType("application/json").content("{}")).andExpect(status().isForbidden());}
 @Test void changedProviderIdentityCannotCapture(){when(paypal.identity()).thenReturn("d".repeat(64));worker.runBatch();assertEquals("PROVIDER_IDENTITY_CHANGED",payment("review_reason"));verify(paypal,never()).details(anyString());}
 @Test void oldUncertainCaptureIsNeverRetried(){jdbc.update("UPDATE order_payment SET capture_attempted_at=CURRENT_TIMESTAMP-interval '6 hours' WHERE order_id=?",id);worker.runBatch();verify(paypal,never()).capture(anyString(),any());assertEquals("CAPTURE_OUTCOME_REQUIRES_REVIEW",payment("review_reason"));}
 @Test void staleFirstApprovalRequiresReviewWithoutCapture(){jdbc.update("UPDATE restaurant_order SET created_at=CURRENT_TIMESTAMP-interval '6 hours' WHERE id=?",id);worker.runBatch();verify(paypal,never()).capture(anyString(),any());assertNotNull(payment("review_reason"));}
 @Test void cancellationCannotRaceAnAuthorizedCapture() throws Exception {
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
  doAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));orderState="COMPLETED";return details();}).when(paypal).capture(providerId,id);
  try {
   var running=executor.submit(()->reconciliation.reconcile(id,true,true));assertTrue(entered.await(5,TimeUnit.SECONDS));
   mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version()+",\"status\":\"CANCELLED\",\"reason\":\"Cannot fulfil\",\"paymentCollected\":false}")).andExpect(status().isConflict());
   release.countDown();assertTrue(running.get(5,TimeUnit.SECONDS));assertPaid();assertEquals("NEW",order("status"));
  }finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
 }
 @Test void webhookWrongOrderLinkageCannotMarkPaid() throws Exception {
  orderState="COMPLETED";var bad=json.valueToTree(Map.of("id",providerId,"intent","CAPTURE","status","COMPLETED","purchase_units",List.of(Map.of("custom_id",UUID.randomUUID().toString(),"amount",Map.of("currency_code","AUD","value","19.90")))));
  when(paypal.details(providerId)).thenReturn(bad);event("1","PAYMENT.CAPTURE.COMPLETED",reference);inbox.processOne();assertEquals("PENDING",payment("status"));assertNull(jdbc.queryForObject("SELECT processed_at FROM paypal_webhook_event WHERE event_id=?",Object.class,eventPrefix+"1"));
 }
 @Test void exhaustedWebhookRemainsVisibleForManualReview() throws Exception {
  event("1","CHECKOUT.ORDER.APPROVED","UNKNOWN");for(int i=0;i<8;i++){jdbc.update("UPDATE paypal_webhook_event SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?",eventPrefix+"1");assertTrue(inbox.processOne());}
  assertFalse(inbox.processOne());mvc.perform(get("/api/staff/payments/review").with(user("foh").roles("FOH"))).andExpect(status().isOk()).andExpect(jsonPath("$.webhookEvents[0].event_id").value(eventPrefix+"1"));verify(paypal,never()).capture(anyString(),any());
 }
 @Test void independentRequestsCannotCaptureThroughTheSameLease() throws Exception {
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
  doAnswer(call->{entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));orderState="COMPLETED";return details();}).when(paypal).capture(providerId,id);
  try{var running=executor.submit(()->reconciliation.reconcile(id,true,true));assertTrue(entered.await(5,TimeUnit.SECONDS));assertFalse(reconciliation.reconcile(id,true,false));release.countDown();assertTrue(running.get(5,TimeUnit.SECONDS));verify(paypal,times(1)).capture(providerId,id);}finally{release.countDown();executor.shutdownNow();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
 }
 @Test void staffCanTrackRefundProgressButNotForgePaypalCompletion() throws Exception {
  reconciliation.reconcile(id,true,true);jdbc.update("UPDATE restaurant_order SET refund_status='REQUIRED' WHERE id=?",id);
  mvc.perform(post("/api/staff/payments/"+id+"/refund-obligation").with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content("{\"version\":"+version()+",\"status\":\"PENDING\",\"reference\":\"ticket-123\"}")).andExpect(status().isOk());assertEquals("PENDING",order("refund_status"));assertPaid();
  assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM payment_audit WHERE order_id=? AND action='REFUND_OBLIGATION_PENDING' AND actor='foh'",Integer.class,id));
 }
 @Test void slowPaymentWorkDoesNotBlockOtherScheduledOperations() throws Exception {
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  var slow=paymentScheduler.submit(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals("available",operationsScheduler.submit(()->"available").get(2,TimeUnit.SECONDS));}
  finally{release.countDown();slow.get(5,TimeUnit.SECONDS);}
 }
 @Test void reversalWithoutCompletedCaptureEvidenceNeverInventsPayment() throws Exception {
  orderState="COMPLETED";captureState="FAILED";event("1","PAYMENT.CAPTURE.REVERSED",reference);inbox.processOne();
  assertEquals("PENDING",payment("status"));assertEquals("MANUAL_REVIEW",payment("financial_status"));assertNull(jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));
 }
 @Test void adverseReversalStateRetainsPreviouslyRecordedCapture() throws Exception {
  reconciliation.reconcile(id,true,true);var paidAt=jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id);
  captureState="FAILED";event("1","PAYMENT.CAPTURE.REVERSED",reference);inbox.processOne();assertPaid();
  assertEquals(paidAt,jdbc.queryForObject("SELECT paid_at FROM restaurant_order WHERE id=?",Object.class,id));assertEquals("MANUAL_REVIEW",order("refund_status"));
 }
}
