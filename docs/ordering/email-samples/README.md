# Order email samples and presentation review

All samples use fictional Alex Sample details, example.test addresses and a dummy phone number. They were generated from persisted/reloaded PostgreSQL snapshots by the actual SendOrderConfirmationHandler, OrderEmailDetails and OrderEmailHtml code. No SMTP messages were sent.

| Event | Plain-text sample | HTML sample | Assessment |
| --- | --- | --- | --- |
| ORDER_RECEIVED | [Text](customer-order-received.txt) | [HTML](customer-order-received.html) | Reference-bearing acknowledgement; acceptance/payment pending; private one-click read-only tracking action. |
| ORDER_ACCEPTED | [Text](customer-order-accepted.txt) | [HTML](customer-order-accepted.html) | Reference, recorded paid state, full local estimated pickup date/time and tracking action. |
| ORDER_READY | [Text](customer-order-ready.txt) | [HTML](customer-order-ready.html) | Ready-for-pickup subject, restaurant address, paid state and current-status tracking action. |
| ORDER_CANCELLED | [Text](customer-order-cancelled.txt) | [HTML](customer-order-cancelled.html) | Saved cancellation reason and separate-refund caveat; obsolete pickup estimate omitted. The same private read-only link remains valid until its original seven-day expiry. |
| STAFF_ORDER_RECEIVED | [Text](staff-order-received.txt) | [HTML](staff-order-received.html) | Operational heading, reference, fictional contacts and authenticated staff View order action. |

## Snapshot integrity

Reference: ABCD1234. Placed: 7 October 2030, 6:05 PM AEDT (Australia/Melbourne). Accepted pickup estimate: 7 October 2030, 6:45 PM AEDT. Fulfilment: Pickup. Payment method: PayPal. Received/staff samples precede recorded payment; later samples use the persisted paidAt state.

| Item / variation | Quantity | Snapshot base | Options per ordered item | Unit price including options | Line total |
| --- | ---: | ---: | --- | ---: | ---: |
| Green Curry / Chicken | 2 | AUD 23.90 | Extra vegetables: 1 × AUD 2.50; Mild: 1 × AUD 0.00 | AUD 26.40 | AUD 52.80 |
| Pad Thai / Prawns | 1 | AUD 26.90 | Extra prawns: 2 × AUD 3.00 | AUD 32.90 | AUD 32.90 |
| Jasmine Rice / Large | 3 | AUD 4.50 | None | AUD 4.50 | AUD 13.50 |

Stored total: 9920 cents = **AUD 99.20** = 52.80 + 32.90 + 13.50. Option adjustments are included in unit prices, with quantities explicitly per ordered item. The integration fixture changes current menu names and prices after persisting the order; all historical values remain unchanged in emitted emails. Missing optional fields are omitted gracefully.

## Presentation and security

Customer subjects use “Nakorn Thai — Order #ABCD1234 — [event]”; staff use “Nakorn Thai — New customer order #ABCD1234”. Existing presentation refinements remain: audience-specific introductions, readable payment/status labels, structured option quantity/adjustment lines, local pickup date/time, cancellation reason and blank lines between items.

Plain text contains a labelled, unbroken action URL. HTML derives its content from the same escaped plain text, uses explicit line breaks and an accessible styled anchor button. MIME includes both text/plain and text/html alternatives. All five HTML samples were rendered at 390px in Chromium without horizontal overflow; action text, URLs and total matched plain text. Long plain-text URLs remain unbroken for reliable clicking; email clients can soft-wrap them. Actual Gmail/Outlook/Apple Mail rendering and live delivery remain untested.

The UUID in each URL is an order **locator**, not an authorization credential. Customer links open the existing SMS/email verification experience with the locator prefilled. Only an existing receipt token or successfully verified, unexpired tracking grant authorizes order reads. No tracking hash, bearer grant, OTP secret or staff credential is included in either email version. Staff links require the existing staff login and backend role authorization. The example.test host is a sample configuration, not a production destination.

## Regeneration and verification

Final full regression: **386 backend tests passed, zero failures/errors/skips**, Java 21.0.12 and a disposable PostgreSQL 16.14 database, Flyway V1–V35. Frontend: **160 tests passed**, production build passed. New tracking browser suite and all eight existing Chromium suites passed. SMTP/MIME tests use a mock transport; sample generation mocks delivery, diagnostics and scheduling.

Run from backend/ with a dedicated disposable database; ordinary test runs do not rewrite these artifacts unless the export property is provided:

```bash
DB_TEST_URL=jdbc:postgresql://127.0.0.1:55437/nakorn_tracking_release \
DB_TEST_USERNAME=nakorn_test DB_TEST_PASSWORD=disposable-only \
JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk \
PATH=/usr/lib/jvm/java-21-temurin-jdk/bin:$PATH \
mvn --batch-mode --no-transfer-progress \
-Dtest=OrderEmailPresentationIntegrationTest \
-Dorder.email.samples.directory=/home/gnostex/Documents/nakorn/application/nakorn-thai/docs/ordering/email-samples test
```

Source: backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailPresentationIntegrationTest.java. It generates all five text/HTML pairs from actual queued jobs using the trusted PUBLIC_APP_URL=https://restaurant.example.test/ test property. Option ordering follows the existing persisted UUID ordering; quantities, names, prices and totals are invariant.

See the [seven-day tracking implementation and verification report](../seven-day-order-tracking.md) for architecture, URL configuration, migration compatibility, tests, deployment considerations and complete git status. No commit, push, deployment, production configuration change or live notification was performed.

The four customer samples use one identical opaque token generated by the actual application code. The test order and grant are rolled back, so these example.test links grant no real access. Staff links continue to require staff sign-in. The business website setting takes precedence over PUBLIC_APP_URL; PayPal callbacks are no longer used as a fallback.
