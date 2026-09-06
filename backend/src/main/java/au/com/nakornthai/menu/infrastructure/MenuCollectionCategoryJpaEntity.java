package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import java.util.*;
import java.time.*;

@Getter @Setter @Entity @BatchSize(size = 64)
@Table(name = "menu_collection_category")
public class MenuCollectionCategoryJpaEntity extends MenuUuidJpaEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "collection_id")
    private MenuCollectionJpaEntity collection;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "category_id")
    private MenuCategoryJpaEntity category;
    @Column(nullable = false) private int displayOrder;
}
