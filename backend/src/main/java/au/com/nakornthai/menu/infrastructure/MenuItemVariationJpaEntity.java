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
@Table(name = "menu_item_variation")
@BatchSize(size = 64)
public class MenuItemVariationJpaEntity extends MenuUuidJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItemJpaEntity menuItem;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sku", nullable = true, length = 80)
    private String sku;

    @Column(name = "price_minor", nullable = false)
    private Long priceMinor;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AUD";

    @Column(name = "is_default", nullable = false)
    private boolean defaultVariation = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "allergen_review_status", nullable = false, length = 20)
    private String allergenReviewStatus = "NOT_REVIEWED";

    @Column(name = "allergen_reviewed_at", nullable = true)
    private Instant allergenReviewedAt;

    @OneToMany(mappedBy = "variation")
    @BatchSize(size = 64)
    private List<MenuItemVariationDietaryTagJpaEntity> dietaryTags = new ArrayList<>();

    @OneToMany(mappedBy = "variation")
    @BatchSize(size = 64)
    private List<MenuItemVariationAllergenJpaEntity> allergens = new ArrayList<>();
}
