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
@Table(name = "menu_collection_item")
@BatchSize(size = 64)
public class MenuCollectionItemJpaEntity extends MenuAuditJpaEntity {
    @EmbeddedId
    @AttributeOverrides({
        @AttributeOverride(name = "ownerId", column = @Column(name = "collection_id")),
        @AttributeOverride(name = "valueId", column = @Column(name = "menu_item_id"))
    })
    private MenuAssociationId id = new MenuAssociationId();

    @MapsId("ownerId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    private MenuCollectionJpaEntity collection;

    @MapsId("valueId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItemJpaEntity menuItem;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
