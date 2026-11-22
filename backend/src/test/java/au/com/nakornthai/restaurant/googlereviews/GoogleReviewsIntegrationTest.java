package au.com.nakornthai.restaurant.googlereviews;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"GOOGLE_REVIEWS_PUBLIC_USE_APPROVED=true", "google.reviews.sync-delay-ms=3600000"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class GoogleReviewsIntegrationTest {
    private static final String LOCATION = "accounts/11/locations/22";
    private static final String ADMIN_ROOT = "/api/staff/restaurant/google-reviews";
    private static final String PUBLIC = "/api/public/google-reviews";
    private static final Instant JAN = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FEB = Instant.parse("2026-02-01T00:00:00Z");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
        registry.add("NAKORN_CREDENTIAL_MASTER_KEY", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoogleReviewsService service;
    @Autowired au.com.nakornthai.shared.security.JwtService jwt;
    @Autowired au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    @MockitoBean GoogleBusinessClient google;
    UUID owner, other;

    @BeforeEach void fixtures() {
        jdbc.update("delete from google_review_oauth");
        jdbc.update("delete from google_review_selection");
        jdbc.update("""
                update google_review_connection set refresh_credential=null, location_resource=null, location_title=null, location_address=null, maps_url=null, metadata_loaded_at=null,
                  authorization_expired=false, generation=?, last_attempt=null,last_success=null,sync_error=null,total_reviews=null,overall_rating=null,
                  homepage_enabled=false,homepage_limit=6,homepage_order='NEWEST_SELECTED' where restaurant_id=1
                """, UUID.randomUUID());
        owner = staffSession();
        other = staffSession();
        when(google.configuration()).thenReturn(GoogleBusinessClient.OAuthConfiguration.READY);
        when(google.authorize(anyString(), anyString())).thenAnswer(call ->
                URI.create("https://accounts.google.com/o/oauth2/v2/auth?state=" + call.getArgument(0)));
        when(google.exchange(eq("test-code"), matches("[A-Za-z0-9_-]{43}")))
                .thenReturn(new GoogleBusinessClient.Token("access", "private-refresh"));
        when(google.refresh("private-refresh")).thenReturn(new GoogleBusinessClient.Token("access", null));
        when(google.locations("access")).thenReturn(List.of(new GoogleBusinessClient.Location(LOCATION, "Test restaurant", "Melbourne", null)));
        when(google.reviews("access", LOCATION)).thenReturn(new GoogleBusinessClient.Snapshot(List.of(
                new GoogleBusinessClient.Review("one", "Anna", 5, "Original review", JAN, JAN, "Private reply"),
                new GoogleBusinessClient.Review("two", "Bill", 2, "Second review", FEB, FEB, "")), 2, 3.5));
        when(google.revoke("private-refresh")).thenReturn(true);
    }

    @AfterEach void removeTestStaff() {
        List<UUID> users = jdbc.queryForList("select user_id from staff_session where id in (?,?)", UUID.class, owner, other);
        jdbc.update("delete from staff_session where id in (?,?)", owner, other);
        users.forEach(id -> jdbc.update("delete from staff_user where id=?", id));
    }

    @Test void validatesStateAndBindsCallbackToTheInitiatingSession() throws Exception {
        String bearer = "Bearer " + jwt.issue(sessions.findById(owner).orElseThrow().getUser(), owner);
        String authorize = mvc.perform(post("/api/staff/restaurant/integrations/google/connect")
                        .header("Authorization", bearer).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String state = URI.create(com.jayway.jsonpath.JsonPath.read(authorize, "$.authorizationUrl"))
                .getQuery().substring("state=".length());
        assertFalse(service.callback("bad-state", "test-code"));
        mvc.perform(get("/api/integrations/google/callback").param("state", state).param("code", "test-code"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Return to StaffPlatform")));
        assertFalse(service.callback(state, "test-code"));
        assertEquals("DISCONNECTED", service.finish(other).state());
        assertEquals("AUTHORIZATION_RECEIVED", service.status(owner).state());
        mvc.perform(post("/api/staff/restaurant/integrations/google/complete")
                        .header("Authorization", bearer).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("LOCATION_REQUIRED"));
        String cipher = jdbc.queryForObject("select refresh_credential from google_review_connection where restaurant_id=1", String.class);
        assertTrue(cipher.startsWith("v1:"));
        assertFalse(cipher.contains("private-refresh"));
        String revoked = start(other);
        jdbc.update("update staff_session set revoked_at=now() where id=?", other);
        assertFalse(service.callback(revoked, "test-code"));
        String expired = start(owner);
        jdbc.update("update google_review_oauth set expires_at=now() where staff_session_id=?", owner);
        assertFalse(service.callback(expired, "test-code"));
        verify(google, times(1)).exchange(anyString(), anyString());
    }

    @Test void adminStatusReportsReadinessWithoutCredentialValues() throws Exception {
        String route = "/api/staff/restaurant/integrations/google/status";
        String bearer = "Bearer " + jwt.issue(sessions.findById(owner).orElseThrow().getUser(), owner);
        mvc.perform(get(route).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configurationStatus").value("READY"));
        when(google.configuration()).thenReturn(GoogleBusinessClient.OAuthConfiguration.MISSING_OAUTH_SETTINGS);
        mvc.perform(get(route).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.configurationStatus").value("MISSING_OAUTH_SETTINGS"))
                .andExpect(jsonPath("$.clientId").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.redirectUri").doesNotExist());
    }

    @Test void restrictsStaffAccessAndRequiresCsrfWhileFeedIsPublic() throws Exception {
        mvc.perform(get(ADMIN_ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN_ROOT).with(user("foh").roles("FOH"))).andExpect(status().isForbidden());
        mvc.perform(post(ADMIN_ROOT + "/sync").with(user("owner").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.curated").value(true)).andExpect(jsonPath("$.reviews").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/integrations/google/callback").param("state", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test void persistsSelectionsAndReflectsEditsDeletesFailuresAndRevocation() throws Exception {
        assertTrue(service.callback(start(owner), "test-code"));
        service.finish(owner);
        assertThrows(ResponseStatusException.class, () -> service.location("accounts/11/locations/99"));
        service.location(LOCATION);
        awaitSync();
        assertEquals(2, service.status(owner).totalReviews());
        assertEquals(3.5, service.status(owner).rating());
        var newest = service.reviews("", "all", 0, "newest", 0);
        assertEquals("Bill", newest.reviews().getFirst().reviewer());
        assertEquals("Anna", service.reviews("", "all", 0, "highest", 0).reviews().getFirst().reviewer());
        assertEquals(0, newest.featuredCount());
        var anna = service.reviews("original", "unfeatured", 5, "oldest", 0).reviews().getFirst();
        service.publication(anna.id(), true, "owner");
        assertEquals(1, service.reviews("", "featured", 0, "newest", 0).featuredCount());
        assertTrue(service.feed(false).reviews().isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.save(new GoogleReviewsService.Settings(true, 4, "ROTATE")));
        service.save(new GoogleReviewsService.Settings(true, 3, "NEWEST_SELECTED"));
        mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reviews.length()").value(1)).andExpect(jsonPath("$.reviews[0].reviewer").value("Anna"))
                .andExpect(jsonPath("$.reviews[0].attribution").value("Google"))
                .andExpect(jsonPath("$.reviews[0].id").doesNotExist()).andExpect(jsonPath("$.reviews[0].reply").doesNotExist())
                .andExpect(jsonPath("$.refreshCredential").doesNotExist());

        when(google.reviews("access", LOCATION)).thenReturn(new GoogleBusinessClient.Snapshot(List.of(
                new GoogleBusinessClient.Review("one", "Anna", 4, "Edited review", JAN, FEB, "")), 1, 4.0));
        service.sync();
        assertEquals("Edited review", service.feed(false).reviews().getFirst().comment());
        assertEquals(1, service.reviews("", "all", 0, "newest", 0).total());
        service.publication(anna.id(), false, "owner");
        assertTrue(service.feed(false).reviews().isEmpty());
        service.publication(anna.id(), true, "owner");

        when(google.reviews("access", LOCATION)).thenThrow(new GoogleBusinessClient.Failure("Google unavailable", false));
        service.sync();
        assertEquals("ERROR", service.status(owner).state());
        assertTrue(service.feed(false).reviews().isEmpty());
        when(google.refresh("private-refresh")).thenThrow(new GoogleBusinessClient.Failure("Expired", true));
        service.sync();
        assertEquals("EXPIRED", service.status(owner).state());
        assertTrue(service.disconnect());
        verify(google).revoke("private-refresh");
    }

    @Test void pagesResultsLimitsPublicFeedAndExpiresCachedMetadata() throws Exception {
        assertTrue(service.callback(start(owner), "test-code"));
        service.finish(owner);
        var reviews = java.util.stream.IntStream.range(0, 26).mapToObj(i ->
                new GoogleBusinessClient.Review("id" + i, "Reviewer " + i, 5, "Comment " + i,
                        JAN.plusSeconds(i), JAN.plusSeconds(i), "")).toList();
        when(google.reviews("access", LOCATION)).thenReturn(new GoogleBusinessClient.Snapshot(reviews, 26, 5.0));
        service.location(LOCATION);
        awaitSync();
        var first = service.reviews("", "all", 0, "oldest", 0);
        var second = service.reviews("", "all", 0, "oldest", 1);
        assertEquals(25, first.reviews().size());
        assertTrue(first.hasMore());
        assertEquals("Reviewer 25", second.reviews().getFirst().reviewer());
        assertFalse(second.hasMore());
        first.reviews().stream().limit(6).forEach(item -> service.publication(item.id(), true, "owner"));
        service.save(new GoogleReviewsService.Settings(true, 3, "ROTATE"));
        assertEquals(3, service.feed(false).reviews().size());
        assertEquals(service.feed(false), service.feed(false));
        jdbc.update("""
                update google_review_connection set metadata_loaded_at=now()-interval '2 days',
                    last_success=now()-interval '3 hours' where restaurant_id=1
                """);
        service.cleanup();
        assertNull(service.status(owner).businessName());
        assertNull(service.status(owner).totalReviews());
    }

    private void awaitSync() throws Exception {
        for (int i = 0; i < 150 && "SYNCING".equals(service.status(owner).state()); i++) Thread.sleep(20);
        assertEquals("CONNECTED", service.status(owner).state());
    }

    private String start(UUID session) { return URI.create(service.connect(session)).getQuery().substring("state=".length()); }

    private UUID staffSession() {
        UUID user = UUID.randomUUID(), session = UUID.randomUUID();
        jdbc.update("""
                insert into staff_user(id,username,password_hash,role,created_at,updated_at)
                values (?,?,?,'ADMIN',now(),now())
                """, user, "google-" + user, "unused-test-hash");
        jdbc.update("""
                insert into staff_session(id,user_id,refresh_hash,created_at,expires_at)
                values (?,?,?,now(),now()+interval '1 hour')
                """, session, user, "unused-test-hash");
        return session;
    }
}
