# Order reference subjects and tracking links implementation report

Implemented together with the current uncommitted email-presentation refinements. No commit, push, deployment, production database access, production configuration change or live notification occurred.

## 1. Existing customer tracking architecture

Checkout generates a request UUID and cryptographically random 32-byte token encoded as 64 hex characters. Only the token's SHA-256 hash is stored in restaurant_order.tracking_hash. The original receipt stores the raw token in sessionStorage. GET /api/orders/{id} requires that token in X-Order-Token, or a valid recovery grant for that order. UUID knowledge alone returns 404. Public Spring Security routing permits the endpoint, but OrderAccessService independently enforces token authorization and GetOrderController uses no-store responses.

Existing routes are #/order-confirmation (receipt/current order) and #/track-order (recovery). OrderConfirmationPage retrieves the current persisted status and polls every five seconds until completion/cancellation. Its links previously led to a blank recovery form; no reusable emailed bearer-link generator existed.

Existing recovery endpoints are GET /api/order-verification/options, POST /api/order-verification/start and POST /api/order-verification/check. Start takes a UUID locator and an enabled sms/email channel, returns an indistinguishable challenge response for unknown orders, and sends a code only to the order's saved contact. Existing cooldown/hourly limits apply. Challenges last ten minutes and permit five attempts. Successful verification consumes the challenge and issues a random 64-hex-character token, storing only its hash in order_tracking_grant with a 24-hour expiry. OrderAccessService checks the grant's order ownership and expiry on every read. These APIs, authorization rules, provider integrations, tokens and lifetimes were not changed.

## 2. Existing and extended staff navigation

#/staff/orders previously displayed active queues or recent history, with existing ADMIN/FOH/BOH UI protection. ProtectedRoute renders LoginPage in place, retaining the hash; login does not redirect away from that location. Backend access requires JWT authentication with a valid database staff/session and existing role checks. Queue APIs are /api/staff/foh/orders and /api/staff/kitchen/orders. Queues are capped at 200 entries; FOH history covers terminal orders placed in the last 24 hours, so filtering a queue alone would not reliably open an older emailed order.

The smallest extension is #/staff/orders?order={UUID}, rendering the existing StaffOrderCard in a single-order mode. A new GET /api/staff/orders/{id} in the existing listorders slice uses the same repository and mapper. Existing /api/staff/orders/** security already restricts access to ADMIN/FOH/BOH. ADMIN/FOH can view retained orders independently of queue age/limits. BOH access retains kitchen visibility: only ACCEPTED/PREPARING/READY orders, and no customer name/phone. No new staff credential or customer tracking grant is used. The hash stays intact through login. AppRouter now retains the complete hash so switching IDs on the same route updates the selected order correctly.

## 3. Subjects

OrderEmailDetails.reference(UUID) shares the existing first-eight-hex-characters, uppercase reference logic with the formatter. There is no new numbering scheme.

- Nakorn Thai — Order #ABCD1234 — Received
- Nakorn Thai — Order #ABCD1234 — Accepted
- Nakorn Thai — Order #ABCD1234 — Ready for pickup
- Nakorn Thai — Order #ABCD1234 — Cancelled
- Nakorn Thai — New customer order #ABCD1234

SMS wording and its internal subject metadata remain unchanged.

## 4. Tracking links and security

Customer link: {trusted application base}/#/track-order?order={UUID}. It prefills the **locator**, then requires the existing SMS/email verification process. It is deliberately not a one-click bearer link: adding an email bearer grant without verification would weaken the existing access guarantees. No new customer tracking API or authorization mechanism was introduced. The link itself has no expiry while the order remains retained; the existing recovered grant still expires after 24 hours and can be renewed through verification. If no verification channel is configured, the existing UI directs the customer to their original receipt or the restaurant.

Invalid locator parameters are rejected gracefully by the frontend; unknown valid locators retain the backend's non-enumerating response behavior. An expired/invalid grant gives 404, clears stale displayed order details, stops repeated invalid-access polling, and offers a prefilled recovery link. Pending-checkout 404 retry behavior is preserved. Tests demonstrate a RECEIVED order later appearing READY after following the link and verifying, rather than showing an email-status snapshot.

Staff link: {trusted application base}/#/staff/orders?order={UUID}. The UUID selects the order but grants no privileges. Authentication is required before staff fetching; backend role checks enforce access independently of the UI. Staff calls use the existing bearer session, never X-Order-Token from a customer receipt. Unknown/role-inaccessible orders are handled without exposing details. Untrusted returnUrl parameters do not redirect navigation.

