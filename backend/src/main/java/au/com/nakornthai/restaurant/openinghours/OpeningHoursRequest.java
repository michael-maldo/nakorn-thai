package au.com.nakornthai.restaurant.openinghours;

import jakarta.validation.constraints.*;
import java.time.*;

public final class OpeningHoursRequest {
    private OpeningHoursRequest() {}
    public record Settings(@NotBlank @Size(max = 64) String timezone, @NotNull @PositiveOrZero Long version) {}
    public record Window(@NotNull @Min(1) @Max(7) Short dayOfWeek,
                         @NotNull LocalTime opensAt, @NotNull LocalTime closesAt,
                         @NotNull Boolean active, @PositiveOrZero int displayOrder, @PositiveOrZero Long version) {}
    public record Closure(@NotNull LocalDate closedDate, @Size(max = 500) String reason, @PositiveOrZero Long version) {}
}
