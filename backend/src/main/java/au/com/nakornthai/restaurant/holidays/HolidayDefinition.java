package au.com.nakornthai.restaurant.holidays;

import au.com.nakornthai.restaurant.infrastructure.RestaurantAuditJpaEntity;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
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
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal surchargePercentage = new BigDecimal("0.00");

    void update(String name, LocalDate startDate, LocalDate endDate, HolidayRecurrence recurrence,
                String notes, boolean active, BigDecimal surchargePercentage) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.recurrence = recurrence;
        this.notes = notes;
        this.active = active;
        this.surchargePercentage = surchargePercentage;
    }
}