URL configuration is nakorn.public-app-url in application.yml, sourced from PUBLIC_APP_URL. If absent, it falls back to the existing PAYPAL_RETURN_URL setting and then that setting's established loopback development default. OrderEmailLinks removes callback fragments while preserving the application base path. It validates absolute HTTPS URLs, permits HTTP only for loopback development, and rejects userinfo, query-bearing bases, unsupported schemes and invalid ports. URL construction never reads Host/Forwarded headers or customer data other than the typed UUID. Explicit PUBLIC_APP_URL is recommended for production, independent of whether PayPal is enabled. Base changes are deployment settings and require restart; existing dashboard recipient/toggle runtime precedence remains unchanged. Captured jobs retain their original links after configuration changes.

## 5. HTML buttons and plain-text alternatives

Existing SMTP delivery used SimpleMailMessage and the queue stored only a plain body. Multipart is implemented with a small compatible extension: an optional EmailSender overload, nullable captured HTML body, and MimeMessageHelper with UTF-8 text/plain and text/html alternatives. Existing three-argument calls remain supported; its default overload falls back to plain text for other sender implementations.

OrderEmailHtml derives HTML from the same captured plain body, escapes notes/customer content, uses explicit line breaks, and adds an accessible labelled anchor styled as a button. Plain text retains the full labelled URL. No HTML-only delivery, external assets, scripts, tracking pixels or new dependencies were introduced. HTML and plain details/prices/links are consistent; the five generated HTML samples passed a 390px Chromium check with no horizontal overflow. Live provider/client rendering is intentionally unverified.

## 6. Files changed

The complete working-tree list below includes these changes and the earlier uncommitted presentation/payment regression refinements. Sample files are generated through the real application handler from fictional persisted snapshots.

- [backend/.env.dev.example](../../backend/.env.dev.example): documents the public URL setting for loopback development.
- [backend/.env.prod.example](../../backend/.env.prod.example): documents the HTTPS public URL setting; does not modify live configuration.
- [backend/src/main/java/au/com/nakornthai/notification/domain/EmailSender.java](../../backend/src/main/java/au/com/nakornthai/notification/domain/EmailSender.java): adds a backwards-compatible optional multipart send overload.
- [backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryJpaEntity.java): maps nullable captured htmlBody.
- [backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryWorker.java](../../backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryWorker.java): uses multipart for HTML jobs and the unchanged plain send path for legacy/reservation jobs.
- [backend/src/main/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSender.java](../../backend/src/main/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSender.java): sends UTF-8 multipart alternatives and preserves existing SimpleMailMessage calls.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java): shares reference formatting and retains presentation refinements.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java): adds reference subjects, audience-appropriate links and captured HTML alternatives.
- [backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersController.java](../../backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersController.java): adds the authenticated staff order read endpoint.
- [backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersHandler.java): reuses staff mapping/repository with kitchen visibility restrictions.
- [backend/src/main/resources/application.yml](../../backend/src/main/resources/application.yml): defines trusted public URL precedence using existing callback configuration.
- [backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java): checks multipart independent retries and historical plain-text job compatibility.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetailsTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetailsTest.java): covers presentation, optional fields, money, local pickup and cancellation details.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandlerTest.java): checks reference subjects, audience/content, SMS preservation and multipart retries.
- [backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java): checks UUID-without-token denial, current status, grant expiry and staff detail role enforcement.
- [frontend/package.json](../../frontend/package.json): includes locator tests and the new tracking browser command.
- [frontend/src/app/AppRouter.jsx](../../frontend/src/app/AppRouter.jsx): retains full hash and passes validated locators into existing customer/staff routes.
- [frontend/src/domains/ordering/api/orderApi.js](../../frontend/src/domains/ordering/api/orderApi.js): adds the staff detail read through the existing serialized identity wrapper.
- [frontend/src/domains/ordering/pages/OrderConfirmationPage.jsx](../../frontend/src/domains/ordering/pages/OrderConfirmationPage.jsx): clears expired-access details and offers safe recovery while preserving pending checkout retries.
- [frontend/src/domains/ordering/pages/OrderTrackingPage.jsx](../../frontend/src/domains/ordering/pages/OrderTrackingPage.jsx): prefills the validated locator and handles invalid links without changing verification.
- [frontend/src/domains/ordering/pages/OrdersAdminPage.jsx](../../frontend/src/domains/ordering/pages/OrdersAdminPage.jsx): reuses the existing card, polling and mutations in authenticated single-order mode.
- [frontend/tests/e2e/payments.browser.mjs](../../frontend/tests/e2e/payments.browser.mjs): retains the earlier canonical-phone cooldown test correction.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailHtml.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailHtml.java): escapes shared plain content and renders the HTML action.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinks.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinks.java): validates trusted application base configuration and builds existing hash-route links.
- [backend/src/main/resources/db/migration/V35__order_email_html_alternative.sql](../../backend/src/main/resources/db/migration/V35__order_email_html_alternative.sql): adds only nullable html_body to notification_delivery.
- [backend/src/test/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSenderTest.java](../../backend/src/test/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSenderTest.java): verifies UTF-8 MIME alternatives and old plain messages through a mock transport.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinksTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinksTest.java): checks trusted URL validation, routes, escaped HTML and matching action URLs.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java): generates five text/HTML sample pairs from persisted snapshots and asserts consistency.
- [docs/ordering/email-samples/README.md](email-samples/README.md): indexes regenerated samples and their integrity/security/verification findings.
- [docs/ordering/email-samples/customer-order-accepted.html](email-samples/customer-order-accepted.html): generated fictional HTML order email sample.
- [docs/ordering/email-samples/customer-order-accepted.txt](email-samples/customer-order-accepted.txt): generated fictional plain-text order email sample.
- [docs/ordering/email-samples/customer-order-cancelled.html](email-samples/customer-order-cancelled.html): generated fictional HTML order email sample.
- [docs/ordering/email-samples/customer-order-cancelled.txt](email-samples/customer-order-cancelled.txt): generated fictional plain-text order email sample.
- [docs/ordering/email-samples/customer-order-ready.html](email-samples/customer-order-ready.html): generated fictional HTML order email sample.
- [docs/ordering/email-samples/customer-order-ready.txt](email-samples/customer-order-ready.txt): generated fictional plain-text order email sample.
- [docs/ordering/email-samples/customer-order-received.html](email-samples/customer-order-received.html): generated fictional HTML order email sample.
- [docs/ordering/email-samples/customer-order-received.txt](email-samples/customer-order-received.txt): generated fictional plain-text order email sample.
- [docs/ordering/email-samples/staff-order-received.html](email-samples/staff-order-received.html): generated fictional HTML order email sample.
- [docs/ordering/email-samples/staff-order-received.txt](email-samples/staff-order-received.txt): generated fictional plain-text order email sample.
- [docs/ordering/order-email-tracking-links.md](order-email-tracking-links.md): this implementation/security/test/deployment report.
- [frontend/src/domains/ordering/model/orderLinks.js](../../frontend/src/domains/ordering/model/orderLinks.js): parses strict locator IDs and rejects invalid/duplicate IDs without processing redirects or credentials.
- [frontend/src/domains/ordering/model/orderLinks.test.js](../../frontend/src/domains/ordering/model/orderLinks.test.js): covers locator parsing and untrusted/duplicate input.
- [frontend/tests/e2e/orderEmailLinks.browser.mjs](../../frontend/tests/e2e/orderEmailLinks.browser.mjs): checks OTP-first access, live status, expired/invalid recovery, login retention and staff bearer-only reads.

