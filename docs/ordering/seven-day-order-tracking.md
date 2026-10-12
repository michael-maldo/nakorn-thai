# Business website and seven-day read-only order tracking

Implemented and verified locally on 13 October 2026. No commit, push, deployment, production database access, production configuration change, or real notification delivery was performed. The working tree was clean before this task; all listed changes belong to this feature.

## Architecture and configuration

Business website URL is an ADMIN-only SETTINGS field (`businessWebsiteUrl`) stored in the existing `restaurant_settings.operational_configuration` JSON. The configuration API, version checks, transactional settings lock, audit field-name records and runtime database overrides are reused. React groups it under Business identity and renders a URL input with explanatory help. It is not a boolean or a credential.

`BusinessWebsiteUrl` validates/normalizes scheme, host, port and base path. It rejects credentials, query strings, fragments, malformed hosts, unsafe schemes, ambiguous path normalization and encoded paths. HTTPS is required in production. HTTP is limited to loopback in development/test (prod profile always prohibits it). Configuration saves are validated before persistence. Empty website values deliberately restore the deployment fallback.

Links resolve the dashboard website first, then `PUBLIC_APP_URL` (through `nakorn.public-app-url`); localhost is the final development default only. URLs come exclusively from trusted administrator/deployment configuration. Query parameters and Host headers cannot supply destinations. Administrators must use the owned origin/path actually serving this application, not an unrelated marketing or external site. Changing the setting takes effect for subsequent jobs without restart. Existing captured jobs are never rerendered.

`PAYPAL_RETURN_URL` is explicitly a provider callback to `#/order-confirmation`, used by the payment return/cancel flow and PayPal configuration validation. It is no longer an email-link fallback. Existing payment configuration and semantics remain unchanged. Deployments previously depending on that fallback must explicitly set PUBLIC_APP_URL before replacing the application, or set Business website URL once the new settings UI is available.

## Tracking capability and lifecycle

`OrderViewTokens` generates 32 random bytes with Java SecureRandom and hex encodes them (256-bit opaque credentials). V36 adds a separate `order_view_token` table with exactly one row per order, a unique SHA-256 token hash, issued/expiry/revocation instants, and encrypted token material. The encrypted material is needed to reuse exactly the same token in later event emails; hash-only storage could not reconstruct it. Encryption reuses the existing AES-256-GCM CredentialCipher and NAKORN_CREDENTIAL_MASTER_KEY, with order-specific authenticated context `order-view:<order UUID>`. Plaintext tokens are already necessarily present in captured email bodies; database/queue backups remain sensitive.

Issuance happens during the first trusted customer-email enqueueing transaction, not during anonymous tracking requests. The persisted order is locked before selecting/creating the capability, serializing different notification events. Unique order/hash constraints provide the durable backstop. No token is needed for staff-only or email-less orders. Legacy orders may receive a capability on a legitimate later customer notification; anonymous visitors cannot create/reissue one. Historical queued messages keep their original verified-recovery URL and body.

Expiry is exactly 168 hours from first issuance, enforced by both a database constraint and every server access using the application Clock. Opening links, later event emails and worker retries neither rotate the token nor extend expiry. Expired/revoked rows are retained as tombstones; later emails use existing verified recovery rather than minting another capability. No automatic cleanup job deletes these rows. Order deletion cascades to the row; any future retention cleanup must preserve tombstones while an order can still generate notifications.

`POST /api/staff/orders/{id}/tracking/revoke` allows ADMIN/FOH with existing staff authentication and CSRF protection to revoke this capability only; BOH/guests cannot revoke. There is no new token-issuance endpoint or token-management UI. Original receipt and 24-hour OTP recovery credentials remain independent.

If the existing encryption key is unavailable before issuance, a generic diagnostic and dashboard explanation identify the fallback to verified recovery. No plaintext token is stored as a weaker substitute, and existing order creation/notifications remain usable. One-click behavior therefore requires that deployment prerequisite. Losing/changing the key prevents reconstructing existing token URLs for later emails and fails closed; already captured emails and hash-based reads do not require decryption. Key rotation needs a deliberate re-encryption procedure; it is not automatic.

## Public endpoint and privacy

The email link is `#/track-order?t=<opaque token>`. The hash is not transmitted in HTTP request URLs or server access logs. React sends the credential only in `X-Order-View-Token` to `GET /api/order-tracking`, without cookies or staff identity headers, using no-store and no-referrer fetch options. The application also sets a document no-referrer policy. There is no third-party tracking/analytics loaded by this page. Existing structured request logs use route patterns and omit headers, bodies, query strings and exception messages.

