package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.util.*;
import java.time.*;

@Getter @Setter @Entity @BatchSize(size = 64)
@Table(name = "menu_option")
public class MenuOptionJpaEntity extends MenuUuidJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "option_group_id")
    private MenuOptionGroupJpaEntity optionGroup;
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false) private long priceDeltaMinor;
    @Column(nullable = false, length = 3) private String currency = "AUD";
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(nullable = false) private int displayOrder;
}
