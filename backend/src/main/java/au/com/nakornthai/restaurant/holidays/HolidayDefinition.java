package au.com.nakornthai.restaurant.holidays;

import au.com.nakornthai.restaurant.infrastructure.RestaurantAuditJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "restaurant_holiday_definition")
@Getter
public class HolidayDefinition extends RestaurantAuditJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false)
    private LocalDate startDate;
    @Column(nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private HolidayRecurrence recurrence;
    @Column(length = 500)
    private String notes;
    @Column(nullable = false)
    private boolean active;

    void update(String name, LocalDate startDate, LocalDate endDate, HolidayRecurrence recurrence,
                String notes, boolean active) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;
        this.notes = notes;
        this.active = active;
    }
}
