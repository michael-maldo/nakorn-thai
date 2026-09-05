package au.com.nakornthai.reservation.createreservation;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFunctionEnquiryRequest(
    @NotNull UUID requestId,
    @NotBlank @Size(max=100) String customerName,
    @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Pattern(regexp="[+0-9 ()-]{6,30}") String phone,
    @NotBlank @Size(max=80) String eventType,
    @Min(1) @Max(1000) int guestCount,
    LocalDate preferredDate,
    @NotNull @Size(max=100) String preferredTime,
    @NotBlank @Size(max=2000) String message) {}