The server hashes the token and resolves exactly one order; callers supply no order ID selector. There is no plaintext token equality comparison in application code. The token hash is used for indexed lookup, rather than exposing a partial token comparison oracle. Invalid, expired, revoked and missing credentials all produce the same generic 404 response. Tracking responses use Cache-Control: no-store; Spring Security also applies its default cache prevention to errors.

The dedicated DTO contains only order reference, current status, estimated pickup instant, last update, restaurant timezone, fulfilment, AUD total/currency, customer-authored instructions and historical item/variation/quantity/unit-price/line-total/option-name/option-quantity/option-adjustment snapshots. It does not serialize JPA entities or staff/receipt responses. It excludes customer name, phone, email/address, provider/payment data, paidAt, internal staff cancellation reasons, order UUIDs, option/variation IDs, hashes and credentials.

Current snapshots persist only order-level customer instructions. No item-specific note field exists in checkout or order persistence, so the page displays available instructions without inventing per-item notes. Supporting new per-item notes would be a separate checkout/persistence enhancement.

Bearer links remain sensitive because instructions can contain personal information. Browser history/extensions and mail systems can see the link; no-referrer and hash transport cannot protect against a compromised device/mailbox. Tokens are intentionally retained in the fragment to support reopening/reloading/cross-device access without requiring storage or cookies. They are never used by OrderAccessService, payments, mutations, or staff authorization.

## Customer and staff UI/email compatibility

The new read-only page shows progress, terminal-state messages, pickup location/estimate, restaurant-local last update, and a mobile-friendly itemized Your order section. Unit prices explicitly include options; option quantities/adjustments are per item. Active orders poll every five seconds, terminal orders every sixty seconds (so revocation/expiry can still clear displayed data). Invalid/expired access removes the summary and stops polling; temporary outages show a friendly error and retry after ten seconds. Cancelled/completed orders do not show an obsolete pickup estimate.

`#/recover-order` exposes existing OTP recovery separately. Historical `#/track-order?order=<UUID>` links still open that flow. The old recovery backend supports both SMS and email, but each is gated by provider/configuration. The selector correctly reflects `/api/order-verification/options`; wording now names only enabled channels. Verification/security/provider semantics are unchanged.

Staff links remain `#/staff/orders?order=<UUID>` and continue through ProtectedRoute, in-place login and backend staff role/session authorization. Customer capabilities cannot bypass staff authentication. All existing subject references/event wording and itemized content remain. HTML and plain text capture the same resolved URL, explicitly passed to the HTML renderer; multipart worker/SMTP handling and reservation delivery are unchanged. Staff recipient/URL and customer URLs are captured at enqueue time and remain stable across configuration changes and delivery retries.

## Migration and rollout

V36__read_only_order_tracking.sql is forward-only; V1–V35 were not edited. It adds an order-owned table, order primary key, unique hash constraint, 168-hour expiry check, revocation check and expiry index. Existing notification constraints, purpose-specific uniqueness, ownership and historical rows are untouched. Fresh PostgreSQL verification applies migrations from the beginning through V36.

The inspected GitHub workflow uploads/restarts the backend through infrastructure/scripts/activate-backend.sh, then replaces frontend assets. Backend startup runs Flyway before serving requests; activation health checks only prove process health. Safe prerequisites/sequencing (not performed): ensure trusted HTTPS PUBLIC_APP_URL and the stable credential key; apply V36 before new backend requests/worker activity (startup Flyway does this); replace backend/worker and matching frontend; verify configuration, public tracking expiry/privacy and protected staff routes without live sends; explicitly enable notifications through existing ADMIN controls if desired. Existing staff notification default remains disabled and this feature does not enable any notification flag.

An older frontend cannot handle new `t=` links, so backend/frontend activation should be coordinated before creating new customer messages. Additive V36 may be retained on rollback; do not undo/delete grants or edit prior migrations. Old backend code ignores the new table. Existing bearer emails require the new frontend/backend endpoint to remain available for their intended seven-day lifecycle; rolling back those routes interrupts their use. Flyway target/version compatibility should be checked for the chosen rollback binary; the activation script's health rollback does not migrate schema backward.

## Verification results

