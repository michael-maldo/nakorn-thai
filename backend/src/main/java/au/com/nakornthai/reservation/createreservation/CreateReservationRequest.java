package au.com.nakornthai.reservation.createreservation;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
public record CreateReservationRequest(@NotNull UUID requestId,
 @NotBlank @Size(max=100) String customerName,
 @NotBlank @Pattern(regexp="[+0-9 ()-]{6,30}") String phone,
 @Min(1) @Max(20) int partySize, @NotNull LocalDateTime requestedAt,
 @NotNull @Size(max=1000) String notes) {}
