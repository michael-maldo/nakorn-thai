# Phase 1 customer payment reliability — implementation report

## Summary

Implemented customer-only PayPal recovery while retaining redirect checkout, authoritative AUD
pricing, order/provider idempotency, PayID, unpaid pay-at-restaurant acceptance, and staff/kitchen
operations. No staff order creation, QR handoff, native checkout, JS SDK, automatic provider refund,
production credential change, deployment or live transaction was performed.

The detailed design, operating controls, financial states, webhook subscriptions and sandbox
checklist are in [customer-payment-reliability.md](customer-payment-reliability.md).

## Migration

V39__payment_reconciliation_and_refund_obligations.sql is new and additive. It adds order refund/
review flags, payment lease/retry/provider-identity/financial metadata, paypal_webhook_event,
paypal_refund and payment_audit with indexes/constraints. It backfills REQUIRED for historical paid
cancelled orders and CAPTURED for existing paid PayPal rows. It does not truncate or delete history.
No earlier migration was changed. Fresh migration and Hibernate validation passed on disposable
PostgreSQL 16.15. Existing V38 requires btree_gist; the host installation lacked it, so verification
used a separate PostgreSQL 16 container instead of bypassing or editing that migration.

## State, reconciliation and webhook design

- Historical payment status remains PENDING/PAID and paid_at remains evidence of capture.
  Separate financial state/refund obligations describe current funds and required actions.
- A shared handler is used by customer checks, staff lookup, worker and order webhooks. A durable
  three-minute lease and short locked transactions fence concurrent callers; provider I/O occurs
  outside the business transaction. Capture eligibility is checked again under the order lock.
- Only authoritative APPROVED/open/unpaid/non-held PayPal orders can be captured. Initial capture
  and uncertain retries are bounded by five-hour safety windows. Original deterministic capture
  keys are retained. The worker never creates replacement provider orders.
- Eight failed attempts trigger manual review; retries back off from 30 seconds to one hour.
  Pending approvals are polled until the review window; historical captures are checked daily.
  Pending payments take priority over historical capture monitoring in bounded batches.
- PayPal verifies webhook transmission headers/body against the configured webhook ID. Only verified
  identifiers enter a durable deduplicated inbox. Worker processing retrieves authoritative order,
  capture and refund resources and validates their linkage and AUD amounts. Invalid/unverifiable
  notifications are not acknowledged as successful. No sensitive payload or credential is stored.
- Refund/reversal facts cannot undo capture history. Reversal without authoritative completed-capture
  evidence never invents a paid status. Reversal uncertainty blocks fulfillment and stays reviewable.
- Dedicated payment scheduling leaves notification delivery on a separate scheduler. Counts, age,
  attempts/failures, refund obligations and exhausted inbox work are observable without payload logs.

## Refund obligations

Paid cancellation creates REQUIRED; capture discovered after cancellation does the same.
Staff can record PENDING/manual progress with version, CSRF and role checks. They cannot assert
PayPal refund completion. Verified external refund resources populate a unique refund ledger;
full refunds become COMPLETED, partial/uncertain cases remain reviewable. A resolved full refund
leaves the unresolved worklist while original capture history and fulfillment restrictions remain.
The React worklist includes older orders/exhausted events; existing Android snapshots display
refund/hold fields. No automatic PayPal refund is issued.

## Executed validation

| Suite | Command/context | Result |
| --- | --- | --- |
| Backend including PostgreSQL integration | Java 21; dedicated DB_TEST_*; `mvn --offline --batch-mode --no-transfer-progress verify` | 470 passed, 0 failed/errors/skipped; executable JAR built |
| Payment subset within backend | CreatePaymentHandlerTest (23), CustomerPaymentReliabilityIntegrationTest (33), PayPalReconciliationIntegrationTest (2), ProviderContractsTest (9), RuntimeAvailabilityTest (3) | 70 passed; provider mocked |
| Frontend | `npm test` | 196 passed, 0 failed/skipped |
| Frontend production build | `npm run build` | Passed |
| Payment browser | `npm run test:payment-browser`, PAYMENT_TEST_URL pointed at isolated Vite/Chrome | Passed; includes financial review worklist |
| Staff order browser | `npm run test:orders-browser`, ORDERS_TEST_URL pointed at isolated Vite/Chrome | Passed |
| Android JVM/build | Java 21; `./gradlew --offline --no-daemon testDebugUnitTest assembleDebug` | 79 passed, 0 failed/errors/skipped; debug APK built |
| Patch formatting | `git diff --check` | Passed |
| Android instrumentation | No designated disposable device/emulator used | NOT EXECUTED |
| Real PayPal sandbox / live payments | No provider transactions initiated | NOT EXECUTED |

