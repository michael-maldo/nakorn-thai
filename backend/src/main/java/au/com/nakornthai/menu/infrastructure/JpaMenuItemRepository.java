package au.com.nakornthai.menu.infrastructure;

import au.com.nakornthai.menu.domain.MenuItem;
import au.com.nakornthai.menu.domain.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository @RequiredArgsConstructor
public class JpaMenuItemRepository implements MenuItemRepository {
    private final SpringDataMenuCollectionRepository collections;
    private final SpringDataMenuCollectionItemRepository memberships;
    private final MenuItemMapper mapper;

    @Override @Transactional(readOnly = true)
    public List<MenuItem.CollectionSummary> findPublishedCollections() {
        var now = Instant.now();
        return collections.findByStatusOrderByDisplayOrderAscIdAsc("PUBLISHED").stream().map(c ->
                new MenuItem.CollectionSummary(c.getId(), c.getSlug(), c.getName(), c.getDescription(), c.getTimezone(),
                        c.getDisplayOrder(), MenuCatalogRules.availability(c, now))).toList();
    }

    @Override @Transactional(readOnly = true)
    public Optional<MenuItem.Collection> findVisibleCollection(String slug) {
        var now = Instant.now();
        return collections.findVisibleBySlug(slug).map(c -> {
            var availability = MenuCatalogRules.availability(c, now);
            var dishes = memberships.findPublishedMemberships(c.getId()).stream()
                    .filter(m -> m.effectiveCategory().isActive())
                    .filter(m -> m.getCollectionCategory() == null || m.getCollectionCategory().getCollection().getId().equals(c.getId()))
                    .map(m -> mapper.map(m, availability.available()))
                    .sorted(Comparator.comparingInt((MenuItem i) -> i.category().displayOrder())
                            .thenComparing(i -> i.category().id()).thenComparingInt(MenuItem::displayOrder).thenComparing(MenuItem::id)).toList();
            var categories = new LinkedHashMap<UUID, MenuItem.Category>();
            c.getCategories().stream().filter(cc -> cc.getCategory().isActive()).forEach(cc -> {
                var category = cc.getCategory();
                categories.put(category.getId(), new MenuItem.Category(category.getId(), category.getSlug(), category.getName(), cc.getDisplayOrder()));
            });
            dishes.forEach(i -> categories.putIfAbsent(i.category().id(), i.category()));
            return new MenuItem.Collection(c.getId(), c.getSlug(), c.getName(), c.getDescription(), dishes, c.getTimezone(), availability,
                    categories.values().stream().sorted(Comparator.comparingInt(MenuItem.Category::displayOrder).thenComparing(MenuItem.Category::id)).toList());
        });
    }
}
