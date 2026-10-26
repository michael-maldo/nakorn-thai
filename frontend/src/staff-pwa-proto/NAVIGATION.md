> Approved RBAC refinement: see [policy and verification](../../../docs/security/staff-rbac-refinement.md). The historical FOH Online ordering exception below is superseded; functions and ordering configuration are now ADMIN-only.

# Navigation parity — before and after

Inventory recorded before navigation changes. All ten destinations previously
appeared in both sidebar and Manage. Route role lists remain the authority for
both visibility and direct-route access; no permissions or feature code change.

| Route | Existing roles | Preserved functionality | New placement |
| --- | --- | --- | --- |
| `#/staff` | ADMIN, FOH, BOH | Dashboard, summaries, session | Operations (sidebar/drawer) |
| `#/staff/orders` | ADMIN, FOH, BOH | Queue/history, linked order query, transitions, drafts, payment verification/review | Operations (sidebar/drawer) |
| `#/staff/reservations` | ADMIN, FOH | Filters, notes, statuses, availability | Operations |
| `#/staff/functions` | ADMIN | Filters, pagination, agreed date, notes, status updates | Operations |
| `#/staff/ordering` | ADMIN | Availability, pause/message controls | Header Manage |
| `#/staff/menu` and descendants | ADMIN | Items/create/overview/pricing/options/images/membership; collections/create/overview/items/categories/availability | Header Manage (Menu management) |
| `#/staff/homepage` | ADMIN | Collection selection, order, save/cancel | Header Manage |
| `#/staff/restaurant` | ADMIN | Timezone, opening windows and closures CRUD | Header Manage |
| `#/staff/users` | ADMIN | Create/edit/reset/enable/disable accounts | Header Manage |
| `#/staff/settings` | ADMIN | Business settings, integrations, tests, configuration audit, draft guards | Header Manage |

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

## Header Manage and contextual refresh refinement

Pre-change inventory: routes/role lists/nested workflows remain exactly those in
the matrix above. Bottom Home/Orders shortcuts are removed; both remain in
Operations. ADMIN's six Manage links move behind the header Manage button.
FOH's existing read-only Online ordering link moves to an explicitly labeled
contextual sidebar/drawer section; BOH gains nothing. This is a placement exception,
not a role-rule change. Existing dialog Escape, focus return and menu draft guards
remain applicable. Sign-out and session restoration remain unchanged.

| Existing control | Original behavior/state retained | New position |
| --- | --- | --- |
| Orders Refresh | loadRef.current(), current queue/filter/drafts, loading/write disable; 15s polling unchanged | Bottom-right action bar |
| Refresh bookings | load(), selected date, availability/loading/write disable | Bottom-right action bar |
| Refresh enquiries | increment reload, current status/page, busy disable | Bottom-right action bar |
| Reload accounts | reload(), existing draft reset semantics, busy disable | Bottom-right action bar |
| Refresh data (menu) | existing allowMenuNavigation guard then state.reload | Bottom-right action bar |
| Refresh settings (homepage) | existing reload/selection behavior, busy disable | Bottom-right action bar |
| Refresh status (ordering) | existing reload, busy disable | Bottom-right action bar |
| Reload schedule | existing reload, busy disable | Bottom-right action bar |
| Refresh configuration | existing dirty discard confirmation, request/version protections | Bottom-right action bar |
| Retry availability | reservation prerequisite/error-specific recovery | Remains beside its error |
| Reload options | local option editor close/pending reset and dirty guard | Remains in option editor |

No refresh is added to Home. Save/Cancel/Submit/destructive actions stay in their
forms. The shared bar portals each original button with its original handler and
props, rather than registering callbacks or copying page state. The bar has no
content on pages without refresh and is hidden there. Inline error retries and
section-local reloads remain contextual, rather than becoming duplicate page refreshes.

Verification results for this refinement: 141 unit tests, 19 deployment safeguard
tests, 373 prototype browser assertions and 100 responsive route renders passed.
Existing menu, order-draft/polling/payment and settings browser suites passed.
Production build passed. The iPad portrait screenshot was visually inspected.
All nine page diffs reduce exactly to their previous source after removing the
PageAction import/wrapper; original button handlers/props and feature logic match.
Browser tests explicitly check selected-date booking refresh and retained notes,
FOH desktop/drawer ordering access, ADMIN header Manage/Escape focus, no bottom
navigation, empty-bar hiding and bottom-right alignment across five viewports.

Confirmed transition policy: ADMIN, FOH and BOH all have every
valid non-cancellation order transition. Only ADMIN can cancel. Both operational
roles complete acceptance → preparation → readiness → handover/completion. BOH
NEW orders are included in its existing kitchen queue, and the Status filter now
includes NEW. Customer-contact privacy and payment verification permissions stay
unchanged. The shared Staff Web/PWA and Android action models match this policy.
Corrected browser verification: 381 assertions and 100 responsive route renders,
including full end-to-end synthetic order lifecycles for FOH and BOH.
