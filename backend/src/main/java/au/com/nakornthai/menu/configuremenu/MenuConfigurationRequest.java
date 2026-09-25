package au.com.nakornthai.menu.configuremenu;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;
import java.time.*;
import java.util.UUID;

public final class MenuConfigurationRequest {
    private MenuConfigurationRequest() {}
    public record Collection(@NotBlank @Size(max=150) String name,
            @NotBlank @Size(max=180) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String slug,
            @Size(max=10000) String description, @NotNull @Pattern(regexp="DRAFT|PUBLISHED|ARCHIVED") String status,
            boolean active, @NotBlank @Size(max=64) String timezone, Instant startsAt, Instant endsAt,
            @Min(0) int displayOrder, @PositiveOrZero Long version, LocalTime dailyCutoffTime) {}
    public record Schedule(@NotNull @Pattern(regexp="WEEKLY|SPECIFIC_DATE") String ruleType,
            @Min(1) @Max(7) Short dayOfWeek, LocalDate specificDate, LocalTime startTime, LocalTime endTime,
            boolean active, @Min(0) int displayOrder, @PositiveOrZero Long version) {}
    public record Category(@NotNull UUID categoryId, @Min(0) int displayOrder, @PositiveOrZero Long version) {}
    public record Membership(UUID collectionCategoryId, @PositiveOrZero Long priceOverrideMinor,
            @Min(0) int displayOrder, @PositiveOrZero Long version) {}
    public record Group(@NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String code,
            @NotBlank @Size(max=100) String name, @NotNull @Pattern(regexp="SINGLE|MULTIPLE") String selectionType,
            boolean active, @PositiveOrZero Long version) {}
    public record Option(@NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String code,
            @NotBlank @Size(max=100) String name,
            boolean active, @Min(0) int displayOrder, @PositiveOrZero Long version) {}
    public record Assignment(@Min(0) @Max(100) int minSelections, @Min(1) @Max(100) int maxSelections,
            @Min(0) int displayOrder, @PositiveOrZero Long version,
            @NotNull @PositiveOrZero Long groupVersion,
            @NotNull @Size(max=100) List<@NotNull @Valid OptionPrice> prices) {}
    public record OptionPrice(@NotNull UUID optionId, @Min(0) @Max(999999999) long priceDeltaMinor) {}
    public record NewChoice(@NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String code,
            @NotBlank @Size(max=100) String name, @Min(0) @Max(999999999) long priceDeltaMinor) {}
    public record CreateAssignedGroup(@NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String code,
            @NotBlank @Size(max=100) String name, @NotNull @Pattern(regexp="SINGLE|MULTIPLE") String selectionType,
            @Min(1) @Max(100) int maxSelections, @Min(0) int displayOrder,
            @NotEmpty @Size(max=100) List<@NotNull @Valid NewChoice> options) {}
}
