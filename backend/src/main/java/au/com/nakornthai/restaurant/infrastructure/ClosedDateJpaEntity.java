package au.com.nakornthai.restaurant.infrastructure;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "restaurant_closed_date") @Getter @Setter @NoArgsConstructor
public class ClosedDateJpaEntity extends RestaurantAuditJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private LocalDate closedDate;
    @Column(length = 500) private String reason;
}
