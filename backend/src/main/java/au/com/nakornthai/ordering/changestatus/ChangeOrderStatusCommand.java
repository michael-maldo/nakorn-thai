package au.com.nakornthai.ordering.changestatus;
import jakarta.validation.constraints.*;
public record ChangeOrderStatusCommand(@Min(0) long version,
        @NotNull @Pattern(regexp="ACCEPTED|PREPARING|READY|COMPLETED|CANCELLED") String status,
        @Min(5) @Max(180) Integer pickupMinutes, boolean paymentCollected,
        @Size(max=500) String reason) {}
