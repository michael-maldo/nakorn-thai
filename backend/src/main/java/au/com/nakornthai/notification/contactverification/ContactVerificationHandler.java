package au.com.nakornthai.notification.contactverification;
import au.com.nakornthai.notification.domain.*;
import au.com.nakornthai.notification.infrastructure.ContactVerificationJpaEntity;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Slf4j
public class ContactVerificationHandler {
 private final EntityManager em;
 private final ContactVerificationProvider provider;
 private final Clock clock;
 private final io.micrometer.core.instrument.MeterRegistry metrics;
 public Map<String,Boolean> options(){return Map.of("sms",provider.enabled("sms"),"email",provider.enabled("email"));}
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Map<String,Object> start(String channel,String destination) {
  String to=ContactDestination.normalize(channel,destination);
  if(to==null)throw failure(HttpStatus.BAD_REQUEST,"Contact destination required");
  if(!provider.enabled(channel.toLowerCase(Locale.ROOT)))throw failure(HttpStatus.SERVICE_UNAVAILABLE,"Verification channel unavailable");
  String hash=ContactDestination.hash(channel+":"+to);
  Instant now=clock.instant();
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",Long.parseUnsignedLong(hash.substring(0,16),16)).getSingleResult();
  Number count=(Number)em.createNativeQuery("SELECT count(*) FROM contact_verification WHERE destination_hash=:hash AND created_at>:since",Long.class).setParameter("hash",hash).setParameter("since",now.minusSeconds(3600)).getSingleResult();
  Number recent=(Number)em.createNativeQuery("SELECT count(*) FROM contact_verification WHERE destination_hash=:hash AND created_at>:since",Long.class).setParameter("hash",hash).setParameter("since",now.minusSeconds(60)).getSingleResult();
  if(count.longValue()>=5 || recent.longValue()>0)throw failure(HttpStatus.TOO_MANY_REQUESTS,"Please wait before requesting another code");
  var entry=new ContactVerificationJpaEntity();entry.setId(UUID.randomUUID());entry.setChannel(channel);entry.setDestinationHash(hash);entry.setCreatedAt(now);entry.setExpiresAt(now.plusSeconds(600));em.persist(entry);em.flush();
  entry.setProviderReference(provider.start(to,channel.toLowerCase(Locale.ROOT)));
  metrics.counter("reservation.contact.verification.requests","channel",channel).increment();
  log.info("contact_verification_requested channel={} id={}",channel,entry.getId());
  return Map.of("id",entry.getId(),"expiresAt",entry.getExpiresAt(),"resendAt",now.plusSeconds(60));
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Map<String,Object> verify(UUID id,String code) {
  var entry=em.find(ContactVerificationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(entry==null || (entry.getConsumedBy()!=null || entry.getConsumedOrderId()!=null) || !entry.getExpiresAt().isAfter(clock.instant()))throw failure(HttpStatus.BAD_REQUEST,"Code expired or invalid; request a new code");
  if(entry.getVerifiedAt()!=null)return Map.of("id",id,"verified",true,"expiresAt",entry.getExpiresAt());
  if(entry.getAttempts()>=5 || entry.getProviderReference()==null)throw failure(HttpStatus.BAD_REQUEST,"Attempt limit reached; request a new code");
  entry.setAttempts(entry.getAttempts()+1);
  if(!provider.check(entry.getProviderReference(),code)) {
   metrics.counter("reservation.contact.verification.checks","channel",entry.getChannel(),"result","failed").increment();
   log.info("contact_verification_failed channel={} id={}",entry.getChannel(),id);
   throw failure(HttpStatus.BAD_REQUEST,"Code invalid or expired");
  }
  metrics.counter("reservation.contact.verification.checks","channel",entry.getChannel(),"result","verified").increment();
  entry.setVerifiedAt(clock.instant());log.info("contact_verification_succeeded channel={} id={}",entry.getChannel(),id);
  return Map.of("id",id,"verified",true,"expiresAt",entry.getExpiresAt());
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public boolean consume(UUID id,String channel,String destination,UUID reservationId) {
  return consume(id,channel,destination,reservationId,null);
 }
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
 public boolean consumeForOrder(UUID id,String destination,UUID orderId) {
  return consume(id,"SMS",destination,null,orderId);
 }
 private boolean consume(UUID id,String channel,String destination,UUID reservationId,UUID orderId) {
  if(id==null)return false;
  var entry=em.find(ContactVerificationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(destination==null || entry==null || entry.getVerifiedAt()==null || !entry.getExpiresAt().isAfter(clock.instant()) || (entry.getConsumedBy()!=null || entry.getConsumedOrderId()!=null) || !channel.equals(entry.getChannel()) || !ContactDestination.hash(channel+":"+destination).equals(entry.getDestinationHash()))
   throw failure(HttpStatus.BAD_REQUEST,"Contact verification invalid, expired or already used");
  entry.setConsumedBy(reservationId);entry.setConsumedOrderId(orderId);return true;
 }
 private static ResponseStatusException failure(HttpStatus status,String message){return new ResponseStatusException(status,message);}
}
