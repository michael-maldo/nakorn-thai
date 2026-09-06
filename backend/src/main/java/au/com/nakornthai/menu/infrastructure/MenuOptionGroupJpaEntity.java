package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.util.*;
import java.time.*;

@Getter @Setter @Entity @BatchSize(size = 64)
@Table(name = "menu_option_group")
public class MenuOptionGroupJpaEntity extends MenuUuidJpaEntity {
    @Column(nullable = false, length = 100) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 20) private String selectionType = "SINGLE";
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @OneToMany(mappedBy = "optionGroup") @BatchSize(size = 64)
    private List<MenuOptionJpaEntity> options = new ArrayList<>();
}
