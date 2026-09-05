package au.com.nakornthai.menu.infrastructure;

import au.com.nakornthai.menu.domain.MenuItem;
import au.com.nakornthai.menu.domain.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaMenuItemRepository implements MenuItemRepository {
    private final SpringDataMenuCollectionRepository collections;
    private final SpringDataMenuItemRepository items;
    private final MenuItemMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<MenuItem.Collection> findVisibleCollection(String slug) {
        return collections.findVisibleBySlug(slug).map(collection -> new MenuItem.Collection(
                collection.getId(), collection.getSlug(), collection.getName(), collection.getDescription(),
                items.findPublishedByCollectionId(collection.getId()).stream().map(mapper::map).toList()));
    }
}
