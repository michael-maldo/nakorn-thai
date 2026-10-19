# Customer payment reliability — Phase 1

This implementation retains customer redirect checkout and existing staff queue APIs.
Staff React and Android remain operational interfaces; neither initiates PayPal checkout.
Mocked-provider tests are not evidence of PayPal sandbox or live readiness.

## Capture policy and state

- PAY_AT_RESTAURANT never invokes PayPal and can be accepted unpaid. PayID confirmation
  remains manual. PAYPAL acceptance/handover requires a verified capture and no financial hold.
- The receipt, worker and approved-order webhook use the same reconciliation handler.
  The worker can capture an authoritative APPROVED order without a browser return, only
  when the persisted restaurant order is open, unpaid and has no refund/review hold.
  The provider order/custom_id, CAPTURE intent, exact server AUD amount and final capture
  are validated. Approval/cancel redirects never establish payment.
- Capture uses the existing deterministic PayPal-Request-Id. A durable first capture-attempt
  timestamp limits uncertain APPROVED retries to five hours. After that, only authoritative
  completed-capture lookup is allowed; another capture needs manual investigation.
- Restaurant states are unchanged. order_payment.status remains PENDING/PAID, where PAID
  records the historical verified capture. financial_status independently tracks UNCONFIRMED,
  CAPTURED, PARTIALLY_REFUNDED, REFUNDED or MANUAL_REVIEW. paid_at is never cleared by a refund.
- A paid cancellation sets refund_status=REQUIRED. If capture is discovered after cancellation,
  the same obligation is created. NONE/REQUIRED/PENDING/COMPLETED/MANUAL_REVIEW are separate
  refund-obligation states. No application endpoint issues a PayPal refund.

## Transactions, recovery and limits

V39 is additive. It retains historical orders and receipts, adds durable leases/retry metadata,
an identifier-only verified webhook inbox, a unique provider-refund ledger and payment audit.
Existing paid cancelled orders are backfilled with REQUIRED refund obligations.

Reconciliation claims a three-minute lease under the existing order lock, commits, performs
network requests outside the transaction, then locks the order again to apply verified facts.
A separate locked check authorizes capture. Cancellation is temporarily rejected while an
authorized capture lease is active. A competing check returns the current snapshot and does
not capture. Expired leases can be reclaimed after a crash. Original provider keys are reused.
Provider credentials/environment are frozen for each operation; an environment/client identity
hash is pinned to the payment. Legacy unpinned rows adopt the current identity at first check,
so verify the configured merchant/environment before enabling reconciliation on old data.

The dedicated single-thread payment scheduler does not occupy notification-delivery threads.
Each pass handles at most five inbox events and five payments sequentially. Defaults:

| Setting | Default | Meaning |
| --- | --- | --- |
| PAYPAL_RECONCILIATION_ENABLED | true | Enable scheduled passes; disabling does not disable customer checks or webhook receipt |
| PAYPAL_RECONCILIATION_POLL_MS | 30000 | Delay after a pass finishes |
| PAYPAL_RECONCILIATION_INITIAL_DELAY_MS | 30000 | Startup delay |
| PAYPAL_WEBHOOK_ID | empty | PayPal's registered webhook ID for this environment/application |

Uncaptured payments are checked once per minute after a successful pending lookup; five-hour
unresolved orders require review. Provider failures back off from 30 seconds to one hour and
stop automatic retries after eight failures. Paid captures are checked daily for external
refund state. Known refunded financial state cannot regress on a late completed event.
The worker never creates provider orders. An ambiguous create remains the same food order;
the existing customer retry uses its original create key within its existing five-hour window.
There is no silent replacement after that window. A missing provider ID eventually requires review.

## Verified webhook inbox

POST /api/payments/paypal/webhook is the only CSRF exception. All customer/staff writes keep
their existing CSRF and authorization requirements. The body is size checked and sent with
PayPal transmission headers and configured webhook ID to PayPal's
/v1/notifications/verify-webhook-signature endpoint. Only SUCCESS is accepted. Invalid
signatures return 401; configuration/verification outages return non-2xx for provider retry.
The app never downloads a caller-supplied certificate URL.

After verification, persist event ID, event type, resource ID and provider identity; no raw
payload, OAuth token, signature or customer token is retained. Duplicate event IDs do not
duplicate effects. Return 204 after durable receipt; provider I/O is deferred to the worker.
CHECKOUT.ORDER events trigger shared order reconciliation. PAYMENT.CAPTURE events use
authoritative Orders/Payments API lookups and validate amount, order UUID and provider linkage.
REFUNDED events retrieve the refund and its capture; only IDs are extracted from HATEOAS
links, never followed as arbitrary authenticated URLs. Completed refund IDs are counted once.
Pending refunds are retried; partial/failed/uncertain refunds remain visible for review.
Verified reversal events preserve capture history and create a financial hold/manual review:
the application does not infer a resolved dispute from a webhook alone.

Inbox processing has its own three-minute leases and eight-attempt exponential retry limit.
Unmatched or unprocessable verified events remain in the review queue rather than being
attached speculatively to another order. Unknown event families are acknowledged without
changing restaurant/payment state. Duplicate/out-of-order events always re-read provider state.

