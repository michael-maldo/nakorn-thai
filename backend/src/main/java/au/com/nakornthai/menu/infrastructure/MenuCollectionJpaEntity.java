package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "menu_collection")
@BatchSize(size = 64)
public class MenuCollectionJpaEntity extends MenuUuidJpaEntity {
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "starts_at", nullable = true)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = true)
    private Instant endsAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(nullable = false, length = 64) private String timezone = "Australia/Melbourne";
    @OneToMany(mappedBy = "collection") @BatchSize(size = 64)
    private List<MenuCollectionScheduleJpaEntity> schedules = new ArrayList<>();
    @OneToMany(mappedBy = "collection") @BatchSize(size = 64)
    private List<MenuCollectionCategoryJpaEntity> categories = new ArrayList<>();
}
