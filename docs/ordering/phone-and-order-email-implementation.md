# Phone input and order email implementation report

## A. Implementation summary

Checkout and table requests now share an Australian mobile input. It accepts `04…`, `4…`, `+61…` and Australian international numbers pasted with spacing, parentheses or hyphens. A complete number displays as `+61 434 778 435` on blur. Raw editing text stays in place while focused, preserving cursor edits and deletion. It uses `type="tel"`, `inputMode="tel"`, autocomplete, accessible hints/error feedback and form validity. Invalid and international numbers are not silently converted into Australian mobiles. The generic verification input, staff workflows and function enquiry phone input retain their broader existing behavior.

Customer order emails for ORDER_RECEIVED, ORDER_ACCEPTED, ORDER_READY and ORDER_CANCELLED retain their subjects and event wording, with a shared itemized plain-text details section. It includes the existing short reference, restaurant-local creation time, customer name, fulfilment, payment method/recorded paid status, item names/variations, quantities, snapshot unit prices, options and option quantities/deltas, line totals, stored total/currency and notes. Existing pickup/status wording remains.

Staff new-order email notifications have an independent toggle and recipient under Admin → Settings and integrations → Business settings → Order notifications. They queue STAFF_ORDER_RECEIVED email jobs through the existing durable worker, without synchronously sending SMTP from the order request. Staff bodies also include available customer contact details. Staff notifications default to disabled.

## B. Files changed

- [backend/.env.dev.example](../../backend/.env.dev.example): Documents safe development defaults for independent staff emails.
- [backend/.env.prod.example](../../backend/.env.prod.example): Documents safe production fallback defaults; does not change live configuration.
- [backend/src/main/java/au/com/nakornthai/notification/domain/NotificationType.java](../../backend/src/main/java/au/com/nakornthai/notification/domain/NotificationType.java): Adds the independent STAFF_ORDER_RECEIVED notification purpose.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java): Shared plain-text rendering of authoritative historical order snapshots.
- [backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java): Adds itemized customer emails and independently queues staff jobs in the existing transaction.
- [backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java](../../backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java): Registers staff settings, environment fallbacks and disabled-by-default enablement.
- [backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java](../../backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java): Validates recipient and SMTP readiness; accounts for staff-only SMTP use in integration metadata.
- [backend/src/main/resources/db/migration/V34__staff_order_email_notifications.sql](../../backend/src/main/resources/db/migration/V34__staff_order_email_notifications.sql): Forward-only extension of notification event/ownership checks and an email-only staff check.
- [backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java): Adds database uniqueness, recipient capture and duplicate queue attempt coverage.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetailsTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetailsTest.java): Covers item snapshots, quantities, option deltas, totals, local time, notes, optional values and excluded secrets.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandlerTest.java): Covers four enablement combinations, event-specific content, missing customer email, idempotency and independent worker retries.
- [backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/StaffOrderConfigurationTest.java](../../backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/StaffOrderConfigurationTest.java): Covers safe defaults, environment/dashboard precedence, immediate changes and enabling validation.
- [backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java): Adds Admin configuration persistence, recipient syntax and enabled-recipient validation coverage.
- [frontend/package.json](../../frontend/package.json): Includes phone unit tests in npm test and adds a phone browser test command.
- [frontend/src/shared/utils/australianPhone.js](../../frontend/src/shared/utils/australianPhone.js): Separates Australian mobile display formatting, canonical payload conversion and validation.
- [frontend/src/shared/utils/australianPhone.test.js](../../frontend/src/shared/utils/australianPhone.test.js): Tests entry/paste/partial/delete cases, invalid numbers, verification retention and unchanged normalized request bodies/retries.
- [frontend/src/shared/components/AustralianMobileInput.jsx](../../frontend/src/shared/components/AustralianMobileInput.jsx): Reusable editable tel input with blur formatting, accessible feedback and browser validation.
- [frontend/src/domains/notification/components/ContactVerification.jsx](../../frontend/src/domains/notification/components/ContactVerification.jsx): Opt-in Australian mobile input and valid-mobile gating for sending verification codes.
- [frontend/src/domains/ordering/pages/CheckoutPage.jsx](../../frontend/src/domains/ordering/pages/CheckoutPage.jsx): Uses the reusable input for verified and unverified mobile entry.
- [frontend/src/domains/reservation/pages/ReservationPage.jsx](../../frontend/src/domains/reservation/pages/ReservationPage.jsx): Uses the same input for table requests with or without required verification.
- [frontend/src/domains/restaurant/model/integrationSettings.js](../../frontend/src/domains/restaurant/model/integrationSettings.js): Defines independent staff fields and recipient/SMTP dependency warnings.
- [frontend/src/domains/restaurant/model/integrationSettings.test.js](../../frontend/src/domains/restaurant/model/integrationSettings.test.js): Checks recipient text-field semantics and independent enablement warnings.
- [frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx](../../frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx): Adds staff toggle and conditional required email input under Business settings → Order notifications.
- [frontend/tests/e2e/australianPhone.browser.mjs](../../frontend/tests/e2e/australianPhone.browser.mjs): Chromium editing, paste, deletion, partial validation and verification payload regression with mocked APIs.
- [frontend/tests/e2e/adminSettings.browser.mjs](../../frontend/tests/e2e/adminSettings.browser.mjs): Updates mocked settings/group assertions and checks the new recipient and toggle controls.
- [frontend/tests/e2e/reservationVerification.browser.mjs](../../frontend/tests/e2e/reservationVerification.browser.mjs): Verifies the normalized Australian mobile payload while retaining existing verification/retry regressions.
- [docs/ordering/phone-and-order-email-implementation.md](phone-and-order-email-implementation.md): This implementation and review report.

