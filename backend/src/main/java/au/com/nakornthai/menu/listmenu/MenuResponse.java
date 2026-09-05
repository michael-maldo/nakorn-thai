package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.MenuItem;
import java.util.List;
import java.util.UUID;

public record MenuResponse(UUID id, String slug, String name, String description, List<MenuItem> items) {
    public static MenuResponse from(MenuItem.Collection collection) {
        return new MenuResponse(collection.id(), collection.slug(), collection.name(),
                collection.description(), collection.items());
    }
}
