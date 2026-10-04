package au.com.nakornthai.reservation.listreservations;
import au.com.nakornthai.reservation.infrastructure.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/staff/reservations") @RequiredArgsConstructor
public class ListReservationsController {
 private final SpringDataReservationRepository reservations;
 private final EntityManager em;
 private final au.com.nakornthai.notification.reservationconfirmation.SendReservationConfirmationHandler confirmation;
 private au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 @org.springframework.beans.factory.annotation.Autowired public void configure(au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration){this.configuration=configuration;}
 @GetMapping ResponseEntity<List<ReservationJpaEntity>> list(@RequestParam LocalDate date) {
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reservations.findByRequestedAtGreaterThanEqualAndRequestedAtLessThanOrderByRequestedAtAsc(date.atStartOfDay(),date.plusDays(1).atStartOfDay()));
 }
 public record Update(@NotNull @Min(0) Long version,@NotNull String status,@NotNull @Size(max=500) String staffNote) {}
 @PatchMapping("/{id}") @Transactional @ResponseStatus(HttpStatus.NO_CONTENT)
 void update(@PathVariable UUID id,@Valid @RequestBody Update request, Authentication auth) {
  var r=em.find(ReservationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
  if(r==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Reservation not found");
  if(!r.getVersion().equals(request.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Booking changed; reload before saving");
  var allowed=switch(r.getStatus()) {
   case "REQUESTED" -> Set.of("CONFIRMED","DECLINED","CANCELLED");
   case "CONFIRMED" -> Set.of("SEATED","NO_SHOW","CANCELLED");
   default -> Set.<String>of();
  };
  if(!allowed.contains(request.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid booking status change");
  var type=switch(request.status()) {
   case "CONFIRMED" -> au.com.nakornthai.notification.domain.NotificationType.RESERVATION_CONFIRMED;
   case "DECLINED" -> au.com.nakornthai.notification.domain.NotificationType.RESERVATION_DECLINED;
   case "CANCELLED" -> au.com.nakornthai.notification.domain.NotificationType.RESERVATION_CANCELLED;
   default -> null;
  };
  // Historical email-only/unverified records remain manageable without claiming
  // that their phone was verified. Previously queued work remains untouched.
  if(type!=null && (r.isPhoneVerified() || configuration!=null&&!configuration.snapshot().flag("reservationPhoneRequired")))confirmation.handle(new au.com.nakornthai.notification.reservationconfirmation.SendReservationConfirmationCommand(r.getId(),r.getCustomerName(),r.getRequestedAt(),r.getPartySize(),r.isPhoneVerified()?r.getPhone():null,r.getEmail(),type));
  r.setStatus(request.status());r.setStaffNote(request.staffNote().trim());r.setUpdatedBy(auth.getName());r.setUpdatedAt(Instant.now());
 }
}
