# Epson ePOS activation review

Device printing is opt-in in both Staff PWAs. Local implementation and validation were completed before approval. The user subsequently approved committing, pushing and deploying sequentially through the existing GitHub Actions workflows. Physical printer testing remains deferred.

## Device preferences and enforcement

`deviceEnabled` is stored inside the existing printer preference object:

- Original PWA: `localStorage['nakorn.staff.printer']`.
- Prototype: `localStorage['nakorn-staff-pwa-proto:orders:printer']`.

The shared controller merges defaults with saved fields without writing on load. A missing `deviceEnabled` is false, including legacy profiles with `autoPrint: true`. Toggling device enablement preserves profile fields and explicitly resets automatic printing to false. Storage failures leave the active preference unchanged. Each browser/application must be configured independently.

The shared UI gates endpoint checks and Test Print on the build capability, saved device enablement and valid saved profile. No operation runs on mount/save/toggle. Test Print retains its confirmation prompt. Endpoint status and test-print status are separate and reset when the profile changes; saving never claims connectivity.

The shared acceptance helper and both order-detail entry points enforce enablement. The existing ePOS driver also rejects disabled devices before fetch; its controller reads the current preference to reject stale-dialog sends after disablement. Existing authoritative acceptance/reload, no polling prints, no uncertain retries, pending-operation guards and BOH kitchen order layout remain intact. Print errors remain independent of accepted order state.

Original PWA Settings and Printer Management provide operational access for ADMIN, FOH and BOH. ADMIN Manage → Settings & integrations → Printing reuses that controller and component. The original PWA Manage controls are now ADMIN-only; the link opens the existing Settings page and its shared Printing panel. No new configuration page is introduced. Prototype Device settings and its existing ADMIN integrations panel continue to share one session controller.

## Builds and coordinated deployment

- `.github/workflows/deploy.yml`: production Install and build sets `VITE_STAFF_EPOS_ENABLED: 'true'`. Its development regression server deliberately retains false; separate checks exercise the enabled production preview.
- `.github/workflows/deploy-staff-pwa-proto.yml`: job environment sets the same non-secret flag to true.
- Both jobs inspect their production JavaScript for enabled capability and device guards, and exercise production settings with synthetic APIs. Tests never click a printer action; original production checks also block the synthetic printer URL in CDP.
- Shared printing changes trigger both workflows for the same approved main-branch revision. These remain independent deployments, not an atomic two-app release. Confirm both workflow results and revision before hardware testing. The original workflow also publishes website assets; backend activation remains governed by its existing change detection.
- Approved sequence: temporarily disable the prototype workflow before pushing, commit the reviewed 18-file diff, push main and monitor the original deployment. Verify its active release and health before re-enabling and manually dispatching the prototype workflow on the same main revision. Verify both releases and service workers. Do not enable printing on an operational device during deployment.
- Leave automatic printing off. Configure the actual printer, enable device printing explicitly, check the endpoint, and perform one separately authorized Test Print. Confirm paper/cut physically before considering a separate automatic-print opt-in.

## Rollback on deployment or verification failure

Stop the sequence immediately on failure; do not dispatch the prototype after an original deployment failure. Capture current website, prototype and backend symlink targets and backend process identity before deployment. The original workflow uploads a new release then atomically replaces `/var/www/nakorn-thai/current`; if post-activation verification fails, restore the recorded previous target with a temporary symlink under `/var/www/nakorn-thai/` followed by `mv -Tf` onto `current`. Verify website, Staff Web, PWA assets/workers and backend health again. Do not redeploy or restart the unchanged backend.

The prototype activation script records its previous target and protected website/backend state in `/var/www/nakorn-thai/prototype/backups/<release>.json`. It automatically restores its previous symlink when activation smoke checks fail and verifies the protected state. For a later external verification failure, restore that recorded target with the same atomic symlink replacement within the prototype tree, then run the existing `release.py smoke` against the restored release. Restore the prototype workflow to its prior active state when coordination is finished or stopped. Keep release directories for investigation; do not delete them.

## Hardware prerequisites and risks