## Operational review

ADMIN/FOH can use **Review payment and refund obligations** on the React orders page.
This includes older orders, rather than only the active queue or 24-hour order history.
Android displays financial holds/refund status on existing orders and blocks unsafe handover
actions; backend enforcement remains authoritative.

- GET /api/staff/payments/review lists the oldest 200 held/refund/unresolved orders and up
  to 100 exhausted webhook events. Reaching a limit is displayed; this is a bounded worklist,
  not a complete accounting export.
- Existing POST /api/staff/payments/{id}/check performs lookup only, not staff-driven capture.
- POST /api/staff/payments/{id}/refund-obligation records versioned progress with
  `{ "version": 4, "status": "PENDING", "reference": "internal-review-reference" }`.
  Required states/reference, staff role and CSRF are validated. PayPal COMPLETED cannot be
  asserted manually; it requires authoritative provider reconciliation. For cash/PayID, a
  staff-recorded completion is an explicit operational attestation.
- Inspect payment_audit, paypal_refund and paypal_webhook_event using approved support
  access when investigating exhausted/ambiguous outcomes. Preserve records. Do not create
  another food/provider order, change merchant identity, or clear a financial hold to retry a
  charge. Resolve with provider evidence and a reviewed support procedure. There is no
  automatic dispute adjudication or general-purpose manual financial-hold clearing endpoint.

Metrics: nakorn.payment.pending, nakorn.payment.pending.oldest.seconds,
nakorn.payment.manual.review, nakorn.payment.refund.obligations,
nakorn.payment.webhook.manual.review, nakorn.payment.reconciliation.attempts/failures,
and nakorn.payment.webhook.verification/processing. Gauges refresh after scheduled passes.
Alert on age growth, repeated failures, manual review and unresolved refund obligations.
Structured logs include requestId correlation and sanitized order/event identifiers, never
provider payloads, authorization values, customer tokens or credentials.

## Tests and disposable PostgreSQL

Use a dedicated disposable PostgreSQL database, including btree_gist (required by existing
timekeeping V38). Set DB_TEST_URL/DB_TEST_USERNAME/DB_TEST_PASSWORD and run from backend:

```sh
mvn --batch-mode --no-transfer-progress verify
```

The test properties disable automatic payment scheduling; reliability tests explicitly invoke
the production worker against mocked provider responses. Concurrency tests use independently
committed database transactions. Never point these tests at production. Flyway clean remains
disabled; never edit V1–V38. Review V18's historical reset migration before any upgrade of an
existing operational database that has not applied it.

## Controlled sandbox validation — required before production consideration

1. Use a separate sandbox REST application and sandbox merchant/buyer accounts. Configure
   PAYPAL_ENV=sandbox, sandbox client ID/secret and the existing return route. Do not change
   production credentials. Register a sandbox HTTPS webhook URL reaching
   /api/payments/paypal/webhook and set its PAYPAL_WEBHOOK_ID in the backend environment.
2. Subscribe to CHECKOUT.ORDER.APPROVED, CHECKOUT.ORDER.COMPLETED,
   PAYMENT.CAPTURE.COMPLETED, PAYMENT.CAPTURE.PENDING, PAYMENT.CAPTURE.DENIED,
   PAYMENT.CAPTURE.REFUNDED and PAYMENT.CAPTURE.REVERSED as supported by the sandbox app.
   A simulator is useful for rejection/routing checks; a genuine sandbox transaction is
   required to validate real resources and financial reconciliation.
3. Create an actual customer pickup order. Verify authoritative AUD total and receiving
   sandbox merchant. Approve and return normally; confirm one capture and staff acceptance.
4. Repeat with browser closed after approval. Confirm worker/webhook captures once and the
   same persisted order becomes paid. Replay the same genuine webhook and verify one effect.
5. Cancel approval and confirm the order remains unpaid PAYPAL. Cancel the food order before
   approval and verify it cannot subsequently be captured by the worker.
6. Interrupt backend/network after a sandbox capture, restart/retry and confirm lookup repairs
   the same local payment without another charge. Exercise a concurrent staff check.
7. Cancel a paid order: REQUIRED must appear. Issue a refund manually **in the sandbox PayPal
   dashboard**, then verify refund-event/worker reconciliation and preserved paid_at/capture ID.
   Exercise partial/pending refunds and reordered deliveries. Verify tampered webhook rejection.
8. Verify PAY_AT_RESTAURANT acceptance while unpaid invokes no PayPal endpoint; verify PayID
   confirmation, existing React/Android queues and kitchen transitions still work.

Sandbox evidence, receiving-account verification, capacity/rate-limit tuning, operational alert
configuration, supported refund/reversal payloads and rollout/backup procedures remain release gates.
No live transactions or production deployment are part of this phase.

References: [PayPal webhook verification](https://developer.paypal.com/api/webhooks/overview/),
[PayPal idempotency](https://developer.paypal.com/reference/guidelines/idempotency/),
[Payments API](https://developer.paypal.com/api/payments/v2/).
