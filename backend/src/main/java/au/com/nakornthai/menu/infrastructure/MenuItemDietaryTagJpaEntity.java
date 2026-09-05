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
@Table(name = "menu_item_dietary_tag")
@BatchSize(size = 64)
public class MenuItemDietaryTagJpaEntity extends MenuAuditJpaEntity {
    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "ownerId", column = @Column(name = "menu_item_id")),
        @AttributeOverride(name = "valueId", column = @Column(name = "dietary_tag_id"))
    })
    private MenuAssociationId id = new MenuAssociationId();

    @MapsId("ownerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItemJpaEntity menuItem;

    @MapsId("valueId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dietary_tag_id", nullable = false)
    private DietaryTagJpaEntity dietaryTag;

    @Column(name = "notes", nullable = true, columnDefinition = "text")
    private String notes;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;
}
