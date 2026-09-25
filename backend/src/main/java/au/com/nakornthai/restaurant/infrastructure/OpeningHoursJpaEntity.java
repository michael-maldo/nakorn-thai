package au.com.nakornthai.restaurant.infrastructure;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity @Table(name = "restaurant_opening_hours") @Getter @Setter @NoArgsConstructor
public class OpeningHoursJpaEntity extends RestaurantAuditJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private short dayOfWeek;
    @Column(nullable = false) @JdbcTypeCode(SqlTypes.LOCAL_TIME) private LocalTime opensAt;
    @Column(nullable = false) @JdbcTypeCode(SqlTypes.LOCAL_TIME) private LocalTime closesAt;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(nullable = false) private int displayOrder;
}
