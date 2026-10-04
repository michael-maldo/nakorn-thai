package au.com.nakornthai.payment.createpayment;
import au.com.nakornthai.payment.infrastructure.*;
import au.com.nakornthai.ordering.infrastructure.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.Instant;
@Service @lombok.extern.slf4j.Slf4j
public class CreatePaymentHandler {
 private final EntityManager em;private final OrderAccessService access;private final PayPalPaymentProvider paypal;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 private final String payid,name;private final boolean payidEnabled;
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;

 private boolean payidEnabled(){if(configuration==null)return payidEnabled;var c=configuration.snapshot();return c.flag("payidEnabled")&&c.configured("PAYID");}
 private String payid(){return configuration==null?payid:configuration.snapshot().text("identifier");}
 private String accountName(){return configuration==null?name:configuration.snapshot().text("accountName");}
 private boolean cashEnabled(){return configuration==null||configuration.snapshot().flag("payAtRestaurantEnabled");}
 @org.springframework.beans.factory.annotation.Autowired
 public CreatePaymentHandler(EntityManager em,OrderAccessService access,PayPalPaymentProvider paypal,io.micrometer.core.instrument.MeterRegistry metrics,au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){
  this(em,access,paypal,metrics,false,"","");this.configuration=configuration;
 }
 public CreatePaymentHandler(EntityManager em,OrderAccessService access,PayPalPaymentProvider paypal,io.micrometer.core.instrument.MeterRegistry metrics,@Value("${PAYID_ENABLED:false}") boolean enabled,@Value("${PAYID_IDENTIFIER:}") String payid,@Value("${PAYID_ACCOUNT_NAME:}") String name) {
  this.metrics=metrics;this.em=em;this.access=access;this.paypal=paypal;this.payidEnabled=enabled;this.payid=payid;this.name=name;

 }
 public Map<String,Object> options(){return Map.of("paypal",paypal.enabled(),"payid",payidEnabled(),"payAtRestaurant",cashEnabled());}
 @Transactional(noRollbackFor=ResponseStatusException.class) public Map<String,Object> start(UUID id,String token,String method) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);access.require(order,token);
  if(Set.of("CANCELLED","COMPLETED").contains(order.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Order is closed");
  var payment=em.find(OrderPaymentJpaEntity.class,id);
  if(order.getPaidAt()!=null)return view(order,payment);
  if(!order.getPaymentMethod().equals(method))throw new ResponseStatusException(HttpStatus.CONFLICT,"Use the payment method selected at checkout");
  if(payment!=null && !payment.getMethod().equals(method))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment already started with another method; contact the restaurant");
  if(!Set.of("PAYPAL","PAYID","PAY_AT_RESTAURANT").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(method.equals("PAY_AT_RESTAURANT"))return view(order,null);
  if((method.equals("PAYPAL")&&!paypal.enabled()) || (method.equals("PAYID")&&!payidEnabled()))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment method unavailable");
  if(payment==null){payment=new OrderPaymentJpaEntity();payment.setOrderId(id);payment.setMethod(method);em.persist(payment);em.flush();metrics.counter("nakorn.payment.initiated","method",method).increment();
   log.info("payment_initiated method={} order={}",method,id);}
  if(method.equals("PAYPAL") && payment.getProviderOrderId()==null) {
   // Order creation survives a process crash that rolls back the first provider attempt.
   if(payment.getUpdatedAt().isBefore(Instant.now().minusSeconds(5*3600)) || order.getCreatedAt().isBefore(Instant.now().minusSeconds(5*3600)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment setup needs restaurant review; do not create another order");
   var response=paypal.create(id,order.getTotalMinor());
   if(response==null)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal approval unavailable");
   String providerId=response.path("id").asText();String approve=null;
   for(var link:response.path("links"))if(Set.of("approve","payer-action").contains(link.path("rel").asText()))approve=link.path("href").asText();
   if(!providerId.matches("[A-Za-z0-9]{1,100}") || !paypal.validApprovalUrl(approve))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal approval unavailable");
   PayPalPaymentProvider.validateOrder(response,providerId,id,order.getTotalMinor());
   payment.setProviderOrderId(providerId);payment.setApprovalUrl(approve);
   metrics.counter("nakorn.payment.provider_order_created","method","PAYPAL").increment();
  }
  return view(order,payment);
 }
 @Transactional public Map<String,Object> check(UUID id,String token,boolean capture,boolean staff) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(staff){if(order==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else access.require(order,token);
  var p=em.find(OrderPaymentJpaEntity.class,id);
  if(p!=null && p.getMethod().equals("PAYPAL") && !p.getStatus().equals("PAID")) {
   if(p.getProviderOrderId()==null)return view(order,p);
   var details=paypal.details(p.getProviderOrderId());
   PayPalPaymentProvider.validateOrder(details,p.getProviderOrderId(),id,order.getTotalMinor());
   if(capture && "APPROVED".equals(details.path("status").asText()) && !Set.of("CANCELLED","COMPLETED").contains(order.getStatus())){ paypal.capture(p.getProviderOrderId(),id); details=paypal.details(p.getProviderOrderId());
    PayPalPaymentProvider.validateOrder(details,p.getProviderOrderId(),id,order.getTotalMinor()); }
   String reference=PayPalPaymentProvider.validatedCapture(details,id,order.getTotalMinor());
   if(reference!=null)record(order,p,reference,"PAYPAL");
  }
  return view(order,p);
 }
 @Transactional public Map<String,Object> confirmPayid(UUID id,long version,String reference,String actor) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(order==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var p=em.find(OrderPaymentJpaEntity.class,id);
  if(p==null || !p.getMethod().equals("PAYID"))throw new ResponseStatusException(HttpStatus.CONFLICT,"No PayID payment to confirm");
  if(reference==null || reference.isBlank() || reference.trim().length()>150)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter the bank transaction reference");
  if(order.getPaidAt()!=null){
   if(!reference.trim().equals(p.getConfirmationReference()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment already confirmed with another reference");
   return view(order,p);
  }
  if(Set.of("CANCELLED","COMPLETED").contains(order.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Order is closed; reconcile any transfer with the restaurant");
  if(order.getVersion()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order changed; refresh before confirming");
  record(order,p,reference.trim(),actor);return view(order,p);
 }
 private void record(OrderJpaEntity order,OrderPaymentJpaEntity p,String reference,String actor){
  // Serialize reference reuse checks without logging raw bank/provider identifiers.
  String hash=au.com.nakornthai.ordering.createorder.CreateOrderHandler.hash(p.getMethod()+":"+reference);
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",Long.parseUnsignedLong(hash.substring(0,16),16)).getSingleResult();
  Number used=(Number)em.createNativeQuery("SELECT count(*) FROM order_payment WHERE method=:method AND confirmation_reference=:reference AND order_id<>:id",Long.class).setParameter("method",p.getMethod()).setParameter("reference",reference).setParameter("id",order.getId()).getSingleResult();
  if(used.longValue()>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment reference already recorded; review receipt before confirming");
  metrics.counter("nakorn.payment.confirmed","method",p.getMethod()).increment();
  log.info("payment_confirmed method={} order={} actor={}",p.getMethod(),order.getId(),actor);
  p.setStatus("PAID");p.setConfirmationReference(reference);p.setConfirmedBy(actor);p.setUpdatedAt(Instant.now());order.setPaidAt(Instant.now());order.setUpdatedAt(Instant.now());
 }
 private Map<String,Object> view(OrderJpaEntity o,OrderPaymentJpaEntity p) {
  var result=new HashMap<String,Object>();result.put("method",o.getPaymentMethod());result.put("paid",o.getPaidAt()!=null);result.put("totalMinor",o.getTotalMinor());result.put("currency","AUD");result.put("status",o.getPaidAt()!=null?"PAID":"PENDING");
  if(p!=null){result.put("status",p.getStatus());result.put("approvalUrl",p.getApprovalUrl());}
  if(o.getPaymentMethod().equals("PAYID")){result.put("payid",payid());result.put("accountName",accountName());result.put("reference",o.getId().toString());}
  return result;
 }
}