Final command/results and complete status are recorded below after verification finishes. Tests use only isolated PostgreSQL 16.14 on loopback port 55437, dedicated databases and fictional contacts. Notification delivery providers are mocked; local-only SMTP safeguards point to 127.0.0.1:1. Disposable services are stopped afterward.

Corrections during verification: the browser initially assumed all sessionStorage was empty, but existing cart state is legitimate; the assertion now checks only that no tracking token is persisted. The new worker retry test initially expected PENDING after failure; existing semantics are FAILED plus nextAttemptAt, and the test was corrected without changing delivery code. Existing availability tests now use the explicit verified-recovery route. Existing admin browser fixtures/assertions include Business identity.

Remaining limitations: real email-client rendering/delivery is intentionally untested; one-click links require the deployment encryption key; per-item notes are not presently persisted; encrypted tokens and captured email bodies require protected backups; bearer-link exposure through mailbox/device/history/extensions cannot be eliminated. Public endpoint traffic limiting and a staff revocation UI are optional follow-ups, not new security dependencies. No unresolved authorization defect was found in the exercised flows.

## Final commands and results

Backend (repository root; fresh dedicated database, Java 21):

```sh
env -u STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED -u STAFF_ORDER_EMAIL_ADDRESS \
 DB_TEST_URL=jdbc:postgresql://127.0.0.1:55437/nakorn_tracking_release \
 DB_TEST_USERNAME=nakorn_test DB_TEST_PASSWORD=disposable-only \
 JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk \
 PATH=/usr/lib/jvm/java-21-temurin-jdk/bin:$PATH \
 SMTP_HOST=127.0.0.1 SMTP_PORT=1 SMTP_FROM=test@example.test \
 NOTIFICATION_POLL_MS=3600000 \
 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2 \
 SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
 mvn --batch-mode --no-transfer-progress -f backend/pom.xml clean verify \
 -Dorder.email.samples.directory=/home/gnostex/Documents/nakorn/application/nakorn-thai/docs/ordering/email-samples
```

Result: **396 passed, 0 failed, 0 errors, 0 skipped**; executable JAR built. Surefire XML totals independently confirmed. Fresh Flyway history: 36 rows, 36 successful; V36 successful. PostgreSQL 16.14. Constraints verified in pg_constraint: order ownership/cascade, unique order/hash, hash shape, 168-hour expiry, valid revocation instant. Existing V34 notification/reservation constraints and V35 multipart/history compatibility remain covered by the complete integration suite. Log: `/tmp/nakorn-seven-day-release.log`.

Frontend (frontend directory): `npm test` → **163 passed, 0 failed, 0 skipped**; `npm run build` → **passed**. Logs: `/tmp/nakorn-seven-day-frontend-final.log`, `/tmp/nakorn-seven-day-build-final.log`. Existing npm environment/deprecation warnings did not affect these checks.

Chromium (repository root; local Vite 127.0.0.1:5175, remote debugging 127.0.0.1:9224; API requests mocked):

```sh
export CHROME_DEBUG_URL=http://127.0.0.1:9224
export ORDER_LINKS_TEST_URL=http://127.0.0.1:5175/
export RESERVATION_TEST_URL=http://127.0.0.1:5175/
export SETTINGS_TEST_URL=http://127.0.0.1:5175/
export PAYMENT_TEST_URL=http://127.0.0.1:5175/
export AVAILABILITY_TEST_URL=http://127.0.0.1:5175/
export HOMEPAGE_TEST_URL=http://127.0.0.1:5175/
export MENU_ADMIN_TEST_URL=http://127.0.0.1:5175/
export MENU_NAVIGATION_TEST_URL=http://127.0.0.1:5175/
for suite in australianPhone reservationVerification adminSettings payments featureAvailability homepageMenu menuAdmin menuNavigation orderEmailLinks; do
 node frontend/tests/e2e/$suite.browser.mjs || exit 1
done
node frontend/tests/e2e/orderEmailSamples.browser.mjs
```

All **10 browser suites passed**. The tracking suite checks no OTP calls, dedicated token header, live READY status polling, item/options arithmetic, no horizontal overflow at 390px, expiry clearing, reopening without stored tokens, invalid tokens, legacy OTP recovery, staff login/deep-link retention and bearer-only staff calls. The sample suite confirms five HTML files, matching text/HTML URLs, one identical customer token, AUD 99.20 total and no overflow at 390px. Email-client-specific rendering remains untested.

All five sample pairs were regenerated by actual persisted/reloaded application rendering (fixture transaction rolled back):

