# Staff RBAC refinement — coordinated release approved

Pre-change inventory: roles ADMIN/FOH/BOH come from identity sessions/JWT authorities.
SecurityConfig limits endpoint families; ChangeOrderStatusHandler separately
checks front/kitchen actions, and reservation writes run transactionally in
ListReservationsController. Both generic PATCH endpoints currently allow FOH
cancellation. Existing state/version/payment/refund/notification code stays intact.
No separate cancellation endpoint was found. Function enquiries and restaurant
ordering reads currently allow FOH; menu/users/configuration are ADMIN-only.
Timekeeping self/csrf is an existing all-staff capability (preserved); administrative
timekeeping endpoints remain ADMIN-only. Public restaurant availability stays public.

| Capability | ADMIN | FOH | BOH |
| --- | --- | --- | --- |
| Home/order monitoring | Yes | Yes | Yes |
| Orders/details | Yes | Yes | Yes |
| Accept/handover/completion | Yes | Yes | Yes |
| Prepare/ready | Yes | Yes | Yes |
| Cancel order | Yes, valid state only | 403 | 403 |
| Reservations/details/non-cancel transitions | Yes | Yes | 403 |
| Cancel reservation | Yes, valid state only | 403 | 403 |
| Functions/menu/ordering configuration/schedule/users/settings | Yes | 403 | 403 |

Compatibility inventory: prototype app/routes owns route visibility; Staff Web
AppRouter and StaffShell independently gate functions/ordering. Staff Web/PWA
share domains/ordering/model/orderModel.js and ReservationAdminPage; PWA does not
expose function/config management itself. Android Order.actions owns cancellation
presentation; Android reservations are an unimplemented capability screen and have
no mutation controls. No notification/polling/audio changes are needed. All API
payloads, CSRF, live sessions, payment checks and optimistic versions are preserved.

