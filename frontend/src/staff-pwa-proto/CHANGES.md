# Changed-file summary

Existing tracked files changed:

- `.gitignore`: ignore the separate prototype build output.
- `frontend/package.json`: add prototype-only dev/build/preview/unit/browser/install scripts; preserve existing commands and dependencies.

New application support:

- `frontend/vite.proto.config.js`: isolated entry, public directory, output and development server.
- `frontend/staff-pwa-proto/index.html`: separate document and manifest.
- `frontend/src/staff-pwa-proto/`: local workflow snapshots, independent shell/router/styles/services/PWA assets, tests and documentation.

Protected React Staff Web/PWA/Android/customer source files and backend contracts
were not modified. No dependency or lockfile change was made. Build artifacts are
ignored, not source changes.

The local workflow copies deliberately preserve existing contracts rather than
requiring shared-code changes. API/model tests are copied locally to exercise those
copies; browser suites include prototype adaptations of the existing Web tests.

Prototype deployment support also includes namespaced asset output, a scoped
Nginx include, release/rollback instructions and a non-destructive live smoke test.

## Exact file inventory

- `.gitignore`
- `frontend/package.json`
- `frontend/src/staff-pwa-proto/CHANGES.md`
- `frontend/src/staff-pwa-proto/DEPLOYMENT.md`
- `frontend/src/staff-pwa-proto/PARITY.md`
- `frontend/src/staff-pwa-proto/README.md`
- `frontend/src/staff-pwa-proto/StaffProtoApp.jsx`
- `frontend/src/staff-pwa-proto/VERIFICATION.md`
- `frontend/src/staff-pwa-proto/app/routes.js`
- `frontend/src/staff-pwa-proto/app/useProtoRoute.js`
- `frontend/src/staff-pwa-proto/components/Icon.jsx`
- `frontend/src/staff-pwa-proto/components/Sheet.jsx`
- `frontend/src/staff-pwa-proto/deploy/nginx.conf`
- `frontend/src/staff-pwa-proto/features/identity/api/identityApi.js`
- `frontend/src/staff-pwa-proto/features/identity/api/identityApi.test.js`
- `frontend/src/staff-pwa-proto/features/identity/components/LoginForm.jsx`
- `frontend/src/staff-pwa-proto/features/identity/model/AuthContext.jsx`
- `frontend/src/staff-pwa-proto/features/identity/pages/UsersPage.jsx`
- `frontend/src/staff-pwa-proto/features/menu/api/menuApi.js`
- `frontend/src/staff-pwa-proto/features/menu/api/menuApi.test.js`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuAdminLayout.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionAvailability.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionCategories.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionDetail.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionItems.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionList.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuCollectionOverview.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuImageEditor.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuItemEditor.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuItemList.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuItemOptionEditor.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuPhoto.jsx`
- `frontend/src/staff-pwa-proto/features/menu/components/MenuScheduleEditor.jsx`
- `frontend/src/staff-pwa-proto/features/menu/hooks/useItemOptionAdmin.js`
- `frontend/src/staff-pwa-proto/features/menu/hooks/useMenuAdminData.js`
- `frontend/src/staff-pwa-proto/features/menu/hooks/useMenuAdminForm.js`
- `frontend/src/staff-pwa-proto/features/menu/model/itemOptionPrices.js`
- `frontend/src/staff-pwa-proto/features/menu/model/itemOptionPrices.test.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuAdminNavigation.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuAdminNavigation.test.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuCollections.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuModel.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuOptions.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuOptions.test.js`
- `frontend/src/staff-pwa-proto/features/menu/model/menuPhotoLayout.js`
- `frontend/src/staff-pwa-proto/features/menu/pages/HomepageSettingsPage.jsx`
- `frontend/src/staff-pwa-proto/features/menu/pages/MenuAdminPage.jsx`
- `frontend/src/staff-pwa-proto/features/ordering/api/orderApi.js`
- `frontend/src/staff-pwa-proto/features/ordering/api/orderApi.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/components/OrderItemOptions.jsx`
- `frontend/src/staff-pwa-proto/features/ordering/model/cartModel.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/cartModel.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/cartReducer.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderLinks.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderLinks.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderModel.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/orderModel.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/staffOrderDrafts.js`
- `frontend/src/staff-pwa-proto/features/ordering/model/staffOrderDrafts.test.js`
- `frontend/src/staff-pwa-proto/features/ordering/pages/OrdersAdminPage.jsx`
- `frontend/src/staff-pwa-proto/features/payment/api/paymentApi.js`
- `frontend/src/staff-pwa-proto/features/payment/api/paymentApi.test.js`
- `frontend/src/staff-pwa-proto/features/payment/components/PaymentReviewPanel.jsx`
- `frontend/src/staff-pwa-proto/features/reservation/api/functionApi.js`
- `frontend/src/staff-pwa-proto/features/reservation/api/functionApi.test.js`
- `frontend/src/staff-pwa-proto/features/reservation/api/reservationApi.js`
- `frontend/src/staff-pwa-proto/features/reservation/api/reservationApi.test.js`
- `frontend/src/staff-pwa-proto/features/reservation/pages/ReservationAdminPage.jsx`
- `frontend/src/staff-pwa-proto/features/restaurant/api/restaurantApi.js`
- `frontend/src/staff-pwa-proto/features/restaurant/api/restaurantApi.test.js`
- `frontend/src/staff-pwa-proto/features/restaurant/model/integrationSettings.js`
- `frontend/src/staff-pwa-proto/features/restaurant/model/integrationSettings.test.js`
- `frontend/src/staff-pwa-proto/features/restaurant/model/settingsDraft.js`
- `frontend/src/staff-pwa-proto/features/restaurant/model/settingsDraft.test.js`
- `frontend/src/staff-pwa-proto/features/restaurant/pages/IntegrationSettingsPage.jsx`
- `frontend/src/staff-pwa-proto/features/restaurant/pages/OnlineOrderingPage.jsx`
- `frontend/src/staff-pwa-proto/features/restaurant/pages/RestaurantSchedulePage.jsx`
- `frontend/src/staff-pwa-proto/features/staff/components/StaffPresentation.jsx`
- `frontend/src/staff-pwa-proto/features/staff/pages/FunctionEnquiriesPage.jsx`
- `frontend/src/staff-pwa-proto/features/staff/pages/StaffDashboardPage.jsx`
- `frontend/src/staff-pwa-proto/features/staff/pages/StaffMenuPage.jsx`
- `frontend/src/staff-pwa-proto/main.jsx`
- `frontend/src/staff-pwa-proto/public/staff-pwa-proto/icon-192.png`
- `frontend/src/staff-pwa-proto/public/staff-pwa-proto/icon-512.png`
- `frontend/src/staff-pwa-proto/public/staff-pwa-proto/icon.svg`
- `frontend/src/staff-pwa-proto/public/staff-pwa-proto/manifest.webmanifest`
- `frontend/src/staff-pwa-proto/pwa/buildWorker.js`
- `frontend/src/staff-pwa-proto/pwa/register.js`
- `frontend/src/staff-pwa-proto/services/mutations.js`
- `frontend/src/staff-pwa-proto/styles/prototype.css`
- `frontend/src/staff-pwa-proto/tests/README.md`
- `frontend/src/staff-pwa-proto/tests/adminSettings.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/browserHarness.mjs`
- `frontend/src/staff-pwa-proto/tests/install.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/isolation.test.js`
- `frontend/src/staff-pwa-proto/tests/liveSmoke.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/menuAdmin.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/ordersDrafts.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/protected-baseline.json`
- `frontend/src/staff-pwa-proto/tests/prototype.browser.mjs`
- `frontend/src/staff-pwa-proto/tests/prototypeFixtures.mjs`
- `frontend/src/staff-pwa-proto/tests/reference.test.js`
- `frontend/src/staff-pwa-proto/tests/run-browser.mjs`
- `frontend/src/staff-pwa-proto/tests/source-baseline.json`
- `frontend/src/staff-pwa-proto/tests/worker.test.js`
- `frontend/staff-pwa-proto/index.html`
- `frontend/vite.proto.config.js`

## Dedicated CI/CD additions

- `.github/workflows/deploy-staff-pwa-proto.yml`: independent main push/manual pipeline, locked dependencies, prototype tests/build/installation checks, verified SSH upload, atomic activation and summary.
- `.github/workflows/deploy.yml`: excludes prototype-only paths; workflow-only edits no longer automatically publish existing applications. Other deployment jobs/steps remain unchanged.
- `frontend/src/staff-pwa-proto/deploy/release.py`: strict artifact/checksum validation, read-only smoke checks, protected-state snapshots, remote locking, atomic activation and rollback.
- `frontend/src/staff-pwa-proto/deploy/test_release.py`: artifact corruption, namespace, revision, activation and rollback tests in temporary directories.
- `frontend/src/staff-pwa-proto/deploy/test_workflow_routing.py`: prototype-only/shared dependency/original application trigger-isolation checks.
- `frontend/src/staff-pwa-proto/deploy/.gitignore`: ignores Python test bytecode.
- `DEPLOYMENT.md`, `README.md`, `tests/README.md`, `VERIFICATION.md`: automated release operation, security, rollback and validation instructions/results.
