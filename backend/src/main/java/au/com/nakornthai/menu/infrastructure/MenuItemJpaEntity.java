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
@Table(name = "menu_item")
@BatchSize(size = 64)
public class MenuItemJpaEntity extends MenuUuidJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private MenuCategoryJpaEntity category;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "allergen_review_status", nullable = false, length = 20)
    private String allergenReviewStatus = "NOT_REVIEWED";

    @Column(name = "allergen_reviewed_at", nullable = true)
    private Instant allergenReviewedAt;

    @OneToMany(mappedBy = "menuItem")
    @BatchSize(size = 64)
    private List<MenuItemVariationJpaEntity> variations = new ArrayList<>();

    @OneToMany(mappedBy = "menuItem")
    @BatchSize(size = 64)
    private List<MenuItemImageJpaEntity> images = new ArrayList<>();

    @OneToMany(mappedBy = "menuItem")
    @BatchSize(size = 64)
    private List<MenuItemDietaryTagJpaEntity> dietaryTags = new ArrayList<>();

    @OneToMany(mappedBy = "menuItem")
    @BatchSize(size = 64)
    private List<MenuItemAllergenJpaEntity> allergens = new ArrayList<>();
}
