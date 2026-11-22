package au.com.nakornthai.restaurant.googlereviews;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
class GoogleBusinessClient {
    record Location(String resource, String title, String address, String mapsUrl) {}
    record Review(String id, String reviewer, int stars, String comment, Instant created,
                  Instant updated, String reply) {}
    record Snapshot(List<Review> reviews, Integer total, Double rating) {}
    record Token(String access, String refresh) {}

    static final class Failure extends RuntimeException {
        final boolean expired;
        Failure(String message, boolean expired) {
            super(message);
            this.expired = expired;
        }
    }

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String ACCOUNTS = "https://mybusinessaccountmanagement.googleapis.com/v1/accounts";
    private static final String LOCATIONS = "https://mybusinessbusinessinformation.googleapis.com/v1/";
    private static final String REVIEWS = "https://mybusiness.googleapis.com/v4/";
    private final RestClient api;
    private final String clientId;
    private final String secret;
    private final String callback;

    GoogleBusinessClient(@Value("${GOOGLE_BUSINESS_CLIENT_ID:}") String clientId,
                         @Value("${GOOGLE_BUSINESS_CLIENT_SECRET:}") String secret,
                         @Value("${GOOGLE_BUSINESS_REDIRECT_URI:}") String callback) {
        this.clientId = clientId;
        this.secret = secret;
        this.callback = callback;
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(12));
        api = RestClient.builder().requestFactory(factory).build();
    }

    enum OAuthConfiguration { READY, MISSING_OAUTH_SETTINGS, INVALID_OAUTH_SETTINGS, INVALID_REDIRECT_URI }

    OAuthConfiguration configuration() {
        if (clientId.isBlank() || secret.isBlank() || callback.isBlank())
            return OAuthConfiguration.MISSING_OAUTH_SETTINGS;
        if (clientId.chars().anyMatch(Character::isWhitespace) || secret.chars().anyMatch(Character::isWhitespace))
            return OAuthConfiguration.INVALID_OAUTH_SETTINGS;
        try {
            URI uri = URI.create(callback);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.normalize().equals(uri)
                    && uri.getRawPath().endsWith("/api/integrations/google/callback"))
                return OAuthConfiguration.READY;
        } catch (IllegalArgumentException ignored) {
            // Diagnostics expose only the outcome, never the configured value.
        }
        return OAuthConfiguration.INVALID_REDIRECT_URI;
    }

    URI authorize(String state, String verifier) {
        return uri("https://accounts.google.com/o/oauth2/v2/auth", Map.of(
                "client_id", clientId, "redirect_uri", callback, "response_type", "code",
                "access_type", "offline", "prompt", "consent select_account",
                "scope", "https://www.googleapis.com/auth/business.manage",
                "state", state, "code_challenge", digest(verifier), "code_challenge_method", "S256"));
    }

    Token exchange(String code, String verifier) {
        return token(Map.of("grant_type", "authorization_code", "code", code,
                "code_verifier", verifier, "redirect_uri", callback));
    }

    Token refresh(String refresh) {
        return token(Map.of("grant_type", "refresh_token", "refresh_token", refresh));
    }

    private Token token(Map<String, String> fields) {
        var form = new LinkedMultiValueMap<String, String>();
        fields.forEach(form::add);
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        try {
            JsonNode result = api.post().uri(TOKEN_ENDPOINT).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (result == null || result.path("access_token").asString("").isBlank())
                throw new Failure("Google did not provide an access token.", false);
            return new Token(result.path("access_token").asString(), result.path("refresh_token").asString(null));
        } catch (RestClientResponseException e) {
            JsonNode body = null;
            try { body = e.getResponseBodyAs(JsonNode.class); } catch (RuntimeException ignored) {}
            boolean expired = body != null && "invalid_grant".equals(body.path("error").asString());
            throw new Failure(expired ? "Google authorization expired. Reconnect to continue."
                    : "Google authorization could not be completed.", expired);
        } catch (RestClientException e) {
            throw new Failure("Google authorization is temporarily unavailable.", false);
        }
    }

    boolean revoke(String refresh) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", refresh);
        try {
            api.post().uri("https://oauth2.googleapis.com/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            return e.getStatusCode().value() == 400;
        } catch (RestClientException e) {
            return false;
        }
    }

    List<Location> locations(String access) {
        var result = new LinkedHashMap<String, Location>();
        for (var page : pages(ACCOUNTS, access, Map.of("pageSize", "20"))) {
            for (var account : page.path("accounts")) {
                String parent = account.path("name").asString("");
                if (!parent.matches("accounts/[0-9]+")) continue;
                for (var locationPage : pages(LOCATIONS + parent + "/locations", access,
                        Map.of("readMask", "name,title,storefrontAddress,metadata", "pageSize", "100"))) {
                    for (var location : locationPage.path("locations")) {
                        String name = location.path("name").asString("");
                        if (name.matches("locations/[0-9]+")) {
                            result.putIfAbsent(name, new Location(parent + "/" + name,
                                    location.path("title").asString("Google Business Profile"),
                                    address(location.path("storefrontAddress")), mapsUrl(location.path("metadata").path("mapsUri").asString(""))));
                        }
                    }
                }
            }
        }
        return List.copyOf(result.values());
    }

    Snapshot reviews(String access, String location) {
        if (!location.matches("accounts/[0-9]+/locations/[0-9]+"))
            throw new IllegalArgumentException("Invalid location resource");
        var pages = pages(REVIEWS + location + "/reviews", access, Map.of("pageSize", "50"));
        var reviews = new LinkedHashMap<String, Review>();
        for (var page : pages) {
            for (var item : page.path("reviews")) {
                String id = item.path("reviewId").asString("");
                int rating = switch (item.path("starRating").asString("")) {
                    case "ONE" -> 1;
                    case "TWO" -> 2;
                    case "THREE" -> 3;
                    case "FOUR" -> 4;
                    case "FIVE" -> 5;
                    default -> 0;
                };
                if (id.isBlank() || rating == 0 || !item.hasNonNull("createTime")) continue;
                var reviewer = item.path("reviewer");
                String name = reviewer.path("isAnonymous").asBoolean(false) ? "Anonymous"
                        : reviewer.path("displayName").asString("Anonymous");
                Instant created = Instant.parse(item.path("createTime").asString());
                Instant updated = item.hasNonNull("updateTime") ? Instant.parse(item.path("updateTime").asString()) : created;
                var review = new Review(id, name, rating, item.path("comment").asString(""), created,
                        updated, item.path("reviewReply").path("comment").asString(""));
                reviews.merge(id, review, (a, b) -> a.updated().isAfter(b.updated()) ? a : b);
            }
        }
        var first = pages.getFirst();
        return new Snapshot(List.copyOf(reviews.values()), first.hasNonNull("totalReviewCount")
                ? first.path("totalReviewCount").asInt() : null, first.hasNonNull("averageRating")
                ? first.path("averageRating").asDouble() : null);
    }

    private List<JsonNode> pages(String endpoint, String access, Map<String, String> params) {
        var seen = new HashSet<String>();
        var pages = new ArrayList<JsonNode>();
        String next = "";
        do {
            if (!seen.add(next) || pages.size() >= 200) throw new Failure("Google pagination could not complete.", false);
            var query = new LinkedHashMap<>(params);
            if (!next.isEmpty()) query.put("pageToken", next);
            var page = get(uri(endpoint, query), access);
            pages.add(page);
            next = page.path("nextPageToken").asString("");
        } while (!next.isEmpty());
        return pages;
    }

    private JsonNode get(URI url, String access) {
        for (int attempt = 0; ; attempt++) {
            try {
                JsonNode response = api.get().uri(url).headers(h -> h.setBearerAuth(access)).retrieve().body(JsonNode.class);
                if (response == null || !response.isObject()) throw new Failure("Google returned an incomplete response.", false);
                return response;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401) throw new Failure("Google authorization expired. Reconnect to continue.", true);
                if (status == 403) throw new Failure("Google denied access. Check API approval and location verification.", false);
                if (status == 404) throw new Failure("Google could not find this business location.", false);
                if (status < 500 && status != 429) throw new Failure("Google rejected the request.", false);
                if (e.getResponseHeaders() != null && e.getResponseHeaders().containsHeader("Retry-After"))
                    throw new Failure("Google requested a delay before the next attempt.", false);
            } catch (RestClientException e) {
                // Network and transient HTTP failures share the bounded retry schedule.
            }
            if (attempt == 2) throw new Failure("Google is temporarily unavailable or its quota has been reached.", false);
            try { Thread.sleep((500L << attempt) + ThreadLocalRandom.current().nextLong(250)); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new Failure("Google request was interrupted.", false);
            }
        }
    }

    private static URI uri(String base, Map<String, String> params) {
        var builder = UriComponentsBuilder.fromUriString(base);
        params.keySet().forEach(key -> builder.queryParam(key, "{" + key + "}"));
        return builder.encode().buildAndExpand(params).toUri();
    }

    private static String address(JsonNode address) {
        var parts = new ArrayList<String>();
        for (var line : address.path("addressLines")) {
            String value = line.asString("");
            if (!value.isBlank()) parts.add(value);
        }
        String locality = address.path("locality").asString("");
        if (!locality.isBlank()) parts.add(locality);
        return String.join(", ", parts);
    }

    private static String mapsUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getHost() == null) return null;
            if (uri.getHost().equals("maps.google.com") || uri.getHost().equals("maps.app.goo.gl")
                    || (uri.getHost().equals("www.google.com") && uri.getPath().startsWith("/maps/"))) return value;
        } catch (IllegalArgumentException ignored) {}
        return null;
    }

    static String digest(String value) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
