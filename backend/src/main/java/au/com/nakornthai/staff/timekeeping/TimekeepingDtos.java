package au.com.nakornthai.staff.timekeeping;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class TimekeepingDtos {
    private TimekeepingDtos() {}
    public record StaffRequest(@NotNull UUID staffUserId) {}
    public record ManualRequest(@NotNull UUID staffUserId, @NotNull Instant clockIn,
            @NotNull Instant clockOut, @NotBlank @Size(max = 1000) String reason) {}
    public record CorrectionRequest(@NotNull @PositiveOrZero Long version, @NotNull Instant clockIn,
            Instant clockOut, @NotBlank @Size(max = 1000) String reason) {}
    public record Entry(UUID id, UUID staffUserId, Instant clockIn, Instant clockOut, boolean open,
            BigDecimal workedSeconds, UUID createdBy, Instant createdAt, Instant updatedAt, Long version) {
        public static Entry of(AttendanceJpaEntity e) {
            return new Entry(e.getId(), e.getStaffUserId(), e.getClockIn(), e.getClockOut(), e.getClockOut() == null,
                    e.getClockOut() == null ? null : seconds(e.getClockIn(), e.getClockOut()),
                    e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
        }
    }
    public static BigDecimal seconds(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        return BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9));
    }
    public record Timesheet(List<Entry> entries, int page, int size, long totalElements,
            BigDecimal completedSeconds, long openShiftCount) {}
    public record Values(Instant clockIn, Instant clockOut, long version) {}
    public record Audit(UUID id, UUID attendanceId, String action, UUID actorId, Instant occurredAt,
            Values previousValues, Values newValues, String reason) {}
    public record AuditPage(List<Audit> entries, int page, int size, long totalElements) {}
}
