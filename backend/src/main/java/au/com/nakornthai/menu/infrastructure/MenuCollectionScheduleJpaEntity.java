package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.util.*;
import java.time.*;

@Getter @Setter @Entity @BatchSize(size = 64)
@Table(name = "menu_collection_schedule")
public class MenuCollectionScheduleJpaEntity extends MenuUuidJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "collection_id")
    private MenuCollectionJpaEntity collection;
    @Column(nullable = false, length = 20) private String ruleType;
    private Short dayOfWeek;
    private LocalDate specificDate;
    private LocalTime startTime;
    private LocalTime endTime;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(nullable = false) private int displayOrder;
}