## 7. Migration

**V35__order_email_html_alternative.sql** adds nullable `notification_delivery.html_body text`. No existing migration, event/channel/ownership constraint, uniqueness index, order table or grant table changed. Historical rows remain NULL and use the same plain-text delivery path. Reservation enqueueing still creates plain jobs. New order emails store both bodies in the existing transactional queue; recipient capture, independent customer/staff enablement and idempotency are unchanged. Jobs retain independent retry outcomes. No production migration was applied.

## 8. Tests and results

- Relevant initial backend unit pass: 15 passed, zero failures/errors/skips.
- Final **Java 21.0.12 Maven clean verify: 386 total, 386 passed, zero failures, errors or skips**, executable JAR built.
- Isolated **PostgreSQL 16.14** cluster under /tmp/nakorn-links-pg.PB9dAj/data, bound only to loopback port 55436. New disposable nakorn_links_verify database; no existing development/production database was used. First complete run applied Flyway V1–V35 from an empty schema; subsequent final run validated all 35 migrations.
- Frontend npm test: **160 passed**, zero failures/skips. npm run build passed.
- New orderEmailLinks Chromium suite: passed (verification-first locator, live READY status, expired and invalid recovery, staff login, retained deep link, staff bearer-only requests and switching IDs on the same route).
- All eight existing Chromium suites passed: phone, reservation verification, Admin settings, payments, feature availability, homepage, menu administration and menu navigation.
- All five actual HTML samples passed a local Chromium 390px layout check: no horizontal overflow, preserved total, accessible action labels and plain/HTML URL parity. A received-email screenshot was visually inspected locally.
- git diff --check passed.

Delivery tests and sample generation use mocked EmailSender, SmsSender, diagnostics and scheduling; MIME tests use a mocked JavaMailSenderImpl. No live SMTP/OTP notification was sent. The tracking integration test exercises the real existing recovery endpoints with a mocked verification provider, then proves expiry via the PostgreSQL grant and protected order read.