- [Customer received text](email-samples/customer-order-received.txt) / [HTML](email-samples/customer-order-received.html)
- [Customer accepted text](email-samples/customer-order-accepted.txt) / [HTML](email-samples/customer-order-accepted.html)
- [Customer ready text](email-samples/customer-order-ready.txt) / [HTML](email-samples/customer-order-ready.html)
- [Customer cancelled text](email-samples/customer-order-cancelled.txt) / [HTML](email-samples/customer-order-cancelled.html)
- [Staff received text](email-samples/staff-order-received.txt) / [HTML](email-samples/staff-order-received.html)

Staff content is unchanged, so an identically regenerated staff file need not appear in git status. Received retains pending acceptance/payment wording; accepted retains full local pickup/paid information; ready retains pickup address; cancelled retains its refund caveat without obsolete pickup time; staff retains operational contact details and protected View order link.

`git diff --check` passed. Local Vite, Chromium and the disposable PostgreSQL cluster were stopped. No production service was touched.

## Files changed and purpose

| File | Purpose |
| --- | --- |
| `backend/.env.dev.example` | Document independent public base and existing encryption-key prerequisite; no live configuration edits. |
| `backend/.env.prod.example` | Document independent public base and existing encryption-key prerequisite; no live configuration edits. |
| `backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinks.java` | Dynamic trusted base resolution; opaque customer links and existing protected staff links. |
| `backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java` | Stable token acquisition and captured matching plain/HTML action URL; verified recovery fallback. |
| `backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java` | Business website SETTINGS field, excluded from boolean fields. |
| `backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java` | Validate and normalize website before existing transactional save. |
| `backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/DatabaseRuntimeConfiguration.java` | Environment-aware loopback URL validation policy. |
| `backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java` | Public read-only endpoint and restricted CSRF-protected revocation. |
| `backend/src/main/resources/application.yml` | Independent PUBLIC_APP_URL fallback; remove payment callback coupling. |
| `backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinksTest.java` | Updated base validation expectations. |
| `backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java` | Real rendering/sample generation, token lifecycle/privacy/access scope, captured configuration and worker retries. |
| `backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java` | Website persistence/normalization, ADMIN access and rejection tests. |
| `docs/ordering/email-samples/README.md` | Update sample review/regen documentation to private one-click links. |
| `docs/ordering/email-samples/customer-order-accepted.html` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-accepted.txt` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-cancelled.html` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-cancelled.txt` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-ready.html` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-ready.txt` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-received.html` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/customer-order-received.txt` | Regenerated fictional customer email with stable seven-day read-only link, unchanged snapshot content. |
| `docs/ordering/email-samples/staff-order-received.html` | Regenerated fictional staff email from actual rendering; authenticated staff link and historical details preserved. |
| `docs/ordering/email-samples/staff-order-received.txt` | Regenerated fictional staff email from actual rendering; authenticated staff link and historical details preserved. |
| `docs/ordering/order-email-tracking-links.md` | Mark earlier verification-first report as superseded. |
| `frontend/index.html` | No-referrer document policy. |
| `frontend/package.json` | Add reusable email-samples browser script. |
| `frontend/src/app/AppRouter.jsx` | Read-only token route, explicit recovery route and historical locator compatibility. |
| `frontend/src/domains/ordering/api/orderApi.js` | Credential-free read-only fetch with dedicated header/no-store/no-referrer. |
| `frontend/src/domains/ordering/api/orderApi.test.js` | Header, no cookies/cache, and safe error contracts. |
| `frontend/src/domains/ordering/model/orderLinks.js` | Strict single opaque token parsing. |
| `frontend/src/domains/ordering/model/orderLinks.test.js` | Malformed/missing/duplicate token rejection. |
| `frontend/src/domains/ordering/pages/OrderTrackingPage.jsx` | Recovery wording reflects enabled verification channels. |
| `frontend/src/domains/restaurant/model/integrationSettings.js` | Website field metadata and text classification. |
| `frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx` | Business identity URL field, fallback help and encryption prerequisite notice. |
| `frontend/src/styles/responsive.css` | Wrapping progress/item/instruction mobile layout. |
| `frontend/tests/e2e/adminSettings.browser.mjs` | Website input and Business identity regression assertions. |
| `frontend/tests/e2e/featureAvailability.browser.mjs` | Verified recovery checks use explicit recovery route. |
| `frontend/tests/e2e/orderEmailLinks.browser.mjs` | One-click/private mobile summary, polling, expiry, reuse and existing OTP/staff checks. |
| `backend/src/main/java/au/com/nakornthai/ordering/trackorder/OrderViewTokens.java` | Random hashed/encrypted stable capability issuance, read scope, expiry and independent revocation. |
| `backend/src/main/java/au/com/nakornthai/ordering/trackorder/TrackOrderController.java` | Dedicated read-only endpoint and authenticated staff revocation endpoint. |
| `backend/src/main/java/au/com/nakornthai/ordering/trackorder/TrackOrderHandler.java` | Minimal private-safe historical snapshot DTO and current progress. |
| `backend/src/main/java/au/com/nakornthai/restaurant/configuration/BusinessWebsiteUrl.java` | Shared strict URL normalization for settings and link generation. |
| `backend/src/main/resources/db/migration/V36__read_only_order_tracking.sql` | Add separate order-owned capability storage and constraints. |
| `backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/BusinessWebsiteUrlTest.java` | Unsafe URL/production HTTP rejection, dev loopback and precedence tests. |
| `docs/ordering/seven-day-order-tracking.md` | Architecture, lifecycle, privacy, rollout, tests and full review report. |
| `frontend/src/domains/ordering/pages/ReadOnlyOrderTrackingPage.jsx` | Polling progress and historical item summary without identity, OTP or stored credentials. |
| `frontend/tests/e2e/orderEmailSamples.browser.mjs` | Reusable five-sample mobile/subject/URL parity verification. |

## Complete git status

No unrelated changes were present before this task or introduced. Nothing is staged or committed by this work.

```text
 M backend/.env.dev.example
 M backend/.env.prod.example
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinks.java
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java
 M backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/DatabaseRuntimeConfiguration.java
 M backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java
 M backend/src/main/resources/application.yml
 M backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinksTest.java
 M backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java
 M backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java
 M docs/ordering/email-samples/README.md
 M docs/ordering/email-samples/customer-order-accepted.html
 M docs/ordering/email-samples/customer-order-accepted.txt
 M docs/ordering/email-samples/customer-order-cancelled.html
 M docs/ordering/email-samples/customer-order-cancelled.txt
 M docs/ordering/email-samples/customer-order-ready.html
 M docs/ordering/email-samples/customer-order-ready.txt
 M docs/ordering/email-samples/customer-order-received.html
 M docs/ordering/email-samples/customer-order-received.txt
 M docs/ordering/email-samples/staff-order-received.html
 M docs/ordering/email-samples/staff-order-received.txt
 M docs/ordering/order-email-tracking-links.md
 M frontend/index.html
 M frontend/package.json
 M frontend/src/app/AppRouter.jsx
 M frontend/src/domains/ordering/api/orderApi.js
 M frontend/src/domains/ordering/api/orderApi.test.js
 M frontend/src/domains/ordering/model/orderLinks.js
 M frontend/src/domains/ordering/model/orderLinks.test.js
 M frontend/src/domains/ordering/pages/OrderTrackingPage.jsx
 M frontend/src/domains/restaurant/model/integrationSettings.js
 M frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx
 M frontend/src/styles/responsive.css
 M frontend/tests/e2e/adminSettings.browser.mjs
 M frontend/tests/e2e/featureAvailability.browser.mjs
 M frontend/tests/e2e/orderEmailLinks.browser.mjs
?? backend/src/main/java/au/com/nakornthai/ordering/trackorder/OrderViewTokens.java
?? backend/src/main/java/au/com/nakornthai/ordering/trackorder/TrackOrderController.java
?? backend/src/main/java/au/com/nakornthai/ordering/trackorder/TrackOrderHandler.java
?? backend/src/main/java/au/com/nakornthai/restaurant/configuration/BusinessWebsiteUrl.java
?? backend/src/main/resources/db/migration/V36__read_only_order_tracking.sql
?? backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/BusinessWebsiteUrlTest.java
?? docs/ordering/seven-day-order-tracking.md
?? frontend/src/domains/ordering/pages/ReadOnlyOrderTrackingPage.jsx
?? frontend/tests/e2e/orderEmailSamples.browser.mjs
```

Diff summary: 38 tracked files changed, 227 insertions and 74 deletions, plus 9 new untracked files listed above (the diff statistics exclude untracked files). Sample option ordering can differ between fixture runs because the existing persistence sorts selected options by generated UUID; values and quantities remain identical.
