package au.com.nakornthai.notification.reservationconfirmation;
import au.com.nakornthai.notification.domain.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;
public record SendReservationConfirmationCommand(UUID reservationId,String customerName,LocalDateTime requestedAt,int partySize,String verifiedPhone,String email,NotificationType type) {
 public SendReservationConfirmationCommand(UUID reservationId,String customerName,LocalDateTime requestedAt,int partySize,String verifiedPhone,String email) {
  this(reservationId,customerName,requestedAt,partySize,verifiedPhone,email,NotificationType.RESERVATION_CONFIRMED);
 }
}
