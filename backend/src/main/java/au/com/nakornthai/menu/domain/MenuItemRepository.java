package au.com.nakornthai.menu.domain;

import java.util.Optional;

public interface MenuItemRepository {
    Optional<MenuItem.Collection> findVisibleCollection(String slug);
}
