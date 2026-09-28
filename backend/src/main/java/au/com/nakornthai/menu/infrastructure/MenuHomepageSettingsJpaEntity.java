package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "menu_homepage_settings")
@Getter
@NoArgsConstructor
public class MenuHomepageSettingsJpaEntity {
    @Id
    private short id = 1;
    @Version
    private Long version;
    @ElementCollection
    @CollectionTable(name = "menu_homepage_collection", joinColumns = @JoinColumn(name = "settings_id"))
    @OrderColumn(name = "display_order")
    @Column(name = "collection_id", nullable = false)
    private List<UUID> collectionIds = new ArrayList<>();
}