The initial database run failed before tests because the host lacked btree_gist; this was resolved
using the disposable container. Early development failures were corrected and the final suites above
passed. Frontend tests emitted non-fatal sandbox HMR/socket warnings. Gradle reported existing
deprecation warnings. Neither is recorded as a failed final test.

## Required scenario coverage

All paths below are relative to backend/src/test/java/au/com/nakornthai/payment/createpayment/.

| Requirement | Evidence |
| --- | --- |
| Successful PayPal payment | CustomerPaymentReliabilityIntegrationTest.customerCheckCapturesAndVerifiesServerTotal |
| Customer cancels approval | cancelledApprovalNeverBecomesCashOrPaid |
| Browser closes after approval | browserClosedAfterApprovalWorkerCapturesWithoutCustomerToken |
| Crash between capture and persistence | crashAfterRemoteCaptureRecoveredAfterLeaseExpiryWithoutRecapture; PayPalReconciliationIntegrationTest.lostCaptureResponseRollsBackLocallyThenReconcilesWithoutRecapture |
| Duplicate capture | duplicateChecksNeverCaptureTwice; existing CreatePaymentHandlerTest idempotency test |
| Concurrent reconciliation / cancellation | independentRequestsCannotCaptureThroughTheSameLease; cancellationCannotRaceAnAuthorizedCapture; PayPalReconciliationIntegrationTest concurrent test |
| Duplicate webhook | duplicateWebhookIsDeduplicated; duplicateRefundEventsCannotDoubleRefundLedger |
| Out-of-order webhook | fullRefundBeforeCaptureNotificationPreservesCaptureAndRefund; verifiedReversalRequiresReviewAndCannotBeUndoneByLateCompletedEvent |
| Invalid signature | invalidSignatureIsRejectedWithoutPersistence; ProviderContractsTest signature cases |
| Provider timeout / bounded retry | timeoutBackoffThenLookupRecoversWithoutRecapture; failuresAreBoundedAndBecomeReview |
| External refunds | pendingExternalRefundRechecksToCompletion; full/partial/deduplication cases |
| Paid cancellation | paidCancellationCreatesRefundObligation; lateCompletedCaptureOnCancelledOrderCreatesRefundObligation |
| Cash acceptance unpaid / no PayPal | payAtRestaurantAcceptanceNeverCallsPaypal |
| Unpaid PayPal rejection | unpaidPaypalCannotBeAccepted |
| PayID / existing operations | Existing CreatePaymentHandlerTest PayID tests; full order/kitchen/identity/reservation suite; browser staff contracts; Android JVM suite |

Crash/lifecycle failures use deterministic provider/lease fault simulations, not an actual JVM kill
or a real PayPal transaction. PostgreSQL concurrency tests use independently committed transactions.

## Remaining production gates

1. Run the controlled sandbox checklist in the design document, including actual receiving merchant,
   authenticated webhook payloads, refunds/reversals, browser abandonment and restart recovery.
2. Verify the merchant/environment before first adoption of legacy unpinned payments. Keep provider
   identity stable while resolving old payments/refunds; identity mismatches require review.
3. Establish alerts, support procedures and ingress rate limits. Tune worker capacity/provider limits.
   Worklists are bounded (200 orders/100 events), not a complete accounting export; the existing
   active order queue also retains its original 200-order limit.
4. Ambiguous provider creation, expired capture windows and unresolved disputes intentionally require
   manual investigation. Partial refunds remain conservative financial holds. No general financial-hold
   clearing or automated dispute/refund policy was introduced.
5. Verify deployment migration history/backups, including the historical V18 reset if still unapplied
   against an existing operational database. No operational database was used here.
6. Android instrumentation and actual sandbox integration remain unverified. Passing mocks/database
   tests does not establish production PayPal readiness.

## Sandbox instructions

Use only sandbox credentials in a separate environment. Set PAYPAL_ENV=sandbox and the existing
PAYPAL_RETURN_URL route; register /api/payments/paypal/webhook in the same sandbox REST application
and set its PAYPAL_WEBHOOK_ID. Keep the reconciliation worker enabled. Follow the full approval,
abandonment, retry, cancellation, refund/reversal and existing-method checklist in the design document.
No production credentials or live refunds are needed for these checks.

## Suggested commit message

`feat(payment): reconcile PayPal payments and track verified refund obligations`

No commit or deployment was made.

## Files added and modified