## C. Architectural decisions

Phone display and payload are separate. The input holds its editing draft locally and updates the existing verification destination with a compact canonical Australian number (`+614…`). Backend inspection confirmed ContactDestination already accepts Australian national formats and E.164, normalizes Australian mobiles to `+614…`, and matches verification using that normalized destination. Backend DTOs, validation, normalization, persistence and providers are untouched. Formatting-only edits retain verification when the canonical destination is unchanged; digit changes invalidate it. Checkout request shape and CSRF/cookie handling are unchanged. Saved pending checkout payloads continue to retry exactly as stored.

The email handler loads the persisted OrderJpaEntity inside its existing mandatory business transaction. Creation already persists and flushes item/option snapshots before invoking it. The shared formatter uses only those snapshots, including historical names, selected option data and authoritative unit prices. Line totals are snapshot unit price × ordered quantity; the overall total is the stored order total. Money uses BigDecimal minor-unit conversion with two decimal places. No current menu reads or repricing occur. Payment wording says “Not recorded as paid” when paidAt is absent, without claiming a provider-specific status that is not available. Generated emails exclude full database UUIDs, request/tracking hashes and verification credentials.

Customer enablement uses orderEmail plus customer email presence. Staff enablement uses staffOrderEmail only for the ORDER_RECEIVED creation event and staffOrderEmailAddress as recipient. Customer email and verified phone presence are not staff prerequisites. Both enabled queues two distinct emails; either enabled queues only its own email; both disabled queues neither email. Existing orderSms behavior is retained separately. The creation handler returns before notifications on an idempotent replay, so existing orders are not backfilled.

Each queue job retains its own recipient, body, status, attempt count and retry schedule. The worker, SMTP sender, polling, retries and reservation enqueueing are unchanged. Existing structured queued/sent/failed logs and metrics carry the distinct STAFF_ORDER_RECEIVED type, without logging recipients or message bodies.

Runtime settings use the existing restaurant_settings.operational_configuration map. Registered keys are staffOrderEmail and staffOrderEmailAddress. Database/dashboard values override environment fallbacks on every snapshot, with no cache or restart needed. Fallbacks are STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED=false and STAFF_ORDER_EMAIL_ADDRESS=hello@nakorn.com.au. Existing dashboard values are never seeded over or overwritten. Admin saves validate address syntax/length; enabled staff delivery requires a usable recipient and configured SMTP. Existing permissions, optimistic versioning and configuration audit behavior remain.

## D. Database migration

V34__staff_order_email_notifications.sql extends notification_delivery_type_check and notification_delivery_owner_check to permit STAFF_ORDER_RECEIVED owned by an order. It adds notification_delivery_staff_channel_check requiring EMAIL for that purpose.

No indexes are added or bypassed. The existing partial unique index notification_delivery_order_event_unique on (order_id,type,channel) independently guarantees one customer ORDER_RECEIVED EMAIL and one STAFF_ORDER_RECEIVED EMAIL per order. Existing reservation uniqueness/ownership remains unchanged. Historical rows retain all existing allowed event values. No existing migration, notification records, order tables or phone persistence format were modified.

## E. Tests

