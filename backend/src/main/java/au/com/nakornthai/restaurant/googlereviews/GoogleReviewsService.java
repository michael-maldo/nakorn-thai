package au.com.nakornthai.restaurant.googlereviews;

import au.com.nakornthai.restaurant.configuration.CredentialCipher;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static au.com.nakornthai.restaurant.googlereviews.GoogleBusinessClient.digest;

@Service
class GoogleReviewsService {
    // The existing backend schema enforces a single restaurant, with ID 1.
    private static final short RESTAURANT = 1;
    private static final String CREDENTIAL = "google-reviews:restaurant:1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final GoogleBusinessClient google;
    private final CredentialCipher cipher;
    private final boolean publicUseApproved;
    private final boolean integrationEnabled;
    private final Duration maximumSnapshotAge;
    private volatile CachedSnapshot cached;
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final AtomicBoolean locationNeedsSync = new AtomicBoolean();
    private final TaskScheduler scheduler;

    record Settings(boolean enabled, int maximum, String ordering) {}
    record Status(String state, boolean configured, String configurationStatus, boolean publicUseApproved, String businessName,
                  String location, String locationAddress, String mapsUrl, Instant lastAttempt, Instant lastSuccess, String error,
                  Integer totalReviews, Double rating, long featuredCount) {}
    record Review(String id, String reviewer, int stars, String comment, Instant created,
                  Instant updated, String reply, boolean featured, Instant selectedAt, String attribution) {}
    record PublicReview(String reviewer, int stars, String comment, Instant created, String attribution) {}
    record PublicFeed(boolean enabled, boolean curated, List<PublicReview> reviews) {}
    record Page(List<Review> reviews, int total, int featuredCount, boolean hasMore) {}
    private record Connection(String credential, String location, String title, String address, String mapsUrl, boolean expired, UUID generation,
                              Instant lastAttempt, Instant lastSuccess, String error, Integer total, Double rating, Settings settings) {}
    private record CachedSnapshot(UUID generation, Instant loaded, GoogleBusinessClient.Snapshot snapshot) {}
    private record OAuthAttempt(String hash, UUID session, UUID generation, String verifier, String pending, String error) {}

