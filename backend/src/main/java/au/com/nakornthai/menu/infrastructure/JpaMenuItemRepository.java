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
    private final au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService restaurant;
    private final java.time.Clock clock;

    @Override @Transactional
    public List<MenuItem.CollectionSummary> findPublishedCollections() {
        var now = clock.instant();
        var published = collections.findByStatusOrderByDisplayOrderAscIdAsc("PUBLISHED");
        var schedule = published.stream().anyMatch(c -> c.getDailyCutoffTime() != null) ? restaurant.schedule() : null;
        return published.stream().map(c ->
                new MenuItem.CollectionSummary(c.getId(), c.getSlug(), c.getName(), c.getDescription(), c.getTimezone(),
                        c.getDisplayOrder(), MenuCatalogRules.availability(c, now, schedule))).toList();
    }

    @Override @Transactional
    public Optional<MenuItem.Collection> findVisibleCollection(String slug) {
        var now = clock.instant();
        return collections.findVisibleBySlug(slug).map(c -> {
            var availability = MenuCatalogRules.availability(c, now, c.getDailyCutoffTime() == null ? null : restaurant.schedule());
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
