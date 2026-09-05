package au.com.nakornthai.menu.getitem;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MenuItemResponse(UUID id, String name, String slug, String description,
        UUID categoryId, String status, boolean available, int displayOrder,
        Set<UUID> collectionIds, Long version, au.com.nakornthai.menu.domain.MenuItem.Image image, List<Price> prices) {
    public record Price(UUID id, String name, java.math.BigDecimal amount) {}
    public record Option(UUID id, String name) {}
    public record Dashboard(List<MenuItemResponse> items, List<Option> categories, List<Option> collections) {}
}
