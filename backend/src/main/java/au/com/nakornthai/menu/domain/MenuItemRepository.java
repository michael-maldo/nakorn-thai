package au.com.nakornthai.menu.domain;

import java.util.Optional;

public interface MenuItemRepository {
    default java.util.List<MenuItem.CollectionSummary> findPublishedCollections() { return java.util.List.of(); }
    Optional<MenuItem.Collection> findVisibleCollection(String slug);
}
