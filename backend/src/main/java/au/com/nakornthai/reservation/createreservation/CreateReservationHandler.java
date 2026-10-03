package au.com.nakornthai.reservation.createreservation;
import au.com.nakornthai.reservation.infrastructure.*;
import lombok.RequiredArgsConstructor;
import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import au.com.nakornthai.restaurant.domain.RestaurantClosedException;
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
 private final RestaurantAvailabilityService availability;
 private final Clock clock;
 private final au.com.nakornthai.notification.contactverification.ContactVerificationHandler verification;
 private final au.com.nakornthai.notification.reservationconfirmation.SendReservationConfirmationHandler notifications;
 @Transactional public Map<String,Object> handle(CreateReservationRequest request) {
  em.createNativeQuery("SELECT pg_advisory_xact_lock(:key)",Object.class).setParameter("key",request.requestId().getMostSignificantBits()).getSingleResult();
  String phone=au.com.nakornthai.notification.domain.ContactDestination.normalize("SMS",request.phone());
  String email=au.com.nakornthai.notification.domain.ContactDestination.normalize("EMAIL",request.email());
  var existing=reservations.findById(request.requestId());
  if(existing.isPresent()) {
   var r=existing.get();
   if(!r.getCustomerName().equals(request.customerName().trim()) || !Objects.equals(r.getPhone(),phone) || !Objects.equals(r.getEmail(),email) || r.getPartySize()!=request.partySize() || !r.getRequestedAt().equals(request.requestedAt()) || !r.getNotes().equals(request.notes().trim()))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Request reference already used; start a new booking");
   return receipt(r);
  }
  phone=au.com.nakornthai.notification.domain.ContactDestination.normalizeMobile(request.phone());
  Instant operationInstant=clock.instant();
  var schedule=availability.schedule();
  var now=LocalDateTime.ofInstant(operationInstant,schedule.timezone());
  if(!request.requestedAt().isAfter(now) || request.requestedAt().isAfter(now.plusDays(90)) || request.requestedAt().getSecond()!=0 || request.requestedAt().getNano()!=0)
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a future time within 90 days (restaurant local time)");
  Instant requestedInstant;
  try { requestedInstant=schedule.requestedInstant(request.requestedAt()); }
  catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage()); }
  if(!schedule.isOpen(requestedInstant)) throw new RestaurantClosedException();
  var r=new ReservationJpaEntity();r.setCreatedAt(operationInstant);r.setUpdatedAt(operationInstant);r.setId(request.requestId());r.setCustomerName(request.customerName().trim());r.setPhone(phone);r.setEmail(email);r.setPartySize(request.partySize());r.setRequestedAt(request.requestedAt());r.setNotes(request.notes().trim());
  r.setPhoneVerified(verification.consume(request.phoneVerificationId(),"SMS",phone,r.getId()));
  if(!r.isPhoneVerified())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Verify your mobile by SMS before requesting a booking");
  reservations.saveAndFlush(r);
  notifications.handle(new au.com.nakornthai.notification.reservationconfirmation.SendReservationConfirmationCommand(r.getId(),r.getCustomerName(),r.getRequestedAt(),r.getPartySize(),r.getPhone(),r.getEmail(),au.com.nakornthai.notification.domain.NotificationType.RESERVATION_RECEIVED));
  return receipt(r);
 }
 private Map<String,Object> receipt(ReservationJpaEntity r) { return Map.of("reference",r.getId(),"message","Booking request received. Your table is not confirmed until staff contact you."); }
}