| Change | File | Purpose |
| --- | --- | --- |
| Modified | `android-staff/app/src/main/java/au/com/nakornthai/staffapp/ordering/model/Order.java` | Display financial holds/refund status and retain safe action prerequisites. |
| Modified | `android-staff/app/src/main/java/au/com/nakornthai/staffapp/ordering/ui/OrderPresentation.java` | Display financial holds/refund status and retain safe action prerequisites. |
| Modified | `android-staff/app/src/test/java/au/com/nakornthai/staffapp/OrderWorkflowTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `backend/.env.dev.example` | Document webhook ID and worker controls; example values only. |
| Modified | `backend/.env.prod.example` | Document webhook ID and worker controls; example values only. |
| Modified | `backend/src/main/java/au/com/nakornthai/ordering/changestatus/ChangeOrderStatusHandler.java` | Track paid cancellation obligations and protect capture/fulfillment transitions. |
| Modified | `backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderResponse.java` | Persist/map additive financial fields while preserving existing order/payment contracts. |
| Modified | `backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderJpaEntity.java` | Persist/map additive financial fields while preserving existing order/payment contracts. |
| Modified | `backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderMapper.java` | Persist/map additive financial fields while preserving existing order/payment contracts. |
| Modified | `backend/src/main/java/au/com/nakornthai/payment/createpayment/CreatePaymentHandler.java` | Delegate checks to shared reconciliation; retain initiation and PayID behavior. |
| Modified | `backend/src/main/java/au/com/nakornthai/payment/infrastructure/OrderPaymentJpaEntity.java` | Persist/map additive financial fields while preserving existing order/payment contracts. |
| Modified | `backend/src/main/java/au/com/nakornthai/payment/infrastructure/PayPalPaymentProvider.java` | Provider signature verification, financial resource reads and frozen provider identity. |
| Added | `backend/src/main/java/au/com/nakornthai/payment/reconcile/PaymentReconciliationHandler.java` | Shared leased reconciliation, bounded worker and separate scheduling. |
| Added | `backend/src/main/java/au/com/nakornthai/payment/reconcile/PaymentReconciliationWorker.java` | Shared leased reconciliation, bounded worker and separate scheduling. |
| Added | `backend/src/main/java/au/com/nakornthai/payment/reconcile/PaymentSchedulingConfiguration.java` | Shared leased reconciliation, bounded worker and separate scheduling. |
| Added | `backend/src/main/java/au/com/nakornthai/payment/refund/RefundObligationController.java` | Role/CSRF-protected refund progress and unresolved financial worklist. |
| Added | `backend/src/main/java/au/com/nakornthai/payment/webhook/PayPalWebhookInbox.java` | Signature-verified, identifier-only, deduplicated webhook processing. |
| Modified | `backend/src/main/java/au/com/nakornthai/payment/webhook/PaymentWebhookController.java` | Signature-verified, identifier-only, deduplicated webhook processing. |
| Modified | `backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java` | Permit verified webhook ingress; exempt only its POST from CSRF. |
| Added | `backend/src/main/resources/db/migration/V39__payment_reconciliation_and_refund_obligations.sql` | Additive reconciliation, webhook deduplication, refund ledger and obligation schema. |
| Modified | `backend/src/test/java/au/com/nakornthai/payment/createpayment/CreatePaymentHandlerTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Added | `backend/src/test/java/au/com/nakornthai/payment/createpayment/CustomerPaymentReliabilityIntegrationTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `backend/src/test/java/au/com/nakornthai/payment/createpayment/PayPalReconciliationIntegrationTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `backend/src/test/java/au/com/nakornthai/payment/createpayment/ProviderContractsTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `backend/src/test/java/au/com/nakornthai/payment/createpayment/RuntimeAvailabilityTest.java` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `backend/src/test/resources/application.properties` | Payment/recovery/security or existing channel regression coverage. |
| Added | `docs/payment/customer-payment-reliability.md` | Design, operations, limitations and sandbox validation instructions. |
| Modified | `docs/payment/payments-and-tracking.md` | Design, operations, limitations and sandbox validation instructions. |
| Added | `docs/payment/phase1-implementation-report.md` | Implementation deliverables, executed results and complete file manifest. |
| Modified | `frontend/src/domains/ordering/model/orderModel.js` | Display financial holds/refunds and provide an explicit staff review worklist. |
| Modified | `frontend/src/domains/ordering/model/orderModel.test.js` | Payment/recovery/security or existing channel regression coverage. |
| Modified | `frontend/src/domains/ordering/pages/OrdersAdminPage.jsx` | Display financial holds/refunds and provide an explicit staff review worklist. |
| Modified | `frontend/src/domains/payment/components/PaymentForm.jsx` | Display financial holds/refunds and provide an explicit staff review worklist. |
| Added | `frontend/src/domains/payment/components/PaymentReviewPanel.jsx` | Display financial holds/refunds and provide an explicit staff review worklist. |
| Modified | `frontend/tests/e2e/payments.browser.mjs` | Payment/recovery/security or existing channel regression coverage. |
