package au.com.nakornthai.menu.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable public read model; variation food profiles never inherit item claims. */
public record MenuItem(UUID id, String slug, String name, String description,
                       boolean available, Image image, String profileScope,
                       FoodProfile profile, List<Variation> variations) {
    public record Collection(UUID id, String slug, String name, String description, List<MenuItem> items) {}
    public record Image(String url, String alt) {}
    public record FoodProfile(String allergenReviewStatus, Instant allergenReviewedAt,
                              List<Badge> dietaryTags, List<Allergen> allergens) {}
    public record Badge(String code, String name, String notes, Instant verifiedAt) {}
    public record Allergen(String code, String name, String declaration, String notes, Instant verifiedAt) {}
    public record Variation(UUID id, String name, long priceMinor, String currency,
                            boolean available, boolean defaultVariation, FoodProfile profile) {}
}