The existing driver requires an HTTPS origin without credentials, path or query and a valid Epson device ID. The tablet must reach the printer LAN endpoint, trust its certificate and match its hostname/IP. Printer firmware must provide the ePOS-Print XML service at `/cgi-bin/epos/service.cgi`, permit requests from the deployed PWA origin, and satisfy the browser's CORS and local-network permissions. Check both PWA deployment origins when configuring access. Working Android ESC/POS LAN printing does not verify this HTTPS/ePOS browser path.

No TLS bypass, raw TCP bridge or credentials are introduced. Browser failures can conflate certificate, CORS, LAN and permission problems. A timeout can mean uncertain paper delivery: inspect output before any deliberate retry. An already-dispatched request cannot be recalled by switching the device off. Hardware compatibility and physical output remain unverified until the restaurant-tablet test.

## Automated validation

- `npm test`: 239 passed; no skips.
- `npm run test:staff-proto`: 181 passed; no skips, including unchanged Android fingerprints.
- Original PWA browser suite: 93 assertions passed.
- Complete prototype browser runner: passed (381 core assertions, 100 responsive route renders, menu administration, shared Orders, printing settings and ADMIN settings).
- Enabled production settings checks: passed for both PWAs; no printer jobs.
- Both builds with `VITE_STAFF_EPOS_ENABLED=true`, production bundle checks, original artifact checks, and both production service-worker/cache isolation browser suites: passed.
- Prototype deployment Python suite: 20 passed.
- `git diff --check`: passed. No backend/schema or Android source changes; no hardware tests performed.

An initial prototype browser run encountered CDP “Promise was collected”; the complete sequential rerun passed. The original-shell and core-test fingerprints were intentionally refreshed; Android fingerprints were retained.

## Exact files changed

| File | Purpose |
| --- | --- |
| `.github/workflows/deploy.yml` | Enable original production ePOS capability and validate the enabled build/UI. |
| `.github/workflows/deploy-staff-pwa-proto.yml` | Enable prototype production capability and validate its enabled build/UI. |
| `frontend/src/shared/staff-orders/printing/epos.js` | Default device enablement off, validate saved profiles, guard transport. |
| `frontend/src/shared/staff-orders/printing/PrinterSettings.jsx` | Shared checkbox, action gates and separate verification statuses. |
| `frontend/src/shared/staff-orders/printing/acceptancePrinting.js` | Require device opt-in for acceptance printing. |
| `frontend/src/shared/staff-orders/hooks/useOrdersController.js` | Supply current device preference to driver and reuse persisted save handler. |
| `frontend/src/shared/staff-orders/components/OrderDetails.jsx` | Pass device opt-in to acceptance safeguard. |
| `frontend/src/shared/staff-orders/components/OrdersPage.jsx` | Gate prototype order printing on device opt-in. |
| `frontend/src/staff-pwa/StaffApp.jsx` | Operational settings, shared ADMIN integrations entry, role guards and order gate. |
| `frontend/src/staff-pwa-proto/StaffProtoApp.jsx` | Replace obsolete blanket “printing disabled” text. |
| `frontend/src/staff-pwa/tests/core.test.js` | Disabled-device, stale-dialog and acceptance regressions. |
| `frontend/src/staff-pwa/tests/eposBuild.test.mjs` | Verify enabled functionality in both production bundles. |
| `frontend/src/staff-pwa/tests/eposEnabled.browser.mjs` | Original production device opt-in, persistence and role checks. |
| `frontend/tests/e2e/staffPwa.browser.mjs` | Explicitly enable device in synthetic transport test. |
| `frontend/src/staff-pwa-proto/tests/printingSettings.browser.mjs` | Shared settings persistence, auto-print-off and capability/device action gates. |
| `frontend/src/staff-pwa-proto/tests/sharedOrders.test.js` | Namespaced printer persistence and existing profile preservation. |
| `frontend/src/staff-pwa-proto/tests/protected-baseline.json` | Refresh only the two intentionally changed original PWA fingerprints. |
| `docs/staff/epos-activation.md` | This review, validation evidence and approval-dependent deployment sequence. |