    GoogleReviewsService(JdbcTemplate jdbc, TransactionTemplate transactions, GoogleBusinessClient google,
                         CredentialCipher cipher, @Value("${GOOGLE_REVIEWS_PUBLIC_USE_APPROVED:false}") boolean publicUseApproved,
                         @Value("${GOOGLE_REVIEWS_ENABLED:true}") boolean integrationEnabled,
                         @Value("${google.reviews.sync-delay-ms:${GOOGLE_REVIEWS_SYNC_DELAY_MS:3600000}}") long delay,
                         @Qualifier("googleReviewsScheduler") TaskScheduler scheduler) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.google = google;
        this.cipher = cipher;
        this.scheduler = scheduler;
        this.publicUseApproved = publicUseApproved;
        this.integrationEnabled = integrationEnabled;
        maximumSnapshotAge = Duration.ofMillis(Math.min(Math.max(delay, 60_000L), 6_900_000L) + 300_000L);
    }

    private String configurationStatus() {
        if (!integrationEnabled) return "DISABLED";
        var oauth = google.configuration();
        if (oauth != GoogleBusinessClient.OAuthConfiguration.READY) return oauth.name();
        return cipher.available() ? "READY" : "CREDENTIAL_STORAGE_UNAVAILABLE";
    }

    boolean configured() { return configurationStatus().equals("READY"); }

    String connect(UUID session) {
        if (!configured()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Google Reviews setup is incomplete. Check the connection status.");
        String state = random();
        String verifier = random();
        transactions.executeWithoutResult(tx -> {
            Connection connection = connection(true);
            jdbc.update("delete from google_review_oauth where restaurant_id=? and staff_session_id=?", RESTAURANT, session);
            jdbc.update("""
                    insert into google_review_oauth(state_hash, restaurant_id, staff_session_id, generation, verifier, expires_at)
                    values (?,?,?,?,?,now() + interval '10 minutes')
                    """, digest(state), RESTAURANT, session, connection.generation(),
                    cipher.encrypt(CREDENTIAL + ":state:" + digest(state), verifier));
        });
        return google.authorize(state, verifier).toString();
    }

    boolean callback(String state, String code) {
        if (!configured() || state == null || !state.matches("[A-Za-z0-9_-]{43}")) return false;
        String hash = digest(state);
        var attempts = jdbc.query("""
                update google_review_oauth o set consumed=true, verifier=''
                from google_review_oauth original, staff_session s, staff_user u, google_review_connection c
                where o.state_hash=? and original.state_hash=o.state_hash and not o.consumed
                    and o.expires_at>now() and s.id=o.staff_session_id and s.revoked_at is null
                    and s.expires_at>now() and u.id=s.user_id and u.enabled and u.role='ADMIN'
                    and c.restaurant_id=o.restaurant_id and c.generation=o.generation and o.restaurant_id=?
                returning o.state_hash,o.staff_session_id,o.generation,original.verifier,o.pending_credential,o.error
                """, this::oauth, hash, RESTAURANT);
        if (attempts.isEmpty()) return false;
        String failure = "Google authorization was cancelled.";
        if (code != null && !code.isBlank() && code.length() <= 4096) {
            try {
                var token = google.exchange(code, cipher.decrypt(CREDENTIAL + ":state:" + hash, attempts.getFirst().verifier()));
                if (token.refresh() != null && !token.refresh().isBlank()) {
                    return jdbc.update("""
                            update google_review_oauth set pending_credential=?
                            where state_hash=? and expires_at>now()
                            """, cipher.encrypt(CREDENTIAL, token.refresh()), hash) == 1;
                }
                failure = "Google did not grant offline access. Reconnect and approve access.";
            } catch (GoogleBusinessClient.Failure e) {
                failure = e.getMessage();
            }
        }
        jdbc.update("update google_review_oauth set error=? where state_hash=?", failure, hash);
        return false;
    }

    Status finish(UUID session) {
        transactions.executeWithoutResult(tx -> {
            Connection current = connection(true);
            var attempts = jdbc.query("""
                    select * from google_review_oauth where staff_session_id=? and restaurant_id=?
                      and generation=? and expires_at>now() and consumed
                    """, this::oauth, session, RESTAURANT, current.generation());
            if (attempts.isEmpty()) return;
            var attempt = attempts.getFirst();
            if (attempt.pending() == null) return;
            jdbc.update("""
                    update google_review_connection set refresh_credential=?, location_resource=null, location_title=null, location_address=null, maps_url=null, metadata_loaded_at=null,
                       authorization_expired=false, generation=?, last_attempt=null,last_success=null,sync_error=null,total_reviews=null,overall_rating=null
                    where restaurant_id=?
                    """, attempt.pending(), UUID.randomUUID(), RESTAURANT);
            jdbc.update("delete from google_review_oauth where restaurant_id=?", RESTAURANT);
            cached = null;
        });
        return status(session);
    }

    Status status(UUID session) {
        Connection connection = connection(false);
        var snapshot = snapshot(connection);
        boolean pending = false;
        boolean received = false;
        String oauthError = null;
        if (session != null) {
            var attempts = jdbc.query("""
                    select * from google_review_oauth where staff_session_id=? and restaurant_id=?
                      and expires_at>now() and generation=?
                    """, this::oauth, session, RESTAURANT, connection.generation());
            pending = !attempts.isEmpty() && attempts.getFirst().error() == null;
            if (!attempts.isEmpty()) {
                oauthError = attempts.getFirst().error();
                received = attempts.getFirst().pending() != null;
            }
        }
        String state = syncing.get() ? "SYNCING" : received ? "AUTHORIZATION_RECEIVED" : pending ? "AUTHORIZATION_REQUIRED"
                : connection.expired() ? "EXPIRED" : oauthError != null ? "ERROR"
                : connection.credential() == null ? "DISCONNECTED" : connection.location() == null ? "LOCATION_REQUIRED"
                : connection.error() != null ? "ERROR" : "CONNECTED";
        String readiness = configurationStatus();
        return new Status(state, readiness.equals("READY"), readiness, publicUseApproved, connection.title(), connection.location(), connection.address(), connection.mapsUrl(),
                connection.lastAttempt(), connection.lastSuccess(), oauthError != null ? oauthError : connection.error(),
                connection.total(), connection.rating(),
                snapshot == null ? featuredCount(connection) : selected(connection, snapshot).size());
    }

    List<GoogleBusinessClient.Location> locations() {
        Connection connection = connection(false);
        try { return google.locations(access(connection)); }
        catch (GoogleBusinessClient.Failure e) {
            markExpired(connection, e);
            throw error(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    void location(String resource) {
        Connection original = connection(false);
        GoogleBusinessClient.Location selected = locations().stream().filter(item -> item.resource().equals(resource))
                .findFirst().orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Choose an accessible Google location."));
        transactions.executeWithoutResult(tx -> {
            Connection current = connection(true);
            if (!current.generation().equals(original.generation()))
                throw error(HttpStatus.CONFLICT, "The Google connection changed. Reload locations.");
            jdbc.update("""
                    update google_review_connection set location_resource=?,location_title=?,location_address=?,maps_url=?,metadata_loaded_at=now(),generation=?,
                        last_attempt=null,last_success=null,sync_error=null,total_reviews=null,overall_rating=null where restaurant_id=?
                    """, selected.resource(), selected.title(), selected.address(), selected.mapsUrl(), UUID.randomUUID(), RESTAURANT);
            cached = null;
        });
        locationNeedsSync.set(true);
        requestSync();
    }

    boolean disconnect() {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            Connection current = connection(true);
            boolean revoked = revoke(current);
            jdbc.update("""
                    update google_review_connection set refresh_credential=null, location_resource=null, location_title=null, location_address=null, maps_url=null, metadata_loaded_at=null,
                        authorization_expired=false, generation=?, last_attempt=null,last_success=null,sync_error=null,total_reviews=null,overall_rating=null
                    where restaurant_id=?
                    """, UUID.randomUUID(), RESTAURANT);
            jdbc.update("delete from google_review_oauth where restaurant_id=?", RESTAURANT);
            cached = null;
            return revoked;
        }));
    }

    private boolean revoke(Connection connection) {
        if (connection.credential() == null) return true;
        if (!configured()) return false;
        try {
            return google.revoke(cipher.decrypt(CREDENTIAL, connection.credential()));
        } catch (IllegalStateException unreadable) {
            return false;
        }
    }

    @Scheduled(fixedDelayString = "${google.reviews.sync-delay-ms:${GOOGLE_REVIEWS_SYNC_DELAY_MS:3600000}}", initialDelay = 10000,
            scheduler = "googleReviewsScheduler")
    public void scheduledSync() {
        if (configured()) sync();
    }

    @Scheduled(fixedDelay = 60000, scheduler = "googleReviewsScheduler")
    public void cleanup() {
        jdbc.update("delete from google_review_oauth where expires_at<=now()");
        jdbc.update("""
                update google_review_connection set location_title=null, location_address=null, maps_url=null,
                    metadata_loaded_at=null where metadata_loaded_at<now()-interval '1 day'
                """);
        jdbc.update("""
                update google_review_connection set total_reviews=null, overall_rating=null
                where (total_reviews is not null or overall_rating is not null)
                    and last_success < now() - interval '2 hours'
                """);
        var current = cached;
        if (current != null && current.loaded().plus(maximumSnapshotAge).isBefore(Instant.now())) forget(current);
    }

    private synchronized void forget(CachedSnapshot current) {
        if (cached == current) cached = null;
    }

    void requestSync() {
        if (!configured()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "Google Reviews setup is incomplete. Check the connection status.");
        if (!syncing.compareAndSet(false, true)) return;
        try {
            scheduler.schedule(this::runSync, Instant.now());
        } catch (RuntimeException failure) {
            syncing.set(false);
            throw failure;
        }
    }

    void sync() {
        if (configured() && syncing.compareAndSet(false, true)) runSync();
    }

    private void runSync() {
        locationNeedsSync.set(false);
        try {
            transactions.executeWithoutResult(tx -> {
                // An advisory lock spans the provider request without locking the settings row.
                // Generation checks discard a result when a different location or grant replaces it.
                if (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "select pg_try_advisory_xact_lock(483701, ?)", Boolean.class, (int) RESTAURANT))) return;
                Connection current = connection(false);
                if (current.location() == null || current.expired()) return;
                OffsetDateTime attempted = OffsetDateTime.now(ZoneOffset.UTC);
                try {
                    String access = access(current);
                    var snapshot = google.reviews(access, current.location());
                    if (current.title() == null) refreshLocationMetadata(current, access);
                    int updated = jdbc.update("""
                            update google_review_connection set last_attempt=?,last_success=now(), sync_error=null,
                                total_reviews=?,overall_rating=? where restaurant_id=? and generation=?
                            """, attempted, snapshot.total(), snapshot.rating(), RESTAURANT, current.generation());
                    if (updated == 1) txAfterCommit(() -> cached = new CachedSnapshot(current.generation(), Instant.now(), snapshot));
                } catch (GoogleBusinessClient.Failure e) {
                    syncError(current, attempted, e.getMessage(), e.expired);
                } catch (java.time.format.DateTimeParseException e) {
                    syncError(current, attempted, "Google returned an invalid review timestamp.", false);
                }
            });
        } finally {
            syncing.set(false);
            if (locationNeedsSync.getAndSet(false) && configured()) requestSync();
        }
    }

    private void refreshLocationMetadata(Connection current, String access) {
        try {
            var metadata = google.locations(access).stream()
                    .filter(item -> item.resource().equals(current.location())).findFirst().orElse(null);
            if (metadata != null) jdbc.update("""
                    update google_review_connection set location_title=?,location_address=?,maps_url=?,metadata_loaded_at=now()
                    where restaurant_id=? and generation=?
                    """, metadata.title(), metadata.address(), metadata.mapsUrl(), RESTAURANT, current.generation());
        } catch (GoogleBusinessClient.Failure ignored) {
            // Review access is independent of transient location metadata errors.
        }
    }

    private void syncError(Connection current, OffsetDateTime attempt, String message, boolean expired) {
        int updated = jdbc.update("""
                update google_review_connection set last_attempt=?, authorization_expired=?,sync_error=?
                where restaurant_id=? and generation=?
                """, attempt, expired, message, RESTAURANT, current.generation());
        if (updated == 1 && expired) cached = null;
    }

    private static void txAfterCommit(Runnable operation) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { operation.run(); }
                });
    }

    Page reviews(String query, String publication, int stars, String sort, int page) {
        Connection connection = connection(false);
        var snapshot = snapshot(connection);
        if (snapshot == null && configured() && connection.location() != null && !connection.expired()
                && connection.error() == null && connection.lastSuccess() != null) {
            requestSync();
        }
        if (snapshot == null) return new Page(List.of(), 0, 0, false);
        var selections = selections(connection);
        String term = query.strip().toLowerCase(Locale.ROOT);
        Comparator<Review> newest = Comparator.comparing(Review::created).reversed().thenComparing(Review::id);
        Comparator<Review> order = switch (sort) {
            case "oldest" -> Comparator.comparing(Review::created).thenComparing(Review::id);
            case "highest" -> Comparator.comparingInt(Review::stars).reversed().thenComparing(newest);
            case "lowest" -> Comparator.comparingInt(Review::stars).thenComparing(newest);
            default -> newest;
        };
        var filtered = snapshot.reviews().stream().map(item -> review(item, selections.get(digest(item.id()))))
                .filter(review -> stars == 0 || review.stars() == stars)
                .filter(review -> !publication.equals("featured") || review.featured())
                .filter(review -> !publication.equals("unfeatured") || !review.featured())
                .filter(review -> term.isEmpty() || review.reviewer().toLowerCase(Locale.ROOT).contains(term)
                        || review.comment().toLowerCase(Locale.ROOT).contains(term))
                .sorted(order).toList();
        int start = Math.min(page * 25, filtered.size());
        int end = Math.min(start + 25, filtered.size());
        int featured = (int) snapshot.reviews().stream().filter(item -> selections.containsKey(digest(item.id()))).count();
        return new Page(filtered.subList(start, end), filtered.size(), featured, end < filtered.size());
    }

    void publication(String id, boolean featured, String actor) {
        transactions.executeWithoutResult(tx -> {
            Connection current = connection(true);
            var snapshot = snapshot(current);
            if (snapshot == null || snapshot.reviews().stream().noneMatch(item -> digest(item.id()).equals(id)))
                throw error(HttpStatus.NOT_FOUND, "This review is unavailable. Sync reviews and try again.");
            jdbc.update("""
                    insert into google_review_selection(restaurant_id,location_id,review_key,featured,updated_by)
                    values (?,?,?,?,?) on conflict (restaurant_id,location_id,review_key) do update
                    set featured=excluded.featured, updated_by=excluded.updated_by, updated_at=now()
                    where google_review_selection.featured<>excluded.featured
                    """, RESTAURANT, locationId(current), id, featured, actor);
        });
    }

    Settings settings() { return connection(false).settings(); }

    Settings save(Settings settings) {
        if (!Set.of(3, 6, 9).contains(settings.maximum()) || settings.ordering() == null
                || !Set.of("NEWEST_SELECTED", "ROTATE").contains(settings.ordering()))
            throw error(HttpStatus.BAD_REQUEST, "Choose a supported review count and ordering.");
        jdbc.update("""
                update google_review_connection set homepage_enabled=?,homepage_limit=?,homepage_order=?,
                    settings_updated_at=now() where restaurant_id=?
                """, settings.enabled(), settings.maximum(), settings.ordering(), RESTAURANT);
        return settings;
    }

    PublicFeed feed(boolean preview) {
        Connection connection = connection(false);
        Settings settings = connection.settings();
        boolean enabled = publicUseApproved && settings.enabled() && snapshot(connection) != null && connection.error() == null;
        var snapshot = snapshot(connection);
        if (snapshot == null || (!preview && !enabled))
            return new PublicFeed(enabled, true, List.of());
        var selected = selected(connection, snapshot);
        if (settings.ordering().equals("ROTATE") && selected.size() > 1) {
            int offset = Math.floorMod(Instant.now().getEpochSecond() / 86400, selected.size());
            java.util.Collections.rotate(selected, -offset);
        }
        return new PublicFeed(enabled, true, selected.stream().limit(settings.maximum())
                .map(item -> new PublicReview(item.reviewer(), item.stars(), item.comment(), item.created(), item.attribution()))
                .toList());
    }

    private List<Review> selected(Connection connection, GoogleBusinessClient.Snapshot snapshot) {
        var selections = selections(connection);
        return snapshot.reviews().stream().map(item -> review(item, selections.get(digest(item.id()))))
                .filter(Review::featured).sorted(Comparator.comparing(Review::selectedAt).reversed().thenComparing(Review::id))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    private Map<String, Instant> selections(Connection current) {
        if (current.location() == null) return Map.of();
        return jdbc.query("""
                select review_key,updated_at from google_review_selection
                where restaurant_id=? and location_id=? and featured
                """, result -> {
            Map<String, Instant> values = new java.util.HashMap<>();
            while (result.next()) values.put(result.getString("review_key"), instant(result, "updated_at"));
            return values;
        }, RESTAURANT, locationId(current));
    }

    private long featuredCount(Connection current) { return selections(current).size(); }

    private static Review review(GoogleBusinessClient.Review item, Instant selected) {
        return new Review(digest(item.id()), item.reviewer(), item.stars(), item.comment(), item.created(), item.updated(),
                item.reply(), selected != null, selected, "Google");
    }

    private GoogleBusinessClient.Snapshot snapshot(Connection connection) {
        var current = cached;
        return !configured() || current == null || connection.expired() || !current.generation().equals(connection.generation())
                || !current.loaded().plus(maximumSnapshotAge).isAfter(Instant.now()) ? null : current.snapshot();
    }

    private void markExpired(Connection connection, GoogleBusinessClient.Failure failure) {
        if (failure.expired) jdbc.update("""
                update google_review_connection set authorization_expired=true,sync_error=?
                where restaurant_id=? and generation=?
                """, failure.getMessage(), RESTAURANT, connection.generation());
    }

    private String access(Connection connection) {
        if (!configured() || connection.credential() == null || connection.expired())
            throw error(HttpStatus.CONFLICT, "Connect Google before selecting a location or syncing.");
        return google.refresh(cipher.decrypt(CREDENTIAL, connection.credential())).access();
    }

    private Connection connection(boolean lock) {
        return jdbc.queryForObject("select * from google_review_connection where restaurant_id=?"
                + (lock ? " for no key update" : ""), (rs, row) -> connection(rs, row), RESTAURANT);
    }

    private Connection connection(ResultSet rs, int row) throws SQLException {
        return new Connection(rs.getString("refresh_credential"), rs.getString("location_resource"),
                rs.getString("location_title"), rs.getString("location_address"), rs.getString("maps_url"),
                rs.getBoolean("authorization_expired"), rs.getObject("generation", UUID.class),
                instant(rs, "last_attempt"), instant(rs, "last_success"), rs.getString("sync_error"),
                rs.getObject("total_reviews", Integer.class), rs.getObject("overall_rating", Double.class),
                new Settings(rs.getBoolean("homepage_enabled"), rs.getInt("homepage_limit"), rs.getString("homepage_order")));
    }

    private OAuthAttempt oauth(ResultSet rs, int row) throws SQLException {
        return new OAuthAttempt(rs.getString("state_hash"), rs.getObject("staff_session_id", UUID.class),
                rs.getObject("generation", UUID.class), rs.getString("verifier"), rs.getString("pending_credential"), rs.getString("error"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime date = rs.getObject(column, OffsetDateTime.class);
        return date == null ? null : date.toInstant();
    }

    private static String locationId(Connection connection) {
        return digest(connection.location().substring(connection.location().lastIndexOf('/') + 1));
    }

    private static String random() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
