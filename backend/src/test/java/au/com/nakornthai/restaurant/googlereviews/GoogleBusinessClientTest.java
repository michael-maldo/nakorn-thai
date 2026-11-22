package au.com.nakornthai.restaurant.googlereviews;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GoogleBusinessClientTest {
    private static final String REVIEWS = "https://mybusiness.googleapis.com/v4/accounts/11/locations/22/reviews";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final GoogleBusinessClient google = new GoogleBusinessClient("client", "secret",
            "https://example.test/api/integrations/google/callback");

    GoogleBusinessClientTest() { ReflectionTestUtils.setField(google, "api", builder.build()); }

    @Test void distinguishesMissingSettingsAndInvalidRedirectsWithoutEchoingValues() {
        String callback = "https://example.test/api/integrations/google/callback";
        assertEquals(GoogleBusinessClient.OAuthConfiguration.READY, google.configuration());
        assertEquals(GoogleBusinessClient.OAuthConfiguration.MISSING_OAUTH_SETTINGS,
                new GoogleBusinessClient("", "secret", callback).configuration());
        assertEquals(GoogleBusinessClient.OAuthConfiguration.INVALID_OAUTH_SETTINGS,
                new GoogleBusinessClient("invalid client", "secret", callback).configuration());
        for (String invalid : new String[]{
                "http://example.test/api/integrations/google/callback",
                "https://staff:private@example.test/api/integrations/google/callback",
                callback + "?secret=private", callback + "#fragment",
                "https://example.test/api/integrations/google/wrong"}) {
            assertEquals(GoogleBusinessClient.OAuthConfiguration.INVALID_REDIRECT_URI,
                    new GoogleBusinessClient("client", "secret", invalid).configuration());
        }
    }

    @Test void usesOfflineConsentAndS256() {
        String url = google.authorize("state-value", "verifier-value").toString();
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("code_challenge=" + GoogleBusinessClient.digest("verifier-value")));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("business.manage"));
        assertFalse(url.contains("secret"));
        assertFalse(url.contains("verifier-value"));
    }

    @Test void exchangesRefreshesAndRevokesWithFormBodies() {
        server.expect(requestTo(TOKEN)).andExpect(content().string(containsString("code_verifier=verifier")))
                .andRespond(withSuccess("{\"access_token\":\"access\",\"refresh_token\":\"refresh\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(TOKEN)).andExpect(content().string(containsString("grant_type=refresh_token")))
                .andRespond(withSuccess("{\"access_token\":\"refreshed\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/revoke"))
                .andExpect(content().string("token=refresh")).andRespond(withSuccess());
        assertEquals("refresh", google.exchange("code", "verifier").refresh());
        assertEquals("refreshed", google.refresh("refresh").access());
        assertTrue(google.revoke("refresh"));
        server.verify();
    }

    @Test void exposesRevokedGrantWithoutReturningProviderBody() {
        server.expect(requestTo(TOKEN)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\",\"error_description\":\"private\"}"));
        var failure = assertThrows(GoogleBusinessClient.Failure.class, () -> google.refresh("refresh"));
        assertTrue(failure.expired);
        assertFalse(failure.getMessage().contains("private"));
        server.verify();
    }

    @Test void retriesThenUsesAllReviewPagesAndDeduplicatesByMostRecentEdit() {
        server.expect(requestTo(REVIEWS + "?pageSize=50")).andRespond(withServerError());
        server.expect(requestTo(REVIEWS + "?pageSize=50")).andExpect(header("Authorization", "Bearer access"))
                .andRespond(withSuccess("""
                    {"totalReviewCount":2,"averageRating":4,"nextPageToken":"a+b",
                    "reviews":[{"reviewId":"one","starRating":"FIVE","createTime":"2026-01-01T00:00:00Z","comment":"Original"}]}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(REVIEWS + "?pageSize=50&pageToken=a%2Bb")).andRespond(withSuccess("""
                {"reviews":[{"reviewId":"one","starRating":"FIVE","createTime":"2026-01-01T00:00:00Z",
                  "updateTime":"2026-02-01T00:00:00Z","comment":"Edited"},
                  {"reviewId":"two","starRating":"THREE","createTime":"2026-01-02T00:00:00Z"}]}
                """, MediaType.APPLICATION_JSON));
        var result = google.reviews("access", "accounts/11/locations/22");
        assertEquals(2, result.total());
        assertEquals(4.0, result.rating());
        assertEquals(2, result.reviews().size());
        assertEquals("Edited", result.reviews().getFirst().comment());
        server.verify();
    }

    @Test void checksAccountAndLocationResourceNames() {
        server.expect(requestTo("https://mybusinessaccountmanagement.googleapis.com/v1/accounts?pageSize=20"))
                .andRespond(withSuccess("{\"accounts\":[{\"name\":\"accounts/11\"},{\"name\":\"https://untrusted.test\"}]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("https://mybusinessbusinessinformation.googleapis.com/v1/accounts/11/locations?")))
                .andRespond(withSuccess("""
                        {"locations":[{"name":"locations/22","title":"Restaurant",
                         "metadata":{"mapsUri":"https://www.google.com/maps/place/example"}}]}
                        """, MediaType.APPLICATION_JSON));
        var locations = google.locations("access");
        assertEquals(1, locations.size());
        assertEquals("accounts/11/locations/22", locations.getFirst().resource());
        assertNotNull(locations.getFirst().mapsUrl());
        server.verify();
    }
}
