package au.com.nakornthai.notification.reservationconfirmation;
import java.time.LocalDateTime;
import java.util.UUID;
public record SendReservationConfirmationCommand(UUID reservationId,String customerName,LocalDateTime requestedAt,int partySize,String verifiedPhone,String verifiedEmail) {}
