package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.MenuItem;
import au.com.nakornthai.menu.domain.MenuItemRepository;
import au.com.nakornthai.shared.observability.CorrelationIdFilter;
import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises MVC serialization, real validation/security, and a mocked persistence boundary. */
@WebMvcTest(ListMenuController.class)
@Import({ListMenuHandler.class, SecurityConfig.class, CorrelationIdFilter.class})
class ListMenuApiTest {
    private static final String URL = "/api/menu/collections/{slug}/items";
    private static final String SLUG = "signature-dishes";
    private static final UUID COLLECTION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository jwtSessions;

    @Autowired MockMvc mvc;
    @MockitoBean MenuItemRepository repository;

    private void collection(List<MenuItem> items) {
        when(repository.findVisibleCollection(SLUG)).thenReturn(Optional.of(
                new MenuItem.Collection(COLLECTION_ID, SLUG, "Signature Dishes", "Chef selections", items)));
    }

    @Test
    void anonymousGetReturnsCollectionAndItemProfile() throws Exception {
        var reviewed = Instant.parse("2026-09-01T00:00:00Z");
        var profile = new MenuItem.FoodProfile("REVIEWED", reviewed,
                List.of(new MenuItem.Badge("TEST_BADGE", "Test badge", "Fixture only", reviewed)),
                List.of(new MenuItem.Allergen("TEST_ALLERGEN", "Test allergen", "MAY_CONTAIN", null, reviewed)));
        var id = UUID.randomUUID();
        collection(List.of(new MenuItem(id, "test-rice", "Test Rice", "Description", false,
                new MenuItem.Image("/media/test.jpg", "Test image"), "ITEM", profile, List.of())));

        mvc.perform(get(URL, SLUG)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Request-ID", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.id").value(COLLECTION_ID.toString()))
                .andExpect(jsonPath("$.name").value("Signature Dishes"))
                .andExpect(jsonPath("$.slug").value(SLUG))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].available").value(false))
                .andExpect(jsonPath("$.items[0].image.url").value("/media/test.jpg"))
                .andExpect(jsonPath("$.items[0].profileScope").value("ITEM"))
                .andExpect(jsonPath("$.items[0].profile.allergenReviewedAt").value(reviewed.toString()))
                .andExpect(jsonPath("$.items[0].profile.dietaryTags[0].code").value("TEST_BADGE"))
                .andExpect(jsonPath("$.items[0].profile.allergens[0].declaration").value("MAY_CONTAIN"))
                .andExpect(jsonPath("$.items[0].variations").isEmpty())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        verify(repository).findVisibleCollection(SLUG);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void variationProfileRemainsScopedAndUnknownValuesStayExplicit() throws Exception {
        var profile = new MenuItem.FoodProfile("NOT_REVIEWED", null, List.of(), List.of());
        var variation = new MenuItem.Variation(UUID.randomUUID(), "Pork", 2450, "AUD", true, true, profile);
        collection(List.of(new MenuItem(UUID.randomUUID(), "fried-rice", "Fried Rice", "Description", true,
                null, "VARIATION_REQUIRED", null, List.of(variation))));
        mvc.perform(get(URL, SLUG)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].image").value(nullValue()))
                .andExpect(jsonPath("$.items[0].profile").value(nullValue()))
                .andExpect(jsonPath("$.items[0].profileScope").value("VARIATION_REQUIRED"))
                .andExpect(jsonPath("$.items[0].variations[0].name").value("Pork"))
                .andExpect(jsonPath("$.items[0].variations[0].priceMinor").value(2450))
                .andExpect(jsonPath("$.items[0].variations[0].currency").value("AUD"))
                .andExpect(jsonPath("$.items[0].variations[0].defaultVariation").value(true))
                .andExpect(jsonPath("$.items[0].variations[0].profile.allergenReviewStatus").value("NOT_REVIEWED"))
                .andExpect(jsonPath("$.items[0].variations[0].profile.allergens").isEmpty());
    }

    @Test
    void emptyPublishedCollectionReturnsAnArray() throws Exception {
        collection(List.of());
        mvc.perform(get(URL, SLUG)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void missingOrNonpublicCollectionReturns404() throws Exception {
        when(repository.findVisibleCollection("missing")).thenReturn(Optional.empty());
        mvc.perform(get(URL, "missing")).andExpect(status().isNotFound());
        verify(repository).findVisibleCollection("missing");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPPERCASE", "bad_slug", "-leading", "trailing-"})
    void invalidSlugReturns404WithoutDatabaseAccess(String slug) throws Exception {
        mvc.perform(get(URL, slug)).andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }

    @Test
    void overlyLongSlugReturns404WithoutDatabaseAccess() throws Exception {
        mvc.perform(get(URL, "a".repeat(181))).andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }

    @Test
    void writeDeniedEvenWithAuthenticationAndCsrfToken() throws Exception {
        mvc.perform(post(URL, SLUG).with(user("test-staff")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void writeWithoutCsrfIsDenied() throws Exception {
        mvc.perform(post(URL, SLUG)).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void unregisteredRouteRequiresAuthorization() throws Exception {
        mvc.perform(get("/api/staff/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/private").with(user("test-staff"))).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    void requestIdsAreGeneratedPerRequestRatherThanTrustingClientValue() throws Exception {
        collection(List.of());
        var first = mvc.perform(get(URL, SLUG).header("X-Request-ID", "client-supplied"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("X-Request-ID");
        mvc.perform(get(URL, SLUG)).andExpect(status().isOk())
                .andExpect(header().string("X-Request-ID", not(first)))
                .andExpect(header().string("X-Request-ID", not("client-supplied")));
        org.junit.jupiter.api.Assertions.assertNotEquals("client-supplied", first);
        org.junit.jupiter.api.Assertions.assertNull(org.slf4j.MDC.get("requestId"));
    }
}
