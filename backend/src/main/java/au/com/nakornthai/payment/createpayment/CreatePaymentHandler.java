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
@Service
public class CreatePaymentHandler {
 private final EntityManager em;private final OrderAccessService access;private final PayPalPaymentProvider paypal;
 private final String payid,name;private final boolean payidEnabled;
 public CreatePaymentHandler(EntityManager em,OrderAccessService access,PayPalPaymentProvider paypal,@Value("${PAYID_ENABLED:false}") boolean enabled,@Value("${PAYID_IDENTIFIER:}") String payid,@Value("${PAYID_ACCOUNT_NAME:}") String name) {
  this.em=em;this.access=access;this.paypal=paypal;this.payidEnabled=enabled;this.payid=payid;this.name=name;
  if(enabled && (payid.isBlank() || name.isBlank()))throw new IllegalArgumentException("Configure PayID identifier and account name");
 }
 public Map<String,Object> options(){return Map.of("paypal",paypal.enabled(),"payid",payidEnabled,"payAtRestaurant",true);}
 @Transactional public Map<String,Object> start(UUID id,String token,String method) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);access.require(order,token);
  if(Set.of("CANCELLED","COMPLETED").contains(order.getStatus()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Order is closed");
  var payment=em.find(OrderPaymentJpaEntity.class,id);
  if(order.getPaidAt()!=null)return view(order,payment);
  if(!order.getPaymentMethod().equals(method))throw new ResponseStatusException(HttpStatus.CONFLICT,"Use the payment method selected at checkout");
  if(payment!=null && !payment.getMethod().equals(method))throw new ResponseStatusException(HttpStatus.CONFLICT,"Payment already started with another method; contact the restaurant");
  if(!Set.of("PAYPAL","PAYID","PAY_AT_RESTAURANT").contains(method))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(method.equals("PAY_AT_RESTAURANT"))return view(order,null);
  if((method.equals("PAYPAL")&&!paypal.enabled()) || (method.equals("PAYID")&&!payidEnabled))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment method unavailable");
  if(payment==null){payment=new OrderPaymentJpaEntity();payment.setOrderId(id);payment.setMethod(method);em.persist(payment);order.setPaymentMethod(method);}
  if(method.equals("PAYPAL") && payment.getProviderOrderId()==null) {
   var response=paypal.create(id,order.getTotalMinor());String providerId=response.path("id").asText();String approve=null;
   for(var link:response.path("links"))if(Set.of("approve","payer-action").contains(link.path("rel").asText()))approve=link.path("href").asText();
   if(!providerId.matches("[A-Za-z0-9]+") || approve==null || !approve.matches("https://(www[.])?(sandbox[.])?paypal[.]com/.*"))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"PayPal approval unavailable");
   payment.setProviderOrderId(providerId);payment.setApprovalUrl(approve);
  }
  return view(order,payment);
 }
 @Transactional public Map<String,Object> check(UUID id,String token,boolean capture,boolean staff) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(staff){if(order==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);}else access.require(order,token);
  var p=em.find(OrderPaymentJpaEntity.class,id);
  if(p!=null && p.getMethod().equals("PAYPAL") && !p.getStatus().equals("PAID")) {
   var details=paypal.details(p.getProviderOrderId());
   if(capture && "APPROVED".equals(details.path("status").asText()) && !Set.of("CANCELLED","COMPLETED").contains(order.getStatus())){ paypal.capture(p.getProviderOrderId(),id); details=paypal.details(p.getProviderOrderId()); }
   String reference=PayPalPaymentProvider.validatedCapture(details,id,order.getTotalMinor());
   if(reference!=null)record(order,p,reference,"PAYPAL");
  }
  return view(order,p);
 }
 @Transactional public Map<String,Object> confirmPayid(UUID id,long version,String reference,String actor) {
  var order=em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);if(order==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  var p=em.find(OrderPaymentJpaEntity.class,id);
  if(p==null || !p.getMethod().equals("PAYID"))throw new ResponseStatusException(HttpStatus.CONFLICT,"No PayID payment to confirm");
  if(order.getPaidAt()!=null)return view(order,p);
  if(order.getVersion()!=version)throw new ResponseStatusException(HttpStatus.CONFLICT,"Order changed; refresh before confirming");
  record(order,p,reference.trim(),actor);return view(order,p);
 }
 private void record(OrderJpaEntity order,OrderPaymentJpaEntity p,String reference,String actor){
  p.setStatus("PAID");p.setConfirmationReference(reference);p.setConfirmedBy(actor);p.setUpdatedAt(Instant.now());order.setPaidAt(Instant.now());order.setUpdatedAt(Instant.now());
 }
 private Map<String,Object> view(OrderJpaEntity o,OrderPaymentJpaEntity p) {
  var result=new HashMap<String,Object>();result.put("method",o.getPaymentMethod());result.put("paid",o.getPaidAt()!=null);result.put("totalMinor",o.getTotalMinor());result.put("currency","AUD");
  if(p!=null){result.put("status",p.getStatus());result.put("approvalUrl",p.getApprovalUrl());}
  if(o.getPaymentMethod().equals("PAYID")){result.put("payid",payid);result.put("accountName",name);result.put("reference",o.getId().toString());}
  return result;
 }
}
