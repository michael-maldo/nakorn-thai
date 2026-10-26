# Staff Web → staff-pwa-proto parity matrix

Status terminology: **implemented / mock-verified** means the workflow is present
and its actions are exercised by prototype API/model/browser tests. It does not
claim real-backend or physical-device certification. Every row inherits the
cross-cutting route/role/error/validation/isolation checks described below.

Routes are relative to `/staff-pwa-proto/`; matching Staff Web hashes are relative
to `/`. Paths under `features/` are prototype-local copies of the corresponding
`frontend/src/domains/` modules. All API paths below begin with `/api`.
A = ADMIN, F = FOH, B = BOH.

Test evidence: **U** = prototype Node contracts; **B** = comprehensive browser;
**M** = menu workflow browser; **O** = order draft/workflow browser;
**S** = settings browser; **I** = production installation browser.

| Staff Web reference and route | Prototype implementation | Roles | Actions and API contract | Evidence/status |
| --- | --- | --- | --- | --- |
| `LoginForm`, `AuthContext`, identity client | `features/identity/` + `main.jsx` | A/F/B | CSRF/login, refresh rotation/retry, logout, required credentials, expired-session/access boundary | U/B/O/S; implemented / mock-verified |
| `AppRouter`, `ProtectedRoute`, `StaffShell` | `app/routes.js`, `app/useProtoRoute.js`, `StaffProtoApp.jsx` | Per destination | All 10 staff destinations; direct access; role filtering/denial; unknown route; independent router | U/B/S; implemented / mock-verified |
| `StaffDashboardPage`, `#/staff` | `features/staff/pages/StaffDashboardPage.jsx` | A/F/B | Welcome, identity and link to role-appropriate orders; no dashboard API is used by Web | B; implemented / mock-verified |
| `OrdersAdminPage`, `#/staff/orders` | `features/ordering/pages/OrdersAdminPage.jsx` | A/F/B | GET `/staff/foh/orders?history=false` or `/staff/kitchen/orders`; refresh/poll, filters, counts, limit, loading/stale/error/empty; kitchen privacy | U/B/O; implemented / mock-verified |
| Same, history selector | Same | A/F | GET `/staff/foh/orders?history=true`; completed/cancelled orders placed in last 24h; filter/refresh; no kitchen history | B/O; implemented / mock-verified |
| Same, `?order=<uuid>`, `orderLinks` | Same + local link parser | A/F; restricted B detail | GET `/staff/orders/{id}`; UUID validation, invalid/unavailable state, return to queue | U/B/O; implemented / mock-verified |
| Shared `StaffOrderCard` lifecycle | Prototype-local `StaffOrderCard` | A/F accept/cancel/handover; A/B preparation/ready | PATCH `/staff/orders/{id}/status`; fresh `/staff/orders/csrf`; original version, pickup 5–180m, required reason ≤500, collection checkbox, payment gates | U/B/O; implemented / mock-verified |
| Order status/payment drafts | Local `staffOrderDrafts` and workspace | Per action | Multiple independent drafts; retain across refresh/reorder/filter/history; departed rows block writes; conflict/discard; per-order saving/focus/session cleanup | U/O; implemented / mock-verified |
| Order payment visibility/verification | Local card and order API | A/F | POST `/staff/payments/{id}/payid-confirm` with bank reference/receipt/version; POST `/{id}/check` for existing PayPal verification; no provider changes | U/B/O; implemented / mock-verified |
| `PaymentReviewPanel` under orders | Local `features/payment/components/PaymentReviewPanel.jsx` | A/F | GET `/staff/payments/review`; unresolved obligations/events; limits/empty/error; order links; no refund mutation | B/U; implemented / mock-verified |
| `ReservationAdminPage`, `#/staff/reservations` | Local reservation page/API | A/F | Availability/timezone initialization; date GET `/staff/reservations?date=…`; refresh/retry; versioned PATCH `/{id}` with ≤500-character staff note; all requested/confirmed transitions | U/B; implemented / mock-verified |
| `FunctionEnquiriesPage`, `#/staff/functions` | Local enquiry page/API | A/F | GET `/staff/functions?status=…&page=…`; ALL/status filters; previous/next; PATCH `/{id}` version/status/date/note; agreed-date server validation; audit attribution; no automatic notification | U/B; implemented / mock-verified |
| `OnlineOrderingPage`, `#/staff/ordering` | Local restaurant page/API | A/F read, A write | GET/PUT `/staff/restaurant/ordering`; configuration/hours/pause reasons, pause/resume, optional ≤300-character message, version | U/B; implemented / mock-verified |
| `MenuItemList`, `#/staff/menu[/items]` | Local menu list | A | GET `/staff/menu/items`; name/category search, current/all/publication filters, counts/prices/memberships, empty/no-match | U/B/M; implemented / mock-verified |
| `MenuItemEditor`, `/items/new`, `/{id}/overview` | Local item editor | A | POST/PUT `/staff/menu/items[/{id}]`; name/slug/description/category/status/availability/order; required/pattern/length checks; cancel; DELETE `/{id}?version=…` archive and status restoration | U/B/M; implemented / mock-verified |
| Item `/{id}/pricing` | Local item editor | A | Existing variation prices; standard price creation; AUD decimal conversion/validation; same versioned item PUT | U/B/M; implemented / mock-verified |
| Item `/{id}/options`, `MenuItemOptionEditor` | Local options editor/hook/model | A | GET option groups/assignments; create assigned group; reuse/edit/remove assignment; required/multiple rules; zero vs blank item-specific price; shared choice create/edit/active state; versioned APIs | U/B; implemented / mock-verified |
| Item `/{id}/collections` | Local item editor | A | Select/remove collection IDs through item PUT; preserve retained membership settings | U/B/M; implemented / mock-verified |
| Item `/{id}/images`, `MenuImageEditor` | Local image editor/photo layout | A | Multipart POST `/staff/menu/items/{id}/image`; JPEG/PNG and 8MB client check, alt/focus/zoom/rotation/preview/reset/cancel; independent save/version | U/B/M; implemented / mock-verified |
| `MenuCollectionList`, `/menu/collections` | Local collection list | A | GET `/staff/menu/collections`; search, status/active/effective availability, item/schedule counts | U/B/M; implemented / mock-verified |
| Collection new/overview | Local `MenuCollectionOverview` | A | POST/PUT collections; name/slug/description/status/active/order; archive/inactive semantics; cancel | U/B/M; implemented / mock-verified |
| Collection `/{id}/items` | Local `MenuCollectionItems` | A | PUT/DELETE collection item membership; search/add existing/edit/remove; placement/order; nullable/zero base-price override, version, confirmation | U/B/M; implemented / mock-verified |
| Collection `/{id}/categories` | Local `MenuCollectionCategories` | A | POST/PUT/DELETE category placements; existing canonical categories; order; removal blocked while assigned; confirmation | U/B/M; implemented / mock-verified |
| Collection `/{id}/availability` | Local `MenuCollectionAvailability` | A | Versioned collection PUT; broad UTC validity instants, restaurant-local cutoff, IANA collection timezone; server effective availability and reasons | U/B/M; implemented / mock-verified |
| Collection schedule editor | Local `MenuScheduleEditor` | A | POST/PUT/DELETE `/{id}/schedules`; weekly/specific-date/all-day/overnight; active/order; confirmation and server errors | U/B/M; implemented / mock-verified |
| `HomepageSettingsPage`, `#/staff/homepage` | Local homepage editor | A | GET/PUT `/staff/menu/homepage`; max 20, select/reorder/clear/cancel, version conflict/reload, published visibility explanation | U/B; implemented / mock-verified |
| `RestaurantSchedulePage`, `#/staff/restaurant` | Local schedule page | A | GET `/staff/restaurant/schedule`; PUT `/settings` timezone; POST/PUT/DELETE `/hours`; required times/order/active, overnight semantics and versioned deletes | U/B; implemented / mock-verified |
| Same, closed dates | Same | A | POST/PUT/DELETE `/closed-dates`; local date, optional ≤500-character reason; reload/cancel/version; full-day closure semantics remain server-owned | U/B; implemented / mock-verified |
| `UsersPage`, `#/staff/users` | Local identity user page/API | A | GET/POST `/identity/users`; PUT `/{id}`; username pattern, role/enabled, create/reset password, blank preserves password, cancel/reload, versions and server errors | U/B; implemented / mock-verified |
| `IntegrationSettingsPage`, SETTINGS | Local configuration page/models | A | GET `/staff/restaurant/configuration`; PUT `/configuration/SETTINGS`; business URL, ordering/reservations/verification/notifications/payment flags and staff recipients; dependency warnings, drafts/cancel/discard/conflicts | U/B/S; implemented / mock-verified |
| Same, PAYPAL/PAYID/TWILIO/SMTP | Same | A | Existing category PUT, masked secret keep/replace/clear, source/state metadata; explicit test POST and SMTP test-email; pending-payment/live/credential-clear confirmations | U/S; implemented / mock-verified |
| Same, configuration audit | Same | A | GET `/configuration/audit`; most recent 100 events; audit failure separated from confirmed save result | U/S; implemented / mock-verified |
| Menu/settings dirty guards and Web history | Prototype-local guards/router | A | Guard links, native Back/Forward, immediate Stay/Discard, superseded approval, in-flight save and sign-out/unload | U/M/S; implemented / mock-verified |
| Web responsive workspace | Android-inspired prototype shell/styles | A/F/B | Sidebar/72px rail, hamburger/modal navigation, footer/Manage, 48px targets, scrollable cards/forms, responsive menu tables | B/M; implemented / Chromium-verified |
| PWA install/update (new platform feature) | Prototype manifest/worker/register | A/F/B | Separate scope/cache/icons; explicit update; immutable JS/CSS only; online API access | U/I; implemented / Chromium-verified |

