package au.com.nakornthai.restaurant.holidays;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HolidayDefinitionResponse(UUID id, String name, LocalDate startDate, LocalDate endDate,
                                        HolidayRecurrence recurrence, String notes, boolean active,
                                        Instant createdAt, Instant updatedAt, long version, BigDecimal surchargePercentage) {
    static HolidayDefinitionResponse from(HolidayDefinition row) {
        return new HolidayDefinitionResponse(row.getId(), row.getName(), row.getStartDate(), row.getEndDate(),
                row.getRecurrence(), row.getNotes(), row.isActive(), row.getCreatedAt(), row.getUpdatedAt(), row.getVersion(), row.getSurchargePercentage());
    }
}
