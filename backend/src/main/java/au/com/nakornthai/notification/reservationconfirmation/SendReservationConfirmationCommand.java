package au.com.nakornthai.notification.reservationconfirmation;
import au.com.nakornthai.notification.domain.NotificationType;
import java.time.*;
import java.util.UUID;
public record SendReservationConfirmationCommand(UUID reservationId,String customerName,LocalDateTime requestedAt,int partySize,String verifiedPhone,String email,NotificationType type,ZoneId timezone) {
 public SendReservationConfirmationCommand(UUID id,String name,LocalDateTime time,int guests,String phone,String email,NotificationType type) {
  this(id,name,time,guests,phone,email,type,ZoneId.of("Australia/Melbourne"));
 }
 public SendReservationConfirmationCommand(UUID id,String name,LocalDateTime time,int guests,String phone,String email) {
  this(id,name,time,guests,phone,email,NotificationType.RESERVATION_CONFIRMED);
 }
}