- Frontend npm test: **158 passed**, zero failures/skips.
- Frontend npm run build: **passed**.
- Backend Maven verify on **Java 21.0.12**: **362 discovered, 197 passed, 165 skipped**, zero failures/errors; executable JAR built.
- Australian phone Chromium suite: **passed** (natural input, multi-character paste entry, blur formatting, cursor replacement, Backspace deletion, partial validation and canonical verification payload).
- Reservation Chromium suite: **passed**, including verification errors/expiry, destination changes, optional email and retry behavior.
- Admin settings Chromium suite: **passed**, including new fields, required recipient, existing permissions, source/version handling, validation and stale/rejected saves.
- git diff --check: **passed**.

Browser application APIs and delivery providers were mocked; no live notifications were sent. No DB_TEST_URL was present, so PostgreSQL-dependent integration suites were skipped, including the added V34 queue uniqueness/recipient capture and Admin persistence tests. The migration was not executed against a database. No SMTP inbox-delivery test or observability smoke check was run. Unit tests exercise actual worker delivery/retry state using a mocked sender. Physical phone keyboard behavior still merits manual review; Chromium editing is verified.

## F. Deployment considerations

Apply V34 before the application can enqueue STAFF_ORDER_RECEIVED. Coordinate application/worker rollout so old workers, whose enum does not include the staff event, are replaced before enabling staff notifications. The migration is compatible with historical rows and old applications while no staff-purpose jobs exist.

The default disabled toggle prevents new staff emails merely from applying this change. hello@nakorn.com.au is only a fallback recipient, not a hardcoded sender/handler address. Deliberately enabling the setting will queue staff emails for future genuinely new orders, including when customer order emails are disabled or no customer email exists. There is no historical-order backfill. An environment override can also enable the feature when no dashboard override exists; review deployment environment inputs before rollout.

Changing the address affects newly queued jobs only. Disabling staff notifications stops future enqueueing but does not cancel already queued jobs: those retain their captured recipient and existing retry semantics. No live SMTP credentials or production settings were changed, and no commit, push or deployment was performed.

## G. Risks and follow-ups

Run Maven verify with a dedicated disposable PostgreSQL database before deployment to exercise Flyway V34 and all skipped database tests. Confirm final email readability in target mobile email clients using approved local/test mail infrastructure. Physical mobile keyboard and assistive-technology review is optional follow-up.

The reference remains the existing first eight uppercase UUID characters; it is customer-safe but not globally collision-proof. SMTP success remains submission acceptance, not proof of inbox delivery. The existing worker can resend after a provider accepted a message if the subsequent database transaction fails; this change preserves existing delivery semantics rather than promising exactly-once SMTP delivery. Queue uniqueness prevents duplicate jobs for the same order/purpose.

The function enquiry phone field intentionally retains international/broader contact-number support. No new dependencies were introduced. Formatting happens on blur to avoid cursor jumps from automatic spacing during editing.

## H. Git status and diff summary

27 files changed/added: reusable frontend phone input and tests; shared order email rendering; independent staff purpose/settings; V34; integration regressions; configuration examples; this report. Tracked source diff: 17 files, +94/−24 lines. 10 new files (including this report); new code/test/migration files total 297 lines excluding this report. No commit was created.

```text
 M backend/.env.dev.example
 M backend/.env.prod.example
 M backend/src/main/java/au/com/nakornthai/notification/domain/NotificationType.java
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java
 M backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java
 M backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java
 M frontend/package.json
 M frontend/src/domains/notification/components/ContactVerification.jsx
 M frontend/src/domains/ordering/pages/CheckoutPage.jsx
 M frontend/src/domains/reservation/pages/ReservationPage.jsx
 M frontend/src/domains/restaurant/model/integrationSettings.js
 M frontend/src/domains/restaurant/model/integrationSettings.test.js
 M frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx
 M frontend/tests/e2e/adminSettings.browser.mjs
 M frontend/tests/e2e/reservationVerification.browser.mjs
?? backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java
?? backend/src/main/resources/db/migration/V34__staff_order_email_notifications.sql
?? backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/
?? docs/ordering/phone-and-order-email-implementation.md
?? frontend/src/shared/components/AustralianMobileInput.jsx
?? frontend/src/shared/utils/australianPhone.js
?? frontend/src/shared/utils/australianPhone.test.js
?? frontend/tests/e2e/australianPhone.browser.mjs
```

## Final PostgreSQL verification

The subsequent [final verification report](phone-and-order-email-final-verification.md) supersedes the earlier database-test gaps: PostgreSQL 16.14, Flyway V1–V34 verified, 373 backend tests passed with zero failures/errors/skips, and all requested transactional staff-notification scenarios verified. Production code and deployment configuration were unchanged during that pass; only regression tests and reports were expanded.
