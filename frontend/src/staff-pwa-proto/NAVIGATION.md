# Navigation parity — before and after

Inventory recorded before navigation changes. All ten destinations previously
appeared in both sidebar and Manage. Route role lists remain the authority for
both visibility and direct-route access; no permissions or feature code change.

| Route | Existing roles | Preserved functionality | New placement |
| --- | --- | --- | --- |
| `#/staff` | ADMIN, FOH, BOH | Dashboard, summaries, session | Operations; bottom Home |
| `#/staff/orders` | ADMIN, FOH, BOH | Queue/history, linked order query, transitions, drafts, payment verification/review | Operations; bottom Orders |
| `#/staff/reservations` | ADMIN, FOH | Filters, notes, statuses, availability | Operations |
| `#/staff/functions` | ADMIN, FOH | Filters, pagination, agreed date, notes, status updates | Operations |
| `#/staff/ordering` | ADMIN, FOH | Availability, pause/message controls; FOH remains read-only | Manage |
| `#/staff/menu` and descendants | ADMIN | Items/create/overview/pricing/options/images/membership; collections/create/overview/items/categories/availability | Manage (Menu management) |
| `#/staff/homepage` | ADMIN | Collection selection, order, save/cancel | Manage |
| `#/staff/restaurant` | ADMIN | Timezone, opening windows and closures CRUD | Manage |
| `#/staff/users` | ADMIN | Create/edit/reset/enable/disable accounts | Manage |
| `#/staff/settings` | ADMIN | Business settings, integrations, tests, configuration audit, draft guards | Manage |

No additional top-level destinations were found. Menu descendants and order query
links remain resolved by the unchanged route resolver. All ten routes (100%) remain
available to the same roles. Anonymous access remains handled by the existing auth
boundary. BOH has no management destinations and therefore no Manage button.
The narrow-screen drawer mirrors Operations only. Sign-out remains available in
the header and sheets. Existing menu unsaved-change interception remains intact.

Verification: prototype unit tests, full existing prototype browser regressions
(including menu, orders, reservations and settings), responsive route checks,
management selection/close, keyboard Escape/focus return, all-role visibility and
direct-route denial; production build. These use synthetic APIs, not live writes.

Results: 141 unit tests and 19 deployment safeguards passed; production build
passed. Browser suites passed: 370 prototype assertions/100 responsive route renders,
menu administration, order drafts/polling/payment contracts and full settings
validation/drafts/conflicts/history/sign-out regression. Settings navigation tests
now open Manage before selecting relocated management links. No route, role list,
feature page, API module or backend contract was modified. The dialog cleanup now
closes its captured element before returning focus after unmount.

Changed files: `StaffProtoApp.jsx` (grouped rendering), `app/routes.js` (presentation
selectors and menu label), `components/Sheet.jsx` (focus return),
`tests/navigation.test.js` (complete partition/roles), `tests/prototype.browser.mjs`
(navigation/role/responsive assertions), `tests/adminSettings.browser.mjs`
(relocated link assertions), and this parity record.
