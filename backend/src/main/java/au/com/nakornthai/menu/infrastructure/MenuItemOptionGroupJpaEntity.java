package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.util.*;
import java.time.*;

@Getter @Setter @Entity @BatchSize(size = 64)
@Table(name = "menu_item_option_group")
public class MenuItemOptionGroupJpaEntity extends MenuAuditJpaEntity {
    @EmbeddedId @AttributeOverrides({
        @AttributeOverride(name = "ownerId", column = @Column(name = "menu_item_id")),
        @AttributeOverride(name = "valueId", column = @Column(name = "option_group_id"))})
    private MenuAssociationId id = new MenuAssociationId();
    @MapsId("ownerId") @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "menu_item_id")
    private MenuItemJpaEntity menuItem;
    @MapsId("valueId") @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "option_group_id")
    private MenuOptionGroupJpaEntity optionGroup;
    @Column(nullable = false) private int minSelections;
    @Column(nullable = false) private int maxSelections = 1;
    @Column(nullable = false) private int displayOrder;
}
