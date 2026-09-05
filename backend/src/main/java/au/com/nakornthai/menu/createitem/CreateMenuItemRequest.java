package au.com.nakornthai.menu.createitem;

import jakarta.validation.constraints.*;
import java.util.Set;
import java.util.UUID;

public record CreateMenuItemRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 180) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max = 10000) String description,
        @NotNull UUID categoryId,
        @NotNull @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status,
        boolean available,
        @Min(0) int displayOrder,
        @NotNull @Size(max = 100) Set<@NotNull UUID> collectionIds,
        @PositiveOrZero Long version) {}
