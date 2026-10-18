package au.com.nakornthai.staff.timekeeping;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
import static au.com.nakornthai.staff.timekeeping.TimekeepingDtos.*;

@Entity @Table(name = "staff_attendance_audit") @Getter
public class AttendanceAuditJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID attendanceId;
    @Column(nullable = false, length = 20) private String action;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false) private Instant occurredAt;
    @JdbcTypeCode(SqlTypes.JSON) private Snapshot previousValues;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false) private Snapshot newValues;
    @Column(length = 1000) private String reason;

    protected AttendanceAuditJpaEntity() {}
    AttendanceAuditJpaEntity(AttendanceJpaEntity entry, String action, UUID actor, Instant now, Values before, String reason) {
        this.id = UUID.randomUUID(); this.attendanceId = entry.getId(); this.action = action;
        this.actorId = actor; this.occurredAt = now; this.previousValues = before == null ? null : Snapshot.of(before);
        this.newValues = Snapshot.of(new Values(entry.getClockIn(), entry.getClockOut(), entry.getVersion())); this.reason = reason;
    }
    Audit view() { return new Audit(id, attendanceId, action, actorId, occurredAt,
            previousValues == null ? null : previousValues.view(), newValues.view(), reason); }

    // Keep JSON timestamps as ISO strings: Hibernate's JSON mapper is separate from MVC's configured mapper.
    public record Snapshot(String clockIn, String clockOut, long version) {
        static Snapshot of(Values values) { return new Snapshot(values.clockIn().toString(),
                values.clockOut() == null ? null : values.clockOut().toString(), values.version()); }
        Values view() { return new Values(Instant.parse(clockIn), clockOut == null ? null : Instant.parse(clockOut), version); }
    }
}
