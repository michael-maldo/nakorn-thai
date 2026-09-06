package au.com.nakornthai.menu.infrastructure;

import au.com.nakornthai.menu.domain.MenuItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;

/** Maps initialized entity relationships into API records inside the read transaction. */
@Component
public class MenuItemMapper {
    private final String mediaBaseUrl;

    public MenuItemMapper(@Value("${nakorn.media-base-url}") String mediaBaseUrl) {
        this.mediaBaseUrl = mediaBaseUrl.endsWith("/") ? mediaBaseUrl : mediaBaseUrl + "/";
    }

    public MenuItem map(MenuItemJpaEntity item) {
        var variations = item.getVariations().stream().filter(MenuItemVariationJpaEntity::isActive)
                .sorted(Comparator.comparingInt(MenuItemVariationJpaEntity::getDisplayOrder)
                        .thenComparing(v -> v.getId().toString()))
                .map(v -> new MenuItem.Variation(v.getId(), v.getName(), v.getPriceMinor(), v.getCurrency(),
                        item.isAvailable() && v.isAvailable(), v.isDefaultVariation(), variationProfile(v)))
                .toList();
        var image = item.getImages().stream().filter(MenuItemImageJpaEntity::isPrimary)
                .findFirst().map(i -> new MenuItem.Image(mediaBaseUrl + i.getStorageKey(), i.getAltText(), i.getFocusX(), i.getFocusY(), i.getZoom()))
                .orElse(null);
        return new MenuItem(item.getId(), item.getSlug(), item.getName(), item.getDescription(),
                item.isAvailable(), image, variations.isEmpty() ? "ITEM" : "VARIATION_REQUIRED",
                variations.isEmpty() ? itemProfile(item) : null, variations);
    }

    public MenuItem map(MenuCollectionItemJpaEntity membership, boolean collectionAvailable) {
        var item = membership.getMenuItem();
        var base = map(item);
        var category = membership.effectiveCategory();
        var groups = item.getOptionGroups().stream()
                .sorted(Comparator.comparingInt(MenuItemOptionGroupJpaEntity::getDisplayOrder)
                        .thenComparing(a -> a.getOptionGroup().getId()))
                .map(a -> {
                    var g = a.getOptionGroup();
                    return new MenuItem.OptionGroup(g.getId(), g.getCode(), g.getName(), g.getSelectionType(), g.isActive(),
                            a.getMinSelections(), a.getMaxSelections(), a.getDisplayOrder(), g.getOptions().stream()
                            .sorted(Comparator.comparingInt(MenuOptionJpaEntity::getDisplayOrder).thenComparing(MenuOptionJpaEntity::getId))
                            .map(o -> new MenuItem.Option(o.getId(), o.getCode(), o.getName(), o.getPriceDeltaMinor(),
                                    o.getCurrency(), g.isActive() && o.isActive(), o.getDisplayOrder())).toList());
                }).toList();
        boolean configurable = groups.stream().allMatch(g -> g.minSelections() == 0 ||
                (g.active() && (g.selectionType().equals("SINGLE") ?
                        g.minSelections() <= 1 && g.options().stream().anyMatch(MenuItem.Option::available) :
                        g.options().stream().filter(MenuItem.Option::available).count() * 20 >= g.minSelections())));
        boolean available = collectionAvailable && base.available() && category.isActive() && configurable;
        var variations = base.variations().stream().map(v -> new MenuItem.Variation(v.id(), v.name(),
                v.defaultVariation() && membership.getPriceOverrideMinor() != null ? membership.getPriceOverrideMinor() : v.priceMinor(),
                v.currency(), available && v.available(), v.defaultVariation(), v.profile(), v.priceMinor())).toList();
        return new MenuItem(base.id(), base.slug(), base.name(), base.description(), available, base.image(),
                base.profileScope(), base.profile(), variations,
                new MenuItem.Category(category.getId(), category.getSlug(), category.getName(),
                        membership.getCollectionCategory() == null ? category.getDisplayOrder() : membership.getCollectionCategory().getDisplayOrder()),
                membership.getCollectionCategory() == null ? null : membership.getCollectionCategory().getId(),
                membership.getDisplayOrder(), membership.getPriceOverrideMinor(), groups);
    }

    private MenuItem.FoodProfile itemProfile(MenuItemJpaEntity item) {
        var badges = item.getDietaryTags().stream()
                .filter(a -> a.getVerifiedAt() != null && a.getDietaryTag().isActive())
                .sorted(Comparator.comparingInt((MenuItemDietaryTagJpaEntity a) -> a.getDietaryTag().getDisplayOrder())
                        .thenComparing(a -> a.getDietaryTag().getId().toString()))
                .map(a -> new MenuItem.Badge(a.getDietaryTag().getCode(), a.getDietaryTag().getName(),
                        a.getNotes(), a.getVerifiedAt())).toList();
        var allergens = item.getAllergens().stream()
                .sorted(Comparator.comparingInt((MenuItemAllergenJpaEntity a) -> a.getAllergen().getDisplayOrder())
                        .thenComparing(a -> a.getAllergen().getId().toString()))
                .map(a -> new MenuItem.Allergen(a.getAllergen().getCode(), a.getAllergen().getName(),
                        a.getDeclaration(), a.getNotes(), a.getVerifiedAt())).toList();
        return new MenuItem.FoodProfile(item.getAllergenReviewStatus(), item.getAllergenReviewedAt(), badges, allergens);
    }

    private MenuItem.FoodProfile variationProfile(MenuItemVariationJpaEntity variation) {
        var badges = variation.getDietaryTags().stream()
                .filter(a -> a.getVerifiedAt() != null && a.getDietaryTag().isActive())
                .sorted(Comparator.comparingInt((MenuItemVariationDietaryTagJpaEntity a) -> a.getDietaryTag().getDisplayOrder())
                        .thenComparing(a -> a.getDietaryTag().getId().toString()))
                .map(a -> new MenuItem.Badge(a.getDietaryTag().getCode(), a.getDietaryTag().getName(),
                        a.getNotes(), a.getVerifiedAt())).toList();
        var allergens = variation.getAllergens().stream()
                .sorted(Comparator.comparingInt((MenuItemVariationAllergenJpaEntity a) -> a.getAllergen().getDisplayOrder())
                        .thenComparing(a -> a.getAllergen().getId().toString()))
                .map(a -> new MenuItem.Allergen(a.getAllergen().getCode(), a.getAllergen().getName(),
                        a.getDeclaration(), a.getNotes(), a.getVerifiedAt())).toList();
        return new MenuItem.FoodProfile(variation.getAllergenReviewStatus(), variation.getAllergenReviewedAt(), badges, allergens);
    }
}
