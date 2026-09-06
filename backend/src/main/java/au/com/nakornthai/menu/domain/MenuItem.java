package au.com.nakornthai.menu.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable public read model; variation food profiles never inherit item claims. */
public record MenuItem(UUID id, String slug, String name, String description,
                       boolean available, Image image, String profileScope,
                       FoodProfile profile, List<Variation> variations, Category category,
                       UUID collectionCategoryId, int displayOrder, Long priceOverrideMinor, List<OptionGroup> optionGroups) {
    public MenuItem(UUID id, String slug, String name, String description, boolean available, Image image,
                    String scope, FoodProfile profile, List<Variation> variations) {
        this(id, slug, name, description, available, image, scope, profile, variations, null, null, 0, null, List.of());
    }
    public record Category(UUID id, String slug, String name, int displayOrder) {}
    public record Option(UUID id, String code, String name, long priceDeltaMinor, String currency, boolean available, int displayOrder) {}
    public record OptionGroup(UUID id, String code, String name, String selectionType, boolean active,
                              int minSelections, int maxSelections, int displayOrder, List<Option> options) {}
    public record CollectionSummary(UUID id, String slug, String name, String description, String timezone,
                                    int displayOrder, CollectionAvailability.Result availability) {}
    public record Collection(UUID id, String slug, String name, String description, List<MenuItem> items,
                             String timezone, CollectionAvailability.Result availability, List<Category> categories) {
        public Collection(UUID id, String slug, String name, String description, List<MenuItem> items) {
            this(id, slug, name, description, items, "Australia/Melbourne", null, List.of());
        }
    }
    public record Image(String url, String alt, int focusX, int focusY, double zoom) {
        public Image(String url, String alt) { this(url, alt, 50, 50, 1); }
    }
    public record FoodProfile(String allergenReviewStatus, Instant allergenReviewedAt,
                              List<Badge> dietaryTags, List<Allergen> allergens) {}
    public record Badge(String code, String name, String notes, Instant verifiedAt) {}
    public record Allergen(String code, String name, String declaration, String notes, Instant verifiedAt) {}
    public record Variation(UUID id, String name, long priceMinor, String currency,
                            boolean available, boolean defaultVariation, FoodProfile profile, long variationBasePriceMinor) {
        public Variation(UUID id, String name, long priceMinor, String currency, boolean available, boolean defaultVariation, FoodProfile profile) {
            this(id, name, priceMinor, currency, available, defaultVariation, profile, priceMinor);
        }
    }
}
