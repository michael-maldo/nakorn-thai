# Shared staff Orders — review report

Status: implementation and local verification complete; approved for coordinated release through the existing GitHub Actions workflows.

## Architecture

Both authenticated shells mount `OrdersRuntimeProvider` and exactly one `useOrdersController`. The runtime injects the shell's identity context, authorization, API adapter and preference reader/writer. The controller owns the original foreground order monitor, pending acknowledgement tracker, audio engine and printer instance for the session; route changes do not mount another monitor. Session changes remount and clean up the controller.

`components/OrderBoard`, `OrderDetails`, `StaffOrderCard`, `Dialog` and `OrderMonitorStatus` own the operational dashboard, modal and actions. `hooks/`, `audio/`, `printing/` and `model/` contain the original operational implementations. `services/createOrderApi` preserves the original requests, CSRF, version payloads, identity guards and serialized mutations while creating a separate adapter instance for each application. Backend authorization remains authoritative.

The original PWA keeps its existing shell and styling. Legacy import paths are compatibility reexports. The Staff Web keeps its queue presentation and uses the same extracted card and pure helpers. Its payment-review adapter supplies its existing request wrapper and route format.

The prototype's Orders page is a thin shell adapter to `components/OrdersPage`: it supplies direct links, the contextual bottom-right Refresh portal and the one session controller. The shared page adds the prototype's history, financial-review links and per-order in-memory drafts around the original dashboard/modal. There is no second transition implementation, audio controller or polling timer. History is an explicitly refreshed snapshot, not a second polling loop. Scoped `orders.css` adapts the original presentation without importing the original application shell.

## Parity and deliberate adaptations

| Behavior | Original PWA after extraction | Prototype after integration |
| --- | --- | --- |
| Pending / Accepted / Preparing / Ready queues | Original board, summaries and responsive behavior | Same board; existing queue/status controls retained |
| Details, action forms, estimates and Reload | Original modal/card and reload behavior | Same modal/card; close/resume retains multiple drafts in memory |
| Active polling and Refresh | Original foreground 10-second monitor and in-flight guard | Same monitor; Refresh remains in contextual action bar |
| History / financial review / direct links | Existing shell behavior retained | Existing destinations and links retained; history reads explicitly |
| Mutation errors, payment prerequisites, version conflicts | Same shared card, guard and API contracts | Same implementation; conflict requires explicit reload/discard |
| Alerts / enable sound / acknowledgement / diagnostics | Original implementation and settings UI | Same implementation and UI in all-role Device settings; global monitor strip |
| Dockets / availability / errors | Original printer implementation; default disabled | Same implementation; default disabled; no silent-print assumption |
| Device preferences | Existing `nakorn.staff.*` keys | Separate `nakorn-staff-pwa-proto:orders:*` keys; no automatic preference migration |
| Device settings access | ADMIN, FOH and BOH; BOH remains kitchen-safe | ADMIN, FOH and BOH; does not grant Manage access |
| App routing / navigation / authentication / updates | Existing original shell preserved | Sidebar, ADMIN Manage, contextual Refresh and own auth preserved |

Draft retention is in memory, not a new cross-refresh persistence contract. Original modal Reload behavior is unchanged; prototype retained drafts deliberately require explicit reload on conflict. The prototype BOH queue starts at Pending so BOH can accept orders; the original BOH initial queue selection is preserved.

## RBAC

| Role | Orders | Cancellation | Reservations | Administration | Device sound/printer |
| --- | --- | --- | --- | --- | --- |
| ADMIN | All valid transitions | Allowed | Existing full access | Allowed | Allowed |
| FOH | All valid non-cancellation transitions | Denied | Existing non-cancellation access | Denied | Allowed |
| BOH | All valid non-cancellation transitions | Denied | Denied | Denied | Allowed, kitchen-safe |

BOH contact data remains hidden by the shared card/detail presentation and server response policy. Printer controls retain the original BOH kitchen layout and disabled auto-print override. Payment verification permissions and payment prerequisites are unchanged. No backend authorization, state-machine or database changes are included.

## Validation

All browser APIs are synthetic fixtures. No real orders, payments, refunds or printer jobs were issued.

