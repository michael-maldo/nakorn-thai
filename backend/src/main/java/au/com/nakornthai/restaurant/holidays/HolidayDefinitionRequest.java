package au.com.nakornthai.restaurant.holidays;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record HolidayDefinitionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @NotNull HolidayRecurrence recurrence,
        @Size(max = 500) String notes,
        Boolean active,
        @PositiveOrZero Long version) {
    public HolidayDefinitionRequest {
        if (name != null) name = name.strip();
        if (notes != null) notes = notes.strip();
        if (endDate == null) endDate = startDate;
    }
}
