package au.com.nakornthai.reservation.createreservation;
import au.com.nakornthai.reservation.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class CreateReservationHandler {
 private final SpringDataReservationRepository reservations;
 private final EntityManager em;
 @Transactional public Map<String,Object> handle(CreateReservationRequest request) {
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",request.requestId().getMostSignificantBits()).getSingleResult();
  var existing=reservations.findById(request.requestId());
  if(existing.isPresent()) {
   var r=existing.get();
   if(!r.getCustomerName().equals(request.customerName().trim()) || !r.getPhone().equals(request.phone().trim()) || r.getPartySize()!=request.partySize() || !r.getRequestedAt().equals(request.requestedAt()) || !r.getNotes().equals(request.notes().trim()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Request reference already used; start a new booking");
   return receipt(r);
  }
  var now=LocalDateTime.now(ZoneId.of("Australia/Melbourne"));
  if(!request.requestedAt().isAfter(now) || request.requestedAt().isAfter(now.plusDays(90)) || request.requestedAt().getSecond()!=0 || request.requestedAt().getNano()!=0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a future time within 90 days (Melbourne time)");
  var r=new ReservationJpaEntity();r.setId(request.requestId());r.setCustomerName(request.customerName().trim());r.setPhone(request.phone().trim());r.setPartySize(request.partySize());r.setRequestedAt(request.requestedAt());r.setNotes(request.notes().trim());
  reservations.saveAndFlush(r);return receipt(r);
 }
 private Map<String,Object> receipt(ReservationJpaEntity r) { return Map.of("reference",r.getId(),"message","Booking request received. Your table is not confirmed until staff contact you."); }
}
