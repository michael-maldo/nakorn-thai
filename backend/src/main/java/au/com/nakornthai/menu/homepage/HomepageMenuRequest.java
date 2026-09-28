package au.com.nakornthai.menu.homepage;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record HomepageMenuRequest(@NotNull @PositiveOrZero Long version,
                                  @NotNull @Size(max = 20) List<@NotNull UUID> collectionIds) {}
