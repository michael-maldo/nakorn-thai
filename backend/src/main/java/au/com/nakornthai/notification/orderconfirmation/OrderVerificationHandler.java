package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.notification.infrastructure.*;
import au.com.nakornthai.ordering.infrastructure.OrderJpaEntity;
import au.com.nakornthai.ordering.createorder.CreateOrderHandler;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class OrderVerificationHandler {
 private final EntityManager em;private final TwilioVerifyClient provider;
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public UUID start(UUID orderId,String channel) {
  if(!provider.enabled(channel))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Verification channel unavailable");
  UUID challenge=UUID.randomUUID();var order=em.find(OrderJpaEntity.class,orderId,LockModeType.PESSIMISTIC_READ);
  if(order==null)return challenge;
  String to=channel.equals("sms")?order.getPhone().replaceAll("[^+0-9]",""):order.getEmail();
  if(to==null || to.isBlank())return challenge;
  if(channel.equals("sms") && to.matches("0[0-9]{9}"))to="+61"+to.substring(1);
  if(channel.equals("sms") && !to.matches("[+][1-9][0-9]{7,14}"))return challenge;
  String hash=CreateOrderHandler.hash(to.toLowerCase(Locale.ROOT));
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",Long.parseUnsignedLong(hash.substring(0,16),16)).getSingleResult();
  Number count=(Number)em.createNativeQuery("SELECT count(*) FROM order_verification WHERE destination_hash=:hash AND created_at>CURRENT_TIMESTAMP-interval '1 hour'",Long.class).setParameter("hash",hash).getSingleResult();
  Number recent=(Number)em.createNativeQuery("SELECT count(*) FROM order_verification WHERE destination_hash=:hash AND created_at>CURRENT_TIMESTAMP-interval '60 seconds'",Long.class).setParameter("hash",hash).getSingleResult();
  if(count.longValue()>=5 || recent.longValue()>0)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another code");
  var entry=new OrderVerificationJpaEntity();entry.setId(challenge);entry.setOrderId(orderId);entry.setChannel(channel);entry.setDestinationHash(hash);entry.setCreatedAt(Instant.now());entry.setExpiresAt(Instant.now().plusSeconds(600));em.persist(entry);em.flush();
  entry.setProviderSid(provider.start(to,channel));return challenge;
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Map<String,Object> check(UUID id,String code) {
  var entry=em.find(OrderVerificationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(entry==null || entry.isConsumed() || entry.getProviderSid()==null || entry.getAttempts()>=5 || !entry.getExpiresAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Code expired or invalid; request a new code");
  entry.setAttempts(entry.getAttempts()+1);
  if(!provider.check(entry.getProviderSid(),code))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Code expired or invalid");
  entry.setConsumed(true);byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
  var grant=new OrderTrackingGrantJpaEntity();grant.setOrderId(entry.getOrderId());grant.setTokenHash(CreateOrderHandler.hash(token));grant.setExpiresAt(Instant.now().plusSeconds(86400));em.persist(grant);
  return Map.of("requestId",entry.getOrderId(),"trackingToken",token,"expiresAt",grant.getExpiresAt());
 }
}