Intermediate test-harness issues were corrected: the browser initially counted options/CSRF requests as order reads and used a hidden login heading; selectors were narrowed to real order reads and the visible login form. A line-break assertion initially used a single-line escaping fixture; the fixture was made multiline. The final complete suites passed; no unresolved test/application failure remains.

Final backend command, from repository root:

```bash
env -u STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED -u STAFF_ORDER_EMAIL_ADDRESS \
 DB_TEST_URL=jdbc:postgresql://127.0.0.1:55436/nakorn_links_verify \
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

Frontend commands: `npm test`, `npm run build`; browser scripts use the isolated Vite instance on 127.0.0.1:5175 and Chromium debugging on 127.0.0.1:9224, with ORDER_LINKS_TEST_URL and the existing suite-specific URL variables pointed at that local instance. All application requests are mocked.

## 9. Deployment compatibility and risks

Apply V35 before running the new entity/worker code; the established application startup Flyway sequence does this before JPA validation and worker initialization. The column is additive and nullable. Old workers that understand the existing event types can ignore HTML and deliver the captured plain alternative; historical jobs retain their old subject/body and are not rewritten or backfilled. Existing V34 staff-event rollout precautions remain relevant when reverting to code that predates STAFF_ORDER_RECEIVED.

Deploy frontend routing updates alongside the new backend/link generation. Older frontends can reach the existing tracking/queue pages but do not prefill/select the new query locator. Review PUBLIC_APP_URL or its PAYPAL_RETURN_URL fallback for the actual HTTPS application base, including any path prefix. The validated setting is fixed at startup; changing it requires restart and affects new jobs, not captured historical links. Keep old application origins working for already sent links if changing domains.

Customer email delivery remains independent from OTP provider availability. The link never bypasses verification; at least one configured recovery channel or the customer's existing receipt is needed to view private order data. Existing UI handles unavailable channels and invalid/expired access. Staff links require an enabled staff account/session and an appropriate role.

Live relay acceptance, inbox delivery and Gmail/Outlook/Apple Mail-specific rendering were not tested. Plain-text alternatives and explicit HTML line breaks provide fallbacks. SMTP acceptance before a database outcome commit retains the existing possible resend caveat; this change does not promise exactly-once transport delivery. No provider credentials or production configuration were changed.

## 10. Complete git status

This includes the pre-existing uncommitted email-presentation refinements and sample artifacts. No unrelated changes were introduced. Nothing was committed, pushed or deployed.

```text
 M backend/.env.dev.example
 M backend/.env.prod.example
 M backend/src/main/java/au/com/nakornthai/notification/domain/EmailSender.java
 M backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryJpaEntity.java
 M backend/src/main/java/au/com/nakornthai/notification/infrastructure/NotificationDeliveryWorker.java
 M backend/src/main/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSender.java
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java
 M backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersController.java
 M backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersHandler.java
 M backend/src/main/resources/application.yml
 M backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java
 M backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetailsTest.java
 M backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandlerTest.java
 M backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java
 M frontend/package.json
 M frontend/src/app/AppRouter.jsx
 M frontend/src/domains/ordering/api/orderApi.js
 M frontend/src/domains/ordering/pages/OrderConfirmationPage.jsx
 M frontend/src/domains/ordering/pages/OrderTrackingPage.jsx
 M frontend/src/domains/ordering/pages/OrdersAdminPage.jsx
 M frontend/tests/e2e/payments.browser.mjs
?? backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailHtml.java
?? backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinks.java
?? backend/src/main/resources/db/migration/V35__order_email_html_alternative.sql
?? backend/src/test/java/au/com/nakornthai/notification/infrastructure/email/SmtpEmailSenderTest.java
?? backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailLinksTest.java
?? backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java
?? docs/ordering/email-samples/README.md
?? docs/ordering/email-samples/customer-order-accepted.html
?? docs/ordering/email-samples/customer-order-accepted.txt
?? docs/ordering/email-samples/customer-order-cancelled.html
?? docs/ordering/email-samples/customer-order-cancelled.txt
?? docs/ordering/email-samples/customer-order-ready.html
?? docs/ordering/email-samples/customer-order-ready.txt
?? docs/ordering/email-samples/customer-order-received.html
?? docs/ordering/email-samples/customer-order-received.txt
?? docs/ordering/email-samples/staff-order-received.html
?? docs/ordering/email-samples/staff-order-received.txt
?? docs/ordering/order-email-tracking-links.md
?? frontend/src/domains/ordering/model/orderLinks.js
?? frontend/src/domains/ordering/model/orderLinks.test.js
?? frontend/tests/e2e/orderEmailLinks.browser.mjs
```