## Cross-cutting validation

- All staff routes and menu detail sections render at 810×1080, 1080×810,
  1360×1000, 768×1024 and 390×844 in the comprehensive Chromium suite.
- ADMIN/FOH/BOH navigation and direct denied routes are checked before protected
  requests. Backend authorization still owns enforcement; browser tests are mocked.
- API/model tests retain HTTP methods, CSRF handling, payload versions and error
  messages. Order tests check identity/session binding through refresh/401 retry.
- Double-submit tests check a single write; management coalescing starts before
  CSRF acquisition. Polling does not introduce another monitor in dashboard/shell.
- Menu/settings tests exercise validation, 409, failed authoritative reload,
  retained drafts, independent sections, discarded secrets and unsaved navigation.
- Source fingerprint tests protect original PWA/Android/Web source modules.

## Intentional limits inherited from Staff Web

These are not missing prototype migrations:

- No all-time history/export/sales report or implemented analytics screen exists
  in the active Staff Web route tree; overview is a welcome/navigation page.
- The item editor cannot create arbitrary additional variations, edit canonical
  food declarations or provide canonical-category CRUD.
- Collection deletion and standalone option-group administration are backend
  capabilities without corresponding active Web UI controls.
- Financial review does not issue refunds; function follow-up sends no notification.
- Timekeeping is native-only in this scope.
- Existing PWA audio/recovery/diagnostics and Epson functionality are not Staff Web
  features to copy into this independent prototype. Hardware printing stays off.

## Remaining verification, not claimed complete

Real disposable-database integration for server-side rules (including last-admin,
UTF-8 password byte limit, timezone scheduling, notification delivery and image
pixel limits); real provider calls; physical iPad Safari and Android tablet testing;
OS installation/update and browser-specific keyboard behavior. No live mutation
or hardware/provider integration tests were performed.


## Shared Orders extraction

The prototype now consumes the original Staff PWA operational Orders implementation through `src/shared/staff-orders`. See [architecture, parity, validation and CI/CD impact](../shared/staff-orders/README.md). Both shells retain independent routing, authentication adapters, preferences, manifests and workers. Coordinated release through the existing GitHub Actions workflows is approved; local validation is documented in the linked report.
