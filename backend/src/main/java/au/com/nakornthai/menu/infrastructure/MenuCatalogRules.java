package au.com.nakornthai.menu.infrastructure;

import au.com.nakornthai.menu.domain.*;
import java.time.Instant;
import java.util.List;

public final class MenuCatalogRules {
    private MenuCatalogRules() {}
    public static CollectionAvailability.Result availability(MenuCollectionJpaEntity c, Instant now) {
        return availability(c, now, null);
    }
    public static CollectionAvailability.Result availability(MenuCollectionJpaEntity c, Instant now,
            au.com.nakornthai.restaurant.domain.RestaurantSchedule restaurant) {
        var result = CollectionAvailability.evaluate(c.getStatus(), c.isActive(), c.getStartsAt(), c.getEndsAt(), c.getTimezone(),
                c.getSchedules().stream().map(s -> new CollectionAvailability.Rule(s.getRuleType(), s.getDayOfWeek(),
                        s.getSpecificDate(), s.getStartTime(), s.getEndTime(), s.isActive())).toList(), now);
        return CollectionAvailability.withRestaurantCutoff(result, c.getDailyCutoffTime(), restaurant);
    }
    public static List<MenuPricing.Group> groups(MenuItemJpaEntity item) {
        return item.getOptionGroups().stream().map(a -> {
            var g = a.getOptionGroup();
            return new MenuPricing.Group(g.getId(), g.getName(), g.getSelectionType(), g.isActive(), a.getMinSelections(),
                    a.getMaxSelections(), g.getOptions().stream().map(o ->
                    new MenuPricing.Option(o.getId(), o.getName(), a.getOptionPrices().getOrDefault(o.getId(), 0L), o.isActive() && a.getOptionPrices().containsKey(o.getId()))).toList());
        }).toList();
    }
}