- Existing frontend unit suite: 237 passed, zero skipped.
- Prototype unit suite (includes original sound/printing contracts): 178 passed, zero skipped.
- Existing PWA browser regression: 93 assertions passed, including responsive layouts and BOH device settings.
- Staff Web layout regression: 672 assertions; 88 route/width renders, 20 settings renders, 8 role-navigation renders and 16 public comparisons passed.
- Staff Web order draft browser regression passed.
- Prototype route/workflow regression: 381 assertions and 100 responsive renders passed (ADMIN/FOH/BOH and unauthorized direct routes).
- Prototype menu administration and full settings browser regressions passed.
- Shared Orders browser regression passed: user audio enablement, new-ID alert/repeat suppression, one read during concurrent refresh, multi-order modal drafts, reload/conflict handling and duplicate-submit prevention.
- Both production builds and original static PWA artifact validation passed.
- Both production installation browser suites passed: scoped workers, manifest, reload/control, immutable static caching and existing-cache/customer-scope isolation (14 original-PWA assertions).
- Deployment-helper/workflow-routing suite passed (20 tests).

The former prototype inline-card draft browser scenario now imports the shared modal scenario; inline-card behavior remains covered by the original Staff Web suite. Fingerprint baselines were updated only for the intentionally extracted sources; those assertions are review snapshots, not claims that the original PWA source is byte-for-byte unchanged. Backend, Android, public site source/styles, manifest and worker implementation files remain unchanged.

## CI/CD and release implications

The prototype workflow path filter now includes the shared module and its unchanged external presentation dependencies, plus the original audio/printing unit contracts. Its routing tests verify that shared Orders changes select BOTH frontend workflows. Existing `deploy.yml` already matches `frontend/**`.

A future push of these frontend changes would run the shared frontend/customer-asset deployment and the dedicated prototype deployment. The combined workflow redeploys customer-facing artifacts even though customer functionality is unchanged. Backend changes are not selected by this frontend-only diff. Workflow concurrency groups are independent, so coordinate the future rollout to avoid simultaneous activation/safeguard checks. No workflow deployment logic, Nginx, server, service worker scope or release infrastructure was changed.

## Device and verification limits

Browser audio enablement and diagnostics cannot prove audible playback, iPad locked-screen delivery or background execution on physical devices. Existing autoplay, recovery and suspended-context behavior is reused and unit tested; physical iPad/Android acceptance is still needed. Hardware printing stays disabled unless separately approved; docket generation, unavailable printer behavior and failures are tested with fakes. No Epson hardware, certificate, network or silent Android Chrome printing verification is claimed.

Production worker installation/cache isolation is checked with local built assets and mocked API sessions. The results above describe local validation. Live release verification is reported separately after the approved coordinated deployment.

## Changed files

