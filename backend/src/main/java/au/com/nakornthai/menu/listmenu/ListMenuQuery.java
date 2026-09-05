package au.com.nakornthai.menu.listmenu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ListMenuQuery(
        @NotBlank @Size(max = 180) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*")
        String collectionSlug) {}
