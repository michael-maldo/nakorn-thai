# Google Business Profile reviews

## Data flow and access

StaffPlatform uses the existing ADMIN session, bearer authentication, and restaurant CSRF endpoint. The Nakorn Thai backend models one restaurant through `restaurant_settings.id = 1`. Requests resolve that restaurant on the server. The integration accepts no client-supplied restaurant ID.

The backend creates a short-lived OAuth attempt bound to the initiating staff session and connection generation. It stores a hash of the state and an encrypted PKCE verifier. The callback consumes the state once and stages an encrypted refresh credential. The initiating administrator returns to StaffPlatform, confirms the connection, and selects a location discovered through that credential. A verified location is required for review access. Reconnection requires selecting the location again. Existing selections are keyed by location and review ID hashes.

Flyway V40 creates the connection and display settings record, OAuth attempts, and publication selections. Review content and owner replies are held in memory for at most two hours, normally 65 minutes at the default interval. Location display metadata is cached internally for up to roughly one day and provider-supplied totals for up to roughly two hours. The periodic cleanup removes expired metadata. The durable publication selection records are keyed by hashes of stable identifiers and include the most recent change time and staff actor. Google access tokens are used in memory and refreshed when needed.

The dedicated worker starts after boot and runs every 60 minutes by default. Set `GOOGLE_REVIEWS_SYNC_DELAY_MS` through Spring's environment binding or set `google.reviews.sync-delay-ms` directly. Requests paginate Google reviews in batches of 50 and replace the snapshot after all pages succeed. PostgreSQL advisory locking prevents overlap across application instances, and the connection generation prevents an old job from applying to a replaced connection. Deleted reviews disappear from staff lists and the feed after a successful refresh. Failed and expired grants are surfaced in Android. Public delivery uses the application instance's current snapshot. For this phase, serve the feature from a single application instance so Android and the public route see the same in-memory snapshot.

## Google Cloud and owner setup

1. Use an authorized Google Cloud project. Apply for Business Profile API basic access using the project number and a Google account listed as owner or manager. Google requires a verified, active profile and a representative business website. [Google prerequisites and application](https://developers.google.com/my-business/content/prereqs)
2. Once approved, enable My Business Account Management API, My Business Business Information API, and Google My Business API for reviews. Confirm the quota in Google Cloud. [Google API setup](https://developers.google.com/my-business/content/basic-setup) [Google quota limits](https://developers.google.com/my-business/content/limits)
3. Configure Google Auth Platform branding, audience, and data access for `https://www.googleapis.com/auth/business.manage`. Configure test users when using a test audience and complete any verification indicated by the console before production use. Create a **Web application** OAuth client. Register the exact HTTPS redirect URI that the backend will use, for example `https://staging.example.com/api/integrations/google/callback`. [Business Profile OAuth](https://developers.google.com/my-business/content/implement-oauth) [Web-server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server)
4. Provide these values through the backend's normal deployment secrets and configuration.

   | Setting | Value |
   | --- | --- |
   | `GOOGLE_BUSINESS_CLIENT_ID` | OAuth web client ID |
   | `GOOGLE_BUSINESS_CLIENT_SECRET` | OAuth web client secret |
   | `GOOGLE_BUSINESS_REDIRECT_URI` | Exact registered HTTPS callback |
   | `NAKORN_CREDENTIAL_MASTER_KEY` | Existing base64-encoded AES-256 credential key |
   | `GOOGLE_REVIEWS_PUBLIC_USE_APPROVED` | Defaults to `false`. Set to `true` only after confirmation that this publication use and attribution are permitted |
   | `GOOGLE_REVIEWS_ENABLED` | Defaults to `true`. Set to `false` to disable the admin integration and synchronization |
   | `GOOGLE_REVIEWS_SYNC_DELAY_MS` | Default `3600000` |

   Keep the key stable across restarts and use different keys in test and production. Configure any proxy or tracing infrastructure to omit callback query strings. Those query strings carry ephemeral credentials and state.
5. In StaffPlatform, open **Settings → Google Reviews**. Start authorization, accept consent in the browser, return to StaffPlatform, confirm the connection, and choose the verified Nakorn Thai location. The app will show the sync result.

Google restricts use of Business Profile API data to permitted Business Profile purposes and limits storage, reuse, and manipulation of that content. Obtain written confirmation for the proposed curated, unauthenticated website feed, including attribution and review display conditions. Ordinary API access approval enables staff management and is a separate prerequisite. The public feed defaults to disabled, while staff can prepare selections and previews. [Business Profile API policies](https://developers.google.com/my-business/content/policies)

## Connection diagnostics

The administrator status route returns `configured` and a `configurationStatus` code.
The code distinguishes disabled integration, missing OAuth values, malformed client
settings, an invalid redirect URI, and unavailable credential encryption. Readiness
checks the local settings. Google validates the client, registered redirect URI,
scope and API access during consent and provider calls.

Confirm the Android build points to the intended backend. On a backend with the
reviews implementation and its migration, an unauthenticated request to
`/api/public/google-reviews` returns HTTP 200, including when publication is disabled.
An HTTP 401 on this public route suggests a routing or deployment mismatch.
The staff status route requires an administrator bearer token.

## HTTP endpoints

All staff writes use the existing CSRF token at `GET /api/staff/restaurant/csrf`. Staff routes require ADMIN. The Google callback uses one-time state validation and the public feed requires no staff credentials.

| Method | Path |
| --- | --- |
| GET | `/api/staff/restaurant/integrations/google/status` |
| POST | `/api/staff/restaurant/integrations/google/connect` |
| GET | `/api/integrations/google/callback` |
| POST | `/api/staff/restaurant/integrations/google/complete` |
| GET | `/api/staff/restaurant/integrations/google/locations` |
| PUT | `/api/staff/restaurant/integrations/google/location` |
| POST | `/api/staff/restaurant/integrations/google/disconnect` |
| GET | `/api/staff/restaurant/google-reviews` |
| PATCH | `/api/staff/restaurant/google-reviews/{id}/publication` |
| GET, PUT | `/api/staff/restaurant/google-reviews/display-settings` |
| GET | `/api/staff/restaurant/google-reviews/preview` |
| POST | `/api/staff/restaurant/google-reviews/sync` |
| GET | `/api/public/google-reviews` |

The staff list accepts `query`, `publication` (`all`, `featured`, `unfeatured`), `stars` (0–5), `sort` (`newest`, `oldest`, `highest`, `lowest`), and a zero-based `page`. It returns 25 reviews per page and includes `total`, `featuredCount`, and `hasMore`. Publication accepts `{"featured":true}`. Display settings accept `{"enabled":false,"maximum":6,"ordering":"NEWEST_SELECTED"}`. `maximum` must be 3, 6 or 9, and ordering is `NEWEST_SELECTED` or `ROTATE` (daily UTC rotation).

The public endpoint sends `Cache-Control: no-store` and returns an empty list when disabled, disconnected, expired, or awaiting a usable snapshot. Its normal disabled response is:

```json
{"enabled":false,"curated":true,"reviews":[]}
```

When enabled, each element in `reviews` includes `reviewer`, `stars`, `comment`, `created` (ISO-8601), and `attribution` (Google). Display the returned attribution and identify the feed as curated. Use the restaurant site's existing same-origin API routing.

## Local verification

Run `mvn verify` with the repository's disposable PostgreSQL test bindings to exercise Flyway, staff access, OAuth staging, selection persistence, and the public response. Google HTTP responses are controlled test doubles. Verify live consent and approved location access in staging with an authorized owner before any rollout.