- `.github/workflows/deploy-staff-pwa-proto.yml`
- `frontend/src/domains/ordering/api/orderApi.js`
- `frontend/src/domains/ordering/model/orderModel.js`
- `frontend/src/domains/ordering/model/staffOrderDrafts.js`
- `frontend/src/domains/ordering/pages/OrdersAdminPage.jsx`
- `frontend/src/domains/payment/components/PaymentReviewPanel.jsx`
- `frontend/src/shared/staff-orders/README.md`
- `frontend/src/shared/staff-orders/audio/alerts.js`
- `frontend/src/shared/staff-orders/components/AudioSettings.jsx`
- `frontend/src/shared/staff-orders/components/Dialog.jsx`
- `frontend/src/shared/staff-orders/components/OrderBoard.jsx`
- `frontend/src/shared/staff-orders/components/OrderDetails.jsx`
- `frontend/src/shared/staff-orders/components/OrderMonitorStatus.jsx`
- `frontend/src/shared/staff-orders/components/OrdersPage.jsx`
- `frontend/src/shared/staff-orders/components/PaymentReviewPanel.jsx`
- `frontend/src/shared/staff-orders/components/StaffOrderCard.jsx`
- `frontend/src/shared/staff-orders/hooks/useOrderMonitor.js`
- `frontend/src/shared/staff-orders/hooks/useOrdersController.js`
- `frontend/src/shared/staff-orders/index.js`
- `frontend/src/shared/staff-orders/model/orderModel.js`
- `frontend/src/shared/staff-orders/model/presentation.js`
- `frontend/src/shared/staff-orders/model/staffOrderDrafts.js`
- `frontend/src/shared/staff-orders/orders.css`
- `frontend/src/shared/staff-orders/printing/PrinterSettings.jsx`
- `frontend/src/shared/staff-orders/printing/acceptancePrinting.js`
- `frontend/src/shared/staff-orders/printing/dockets.js`
- `frontend/src/shared/staff-orders/printing/epos.js`
- `frontend/src/shared/staff-orders/runtime.jsx`
- `frontend/src/shared/staff-orders/services/createOrderApi.js`
- `frontend/src/staff-pwa-proto/CHANGES.md`
- `frontend/src/staff-pwa-proto/PARITY.md`
- `frontend/src/staff-pwa-proto/README.md`
- `frontend/src/staff-pwa-proto/StaffProtoApp.jsx`
- `frontend/src/staff-pwa-proto/VERIFICATION.md`
- `frontend/src/staff-pwa-proto/app/orderPreferences.js`
- `frontend/src/staff-pwa-proto/deploy/test_workflow_routing.py`
- `frontend/src/staff-pwa-proto/features/ordering/api/orderApi.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderModel.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/staffOrderDrafts.js`
- `frontend/src/staff-pwa-proto/features/ordering/pages/OrdersAdminPage.jsx`
- `frontend/src/staff-pwa-proto/features/restaurant/pages/IntegrationSettingsPage.jsx`
- `frontend/src/staff-pwa-proto/styles/prototype.css`
- `frontend/src/staff-pwa-proto/tests/isolation.test.js`
- `frontend/src/staff-pwa-proto/tests/ordersDrafts.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/printingSettings.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/protected-baseline.json`
- `frontend/src/staff-pwa-proto/tests/prototype.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/prototypeFixtures.mjs`
- `frontend/src/staff-pwa-proto/tests/run-browser.mjs`
- `frontend/src/staff-pwa-proto/tests/sharedOrders.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/sharedOrders.test.js`
- `frontend/src/staff-pwa-proto/tests/source-baseline.json`
- `frontend/src/staff-pwa/StaffApp.jsx`
- `frontend/src/staff-pwa/components/Dialog.jsx`
- `frontend/src/staff-pwa/features/notifications/alerts.js`
- `frontend/src/staff-pwa/features/orders/OrderBoard.jsx`
- `frontend/src/staff-pwa/features/orders/OrderDetails.jsx`
- `frontend/src/staff-pwa/features/orders/presentation.js`
- `frontend/src/staff-pwa/features/orders/useOrderMonitor.js`
- `frontend/src/staff-pwa/features/printing/PrinterSettings.jsx`
- `frontend/src/staff-pwa/features/printing/acceptancePrinting.js`
- `frontend/src/staff-pwa/features/printing/dockets.js`
- `frontend/src/staff-pwa/features/printing/epos.js`
- `frontend/tests/e2e/staffPwa.browser.mjs`

## ADMIN Printing entry verification

The original `PrinterSettings` UI is also mounted at Prototype → ADMIN Manage → Settings & integrations → Printing. It receives the same session controller profile, printer instance and save handler as Device settings. Printing remains device-local; the entry introduces no restaurant-wide configuration or API. Changes saved from Device settings refresh the ADMIN profile view.

Supported fields are printer name, HTTPS origin/IP, Epson device ID, paper width (58 mm / 32 columns or 80 mm / 48 columns), kitchen/detailed layout and opt-in print-on-acceptance. Saved-endpoint checking uses an empty ePOS document without feeding/cutting paper; Test Print sends an explicit confirmed test docket. A saved profile is not proof of connectivity or physical output.

`VITE_STAFF_EPOS_ENABLED === 'true'` is required at build time. Default builds remain disabled, and the shared deployment workflow explicitly sets false. Local preferences cannot bypass the gate. Enabling requires separately approved build configuration, a reachable HTTPS Epson ePOS-Print endpoint, trusted matching certificate, enabled ePOS service/CORS and browser local-network permissions, plus physical output verification. No enablement was performed.

Automatic printing is limited to opt-in printing on the accepting device after a successful acceptance mutation AND authoritative detail reload confirming ACCEPTED. It does not run from polling, on every status change or after uncertain acceptance. There are no automatic delivery retries, and printing failure never rolls back acceptance. BOH retains the existing kitchen-only order-docket override with automatic printing disabled; its device profile UI does not override those order-operation restrictions. Deliberate manual reprint is available for accepted/preparing/ready orders when enabled, after confirmation and a fresh detail read.

Profiles are browser-origin/device-local storage: original `nakorn.staff.printer`, prototype `nakorn-staff-pwa-proto:orders:printer`. They are not server-synchronized or per-account profiles; users of the same app in one browser share that device profile. Both ADMIN entry and all-role Device settings intentionally address the same prototype profile, while the original PWA remains isolated.

Printing-entry follow-up: both frontend unit suites pass (237 existing / 178 prototype), both builds pass, and the ADMIN Settings regression passes. A dedicated printing browser scenario covers the ADMIN path, shared local profile saving/synchronization, original preference isolation, disabled print gates and FOH/BOH device access with ADMIN routes denied. No hardware requests or real backend writes were used.
