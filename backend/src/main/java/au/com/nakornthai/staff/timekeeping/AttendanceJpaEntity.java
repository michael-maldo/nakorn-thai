package au.com.nakornthai.staff.timekeeping;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "staff_attendance") @Getter @Setter
public class AttendanceJpaEntity {
    @Id private UUID id;
    @Column(nullable = false) private UUID staffUserId;
    @Column(nullable = false) private Instant clockIn;
    private Instant clockOut;
    @Column(nullable = false) private UUID createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private Long version;
}