Deployment hold: deploy.yml triggers website/backend on backend/** or original
frontend/**; dedicated prototype workflow triggers prototype paths; Android workflow
triggers android-staff/**. A main push spanning these files would trigger multiple
pipelines. Do not commit/push/deploy without a separate coordinated approval.

## Implementation and compatibility

`StaffPermissions` is the shared cancellation/transition write policy, called by
the order service and reservation transactional controller before persistence.
SecurityConfig gates functions and restaurant configuration for ADMIN only.
The old front/kitchen transition split is intentionally replaced: both FOH and
BOH can accept, prepare, mark ready and complete handover. ADMIN retains all valid
states, including cancellation. Cancellation still requires
its original valid state, version, reason, payment reconciliation and refund rules.

Staff Web route guards/navigation no longer expose functions/ordering to FOH.
Its shared order model and reservation component also supply the existing PWA,
so that client inherits cancellation hiding without shell/audio/monitor changes.
Android's order action list hides cancellation for FOH; its existing submit guard
uses the same list, so forged local cancellation actions cannot submit. Android
reservation UI remains a capability placeholder with no reservation API/actions.
Local device sound/printer settings and personal timekeeping are not restaurant
administration; their existing behavior is unchanged. Backend administrative
functions remain restricted. Old installed clients may still display old controls
until updated; the new backend will reject forbidden calls with 403, never mutate.

Prototype routes/pages are retained for ADMIN. FOH has Home, Orders, Reservations;
BOH has Home and Orders. Header Manage and all six destinations remain ADMIN-only.
The previous FOH Online ordering contextual-navigation exception is superseded by
this explicitly requested policy. Sidebar/header/refresh styling is unchanged.

The source/protected baseline snapshots update only the intentionally authorized
compatibility changes; other recorded hashes remain fixed. Existing cancellation
regressions run as ADMIN, while FOH sign-out draft cleanup uses acceptance drafts.
The existing BOH kitchen queue now includes NEW alongside ACCEPTED/PREPARING/READY,
and BOH direct order detail is no longer limited to preparation states. This makes
acceptance and completed-order detail reachable without granting FOH-only endpoints
or exposing private customer contact/payment references. The existing staff order
mapping retains payment prerequisite fields. Payment verification permissions are
unchanged (ADMIN/FOH), and BOH acceptance/handover remains blocked until the same
payment prerequisites are met. All clients use the existing queue paths. No
notification engine, audio engine or polling interval changed. No migrations,
entities, DTOs, endpoint paths or workflow files changed.

## Verification

- Java 21 `mvn verify`: 494 discovered, 238 executed/passed, 256 skipped by
  existing environment/DB gates; zero failures/errors. Includes 9 new real
  security-chain/handler API tests using mocked persistence and notification
  collaborators (no providers). Tests cover ADMIN valid cancellation, FOH/BOH
  403s, FOH reservation transitions, BOH reservation denial, management denial,
  state/version rejection, CSRF/authentication, full acceptance/preparation/readiness/completion for both FOH and BOH,
  BOH NEW queue/detail reachability with contact privacy, paid-order refund obligations and audit actor/status.
- Staff Web/PWA npm suite: 237 passed, including existing audio contracts.
- Prototype npm suite: 141 passed, including independent route denial and all
  protected hashes outside the authorized compatibility changes.
- Prototype browser: 381 assertions and 100 responsive route renders; menu,
  order drafts/polling/payment and settings regressions passed with synthetic APIs.
- Existing Staff Web browser: order drafts/polling/conflicts/session cleanup and
  reservation availability/date/retry/session recovery passed with mocked APIs.
- Existing Staff PWA browser: 93 assertions passed, including BOH Pending-order acceptance and explicit FOH/BOH
  cancellation hiding plus existing responsive/audio/session behavior.
- Android Java 21 `./gradlew testDebugUnitTest --offline`: 104 passed.
- Both frontend production builds and 19 prototype deployment safeguard tests pass.
- Physical Android/iPad certification and live authenticated database writes were
  not performed; gated database integration tests are not claimed as verified.

## Approved coordinated deployment plan

1. Review this policy and the exact file list below, including compatibility
   updates. Capture the current backend, website and prototype release symlinks
   and Android artifact version before publishing anything.
2. Approve a coordinated client/backend release. A main push currently matches
   **all three** existing workflows: website/backend, prototype, Android. Do not
   push this work until those automatic deployment effects are explicitly approved
   or an approved release strategy prevents premature activation.
3. Prefer publishing the permission-presentation updates to clients first, then
   activate the matching backend authorization release in an approved maintenance
   window. Existing API payloads remain compatible; old clients receive 403 on
   newly forbidden calls until updated. Force no app/session/data migration.
4. Use established versioned releases/activation with health checks. No database
   changes or Nginx/DNS/TLS changes are needed. Verify ADMIN/FOH/BOH through both
   API and UI using development data; avoid provider transactions. Verify backend
   health, customer site, existing staff clients and prototype after activation.
5. Retain previous artifacts and activation targets. To roll back, restore the
   captured backend release via the established backend activation script and
   restore website/prototype symlinks atomically; restore the previous Android
   artifact if needed. Restart only the backend as required by its existing release
   procedure. No Flyway repair or database rollback is needed. Restoring the old
   backend also restores old FOH cancellation/configuration access and the narrower
   front/kitchen transition split; record those rollback tradeoffs.

The implementation validation above preceded deployment. The coordinated backend/shared
frontend, prototype and Android pre-release are now explicitly approved. Temporarily
pause the prototype workflow before the main push, await backend/shared frontend
success, then re-enable and dispatch the existing prototype workflow. Android builds
its pre-release without automatic tablet installation. Release outcomes are reported
separately after CI/CD verification. No deployment isolation is implemented here.

## Exact changed files

- `.github/workflows/android-staff.yml`
- `android-staff/app/src/main/java/au/com/nakornthai/staffapp/ordering/model/Order.java`
- `android-staff/app/src/test/java/au/com/nakornthai/staffapp/OrderWorkflowTest.java`
- `android-staff/app/src/test/java/au/com/nakornthai/staffapp/OrdersViewModelTest.java`
- `backend/src/main/java/au/com/nakornthai/ordering/changestatus/ChangeOrderStatusHandler.java`
- `backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersHandler.java`
- `backend/src/main/java/au/com/nakornthai/reservation/listreservations/ListReservationsController.java`
- `backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java`
- `backend/src/main/java/au/com/nakornthai/shared/security/StaffPermissions.java`
- `backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java`
- `backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java`
- `backend/src/test/java/au/com/nakornthai/payment/createpayment/CustomerPaymentReliabilityIntegrationTest.java`
- `backend/src/test/java/au/com/nakornthai/restaurant/orderingsettings/OrderingSettingsApiTest.java`
- `backend/src/test/java/au/com/nakornthai/shared/security/StaffAuthorizationTest.java`
- `docs/security/staff-rbac-refinement.md`
- `frontend/src/app/AppRouter.jsx`
- `frontend/src/domains/ordering/model/orderModel.js`
- `frontend/src/domains/ordering/model/orderModel.test.js`
- `frontend/src/domains/ordering/pages/OrdersAdminPage.jsx`
- `frontend/src/domains/reservation/pages/ReservationAdminPage.jsx`
- `frontend/src/domains/staff/components/StaffShell.jsx`
- `frontend/src/domains/staff/components/staffShell.test.js`
- `frontend/src/staff-pwa-proto/NAVIGATION.md`
- `frontend/src/staff-pwa-proto/app/routes.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderModel.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderModel.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/pages/OrdersAdminPage.jsx`
- `frontend/src/staff-pwa-proto/features/reservation/pages/ReservationAdminPage.jsx`
- `frontend/src/staff-pwa-proto/tests/isolation.test.js`
- `frontend/src/staff-pwa-proto/tests/navigation.test.js`
- `frontend/src/staff-pwa-proto/tests/ordersDrafts.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/protected-baseline.json`
- `frontend/src/staff-pwa-proto/tests/prototype.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/prototypeFixtures.mjs`
- `frontend/src/staff-pwa-proto/tests/source-baseline.json`
- `frontend/src/staff-pwa/features/orders/OrderBoard.jsx`
- `frontend/tests/e2e/ordersDrafts.browser.mjs`
- `frontend/tests/e2e/staffPwa.browser.mjs`
- `frontend/tests/e2e/staffPwaFixtures.mjs`
