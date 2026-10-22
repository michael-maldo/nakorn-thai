# Nakorn Thai Staff iPad PWA

## Entry and implementation map

Install from **`https://<existing-frontend-origin>/staff/`** (the documented deployment
origin is `https://nakorn-thai.tech-labs.dev/staff/`; live availability must be verified
after deployment). Safari → Share → Add to Home Screen → enable **Open as Web App**
→ Add. Open it, sign in, then tap **Enable Sound** and confirm the test is audible.
No Apple developer account, native build or App Store distribution is needed.

The existing Vite build now emits both `dist/index.html` and `dist/staff/index.html`.
The customer entry, hash router and customer cart are retained. `/staff/` is a separate
React root with its own manifest and service worker scope; it uses ordinary hash
navigation internally. No backend, database, Android or printer configuration changes.

| Owning files | Purpose |
| --- | --- |
| `frontend/staff/index.html`, `public/staff/` | Dedicated entry, standalone manifest, icons |
| `frontend/src/staff-pwa/main.jsx`, `StaffApp.jsx` | Existing auth provider/login form, role guard, Android-style shell and navigation |
| `staff-pwa/components/` | Native accessible dialog, Android vector paths adapted to SVG |
| `staff-pwa/features/orders/` | Role-filtered monitor, summaries, board, details and existing status/payment controls |
| `staff-pwa/features/notifications/alerts.js` | Silent initial baseline, duplicate suppression, gesture-enabled Web Audio |
| `staff-pwa/features/printing/` | Disabled ePOS adapter, two dockets, confirmed-acceptance guard and printer management |
| `staff-pwa/preferences.js`, `staff.css` | Non-sensitive device preferences and tablet layout |
| `staff-pwa/pwa/` | Static-only service worker generation and explicit update prompt |
| `staff-pwa/tests/core.test.js`, `frontend/tests/e2e/staffPwa*` | Unit and synthetic browser verification |
| `frontend/src/domains/ordering/api/orderApi.js` | Optional cancellation signal on existing serialized staff reads |
| `frontend/vite.config.js`, `package.json` | Multiple entries, worker generation, tests and build version |
| `.github/workflows/deploy.yml` | Frontend verification; skip backend build/activation for frontend-only pushes |

Paths abbreviated as `staff-pwa/` above are under `frontend/src/`.

## Android reference and honest capability differences

Reference inspected: `android-staff/app/src/main/java/au/com/nakornthai/staffapp/`
and `app/src/main/res/`. These sources are authoritative for this checkout; the
physical installed Android APK may differ.

| Android implementation | PWA mapping |
| --- | --- |
| `app/MainActivity`, `StaffApplication`, `AppGraph`; `activity_main.xml` | Persistent white sidebar, hamburger, top identity, monitoring, footer and Manage dialog |
| `identity/ui/LoginFragment`, `AuthViewModel`, `identity/data/IdentityRepository` | Centered staff sign-in using the existing browser identity contract |
| `ordering/ui/OrdersFragment`, `OrderAdapter`, `OrderPresentation`; `fragment_orders.xml`, `item_order.xml`, `view_order_column.xml` | Pending/Accepted/Preparing/Ready summaries, tabs and rounded order cards |
| `OrderDetailDialog`, `OrderActionDialog`, `OrdersViewModel` | Modal snapshot details and versioned actions; React `StaffOrderCard` reuses the existing validated confirmation/payment forms |
| `ordering/monitoring/OrderMonitor`, `NewOrderTracker`, `BoardState` | 10-second foreground polling; silent first snapshot; distinct seen/unacknowledged IDs |
| `analytics/ui/AnalyticsFragment` | Live role-filtered queue metrics, without invented historical analytics |
| `shared/navigation/ManageSheet`, `res/menu/footer.xml` | Home/Analytics/Manage footer, management sheet and sign-out |
| `printing/ui/PrinterManagementFragment`, `PrinterManagementViewModel` | ADMIN-only local printer profile, diagnostics, test control and explicit verification status |
| `res/values/colors.xml`, `dashboard_colors.xml`, `dimens.xml`, drawable icons | Original green/mint/amber/blue palette, 14px cards, 208px expanded / 72px collapsed rail and source vector paths |
| `reservation/ui/ReservationsFragment` | Android is a placeholder; PWA reuses the implemented React reservation manager for ADMIN/FOH |
| `timekeeping/*`, linked `android-timekeeping.md` | Timekeeping remains in Android; no duplicate attendance implementation is claimed |

The authoritative Android docket implementations are
`printing/formatting/OrderDocketFormatter.java` and `OnlineOrderDocketFormatter.java`,
with acceptance/reprint orchestration in `printing/KitchenDocketService.java` and
`ordering/ui/OrdersViewModel.java`. The PWA ports their two distinct layouts into
Epson XML: kitchen text excludes contact/payment information; detailed output retains
centered branding, enlarged order reference, CUSTOMER INFORMATION, FULFILLMENT,
PAYMENT INFORMATION, ORDER ITEMS, ORDER SUMMARY, CUSTOMER INSTRUCTIONS and END OF
ORDER, with bold allergy/instruction options. Paper wrapping supports the Android
32/48-column conventions for 58/80mm. The backend's `StaffOrderResponse` supplies
email, fulfillment, line totals, subtotal, payment status and a safe payment reference.
No public DTO or provider credentials are exposed. The PWA shows those extra fields
only in authorized front-of-house details. Bluetooth/discovery are not browser ports.

## Routes, layout and operation

- `/staff/#/orders` (default): order dashboard and detail/action dialogs.
- `/staff/#/analytics`: live counts from the same authorized monitor.
- `/staff/#/reservations`: existing React reservation workflow; ADMIN/FOH only.
- `/staff/#/printers`: local printer management; ADMIN only.
- `/staff/#/settings`: alerts, installation, version, session controls.
- Manage → Full staff management opens the existing `/#/staff` workspace in a new tab.
  This preserves its routing, menu draft protection and administrative functionality.

The iPad 9th generation uses an 810×1080 CSS viewport in portrait and 1080×810 in
landscape at default scaling. The sidebar collapses to a rail; below 840px it becomes
a modal drawer. The board selects one status at narrow **content** widths and shows
four columns at 960px. Collapse the rail in landscape to make space for four columns.
Safe-area padding, touch targets of at least 48px, readable form text, a scrollable
native modal, focus restoration and visual-viewport keyboard fitting are included.
Portrait, landscape, split view and larger desktop widths are supported by layout;
physical Safari keyboard, text scaling and VoiceOver still require device review.

Customers create orders; staff manage existing server orders. Backend role checks,
versions, payment verification and status semantics remain authoritative. BOH receives
only the kitchen queue and no contacts/payment controls or pending-order access.
Writes use the established API's CSRF token and principal/session guard. Duplicate
submissions are rejected synchronously. Failed or uncertain actions block further
writes until an explicit detail reload; no automatic mutation retry is introduced.
The existing API client retains its established single retry after an explicit 401
and successful refresh. Read cancellation bounds order monitoring requests to 15s.
Queue reads are capped at 200 by the existing backend, with a visible cap notice.

Access credentials remain in memory; refresh credentials remain in the existing
HttpOnly cookie. No access/refresh tokens, passwords, customer data, print payloads or
pending writes are persisted by the PWA. Browser session restoration uses the existing
12-hour staff session, subject to server expiry/revocation and Safari cookie retention.
An invalid/failed refresh returns to sign-in and removes protected dialogs and orders.
Device preferences contain only sidebar state, alert settings and printer configuration.

## Alerts and browser limits

Web Audio is activated only by a staff tap. Enable Sound and Test Sound resume the
AudioContext and play the selected double beep or chime, using a clamped gain setting.
A suspended/interrupted context returns the visible indicator to Sound not enabled.
There is no persisted claim that audio is unlocked after a restart. A new `NEW` ID
following the silent baseline produces one foreground sound and an acknowledgement
banner. Failed reads do not reset the seen-ID set. Acknowledging does not accept.
Monitoring survives route changes and pauses while hidden, then refreshes on return.
The first snapshot after a full reload/login is deliberately silent, matching Android.

The PWA cannot override physical volume, Silent Mode, Focus, locked-screen behavior
or iPadOS background suspension. Keep it visible and the iPad awake during service.
A successful Web Audio call does not prove the speaker is audible. Test on the actual
iPad at shift start, and after app switching or audio interruption.

Web Push is supported for installed Home Screen web apps on iPadOS 16.4+, with
permission requested from user interaction. It needs authenticated subscription
registration, an application push sender and server-side revocation/authorization.
Those do not exist in this implementation, so **background push is not implemented**.
No notification permission is requested and no background/repeating alarm is promised.
See [WebKit's Home Screen push documentation](https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/)
and [Apple's push delivery guidance](https://developer.apple.com/documentation/usernotifications/sending-web-push-notifications-in-web-apps-and-browsers).
[WebKit audio interruption reports](https://bugs.webkit.org/show_bug.cgi?id=273511)
reinforce the need for device testing after foreground recovery.

## Epson research and integration boundary

Epson lists **Wi-Fi/Ethernet TM-m30II** support for ePOS-Print XML. Its service accepts
SOAP/XML POST at `/cgi-bin/epos/service.cgi?devid=local_printer&timeout=10000` and returns
success/error/status. The manual specifies text, feed and cut operations, including
`cut type="feed"`. The adapter uses these commands; physical partial cutting remains
a hardware check. Source: [official ePOS-Print XML manual](https://files.support.epson.com/pdf/pos/bulk/epos-print_xml_um_en_rev_af.pdf)
(sections Operating Environment, Executing Print Service and TM-m30II).
[Epson's model-specific development page](https://www.epson.jp/products/receiptprinter/tmm302/feature_3.htm)
also identifies LAN-only XML availability. This supports an integration candidate,
not proof that the restaurant's exact variant/firmware or iPadOS 26.x works.

Configure an explicit HTTPS printer origin and registered device ID. HTTP examples
use port 80; this HTTPS application requires a working HTTPS endpoint (normally
443; confirm on the actual device). Do not assume ePOS-Device/WebSocket ports
8008/8043 are the XML service port. Do not change existing ePOS, network, certificate,
firmware or Wix settings during development. Verify existing settings read-only
through the approved printer utility/WebConfig first. If ePOS-Print is disabled,
enabling it is a separately reviewed operational change, not something the app does.

Safari must trust the printer's certificate chain, valid dates and hostname/IP SAN.
An IP URL requires a matching IP SAN; a hostname URL requires working local DNS.
A self-signed/untrusted certificate or an HTTP-only endpoint blocks this HTTPS app.
See [Epson’s certificate configuration guidance](https://www.epson.co.uk/en_GB/faq/KA-01775)
and [TM-m30II technical reference](https://download4.epson.biz/sec_pubs/bs/pdf/TM-m30II_trg_en_revG.pdf).
The printer must also allow the application's cross-origin XML/SOAP request and
preflight; browser local-network permission/policy and Wi-Fi isolation can block LAN
access. Generic fetch failures do not identify which layer failed: inspect Safari's
remote inspector and certificate details to report the precise device-specific cause.
There is no certificate bypass, credential forwarding, raw socket, discovery scan,
backend LAN proxy or silently installed print bridge.

`VITE_STAFF_EPOS_ENABLED` defaults to false. The deployment workflow explicitly builds
with `false`, so local preferences cannot enable production printing. Even in an
experimental enabled build, automatic printing defaults off and ADMIN configuration
is required. ADMIN/FOH can use the configured acceptance/reprint workflow; BOH manual
reprints force the kitchen layout and cannot auto-print acceptance. Test print sends one job after a deliberate confirmation. Check endpoint
sends an empty, no-feed/no-cut service document; it does not validate physical output.

Acceptance prints only after the backend status write succeeds **and** an authoritative
detail reload confirms ACCEPTED. Queue polling never prints. A lost acceptance/detail
response does not trigger printing. A print failure leaves acceptance intact.
The printer adapter serializes sends, times out after 15s and never retries delivery.
Epson failure/invalid response and transport errors tell the operator to inspect output
before a deliberate manual reprint. A manual reprint requires a fresh detail read and
confirmation. Both layouts use the Android ASCII printable-text convention; unsupported
glyphs become `?` and physical character output still requires review. The two layouts are kitchen-only (no contact/payment data) and detailed
(customer, phone, prices, total, payment method and server-verified state). Both include
snapshot options, notes and pickup time in Melbourne. Fulfillment comes directly from `StaffOrderResponse.fulfilment`; no new delivery
workflow is invented. Financial review/refund
flags override historical paidAt when describing payment certainty. Non-Latin glyph
support and long-line/paper wrapping must be tested on the printer before rollout.

## PWA caching and updates

The generated `/staff/sw.js` precaches only the staff bundle graph's hashed JS/CSS.
There is no runtime write cache and no API, customer, payment, media, navigation HTML
or offline order snapshot cache. The app requires online access; it does not offer
an offline business workflow or enqueue writes. The scope is `/staff/`; customer
website navigations are outside it. Staff HTML is network-fetched on every fresh open.

The worker checks for updates at startup, foreground return and every five minutes.
New workers wait, with an Update app now prompt. Updates are disabled while an order
dialog is open; staff can finish/discard drafts first. Activation/reload is explicit
and old staff caches are removed. A full close/reopen also permits worker activation.
The build displays the GitHub SHA prefix, or `local` for local builds. Do not configure
long-lived immutable caching for staff HTML or `sw.js` at the reverse proxy/CDN.
Existing static directory hosting serves `dist/staff/index.html` without backend routes.

## Verification and hardware rollout

Run from `frontend/`:

```sh
npm ci --include=optional
npm test
npm run build
# Existing repository browser infrastructure is Chromium CDP, not Playwright.
# Start Vite on 5174 and isolated headless Chrome on CDP 9223, then:
npm run test:staff-pwa-browser
npm run test:staff-layout-browser
```

Browser fixtures intercept every staff API before startup, use fictional JWT-shaped
credentials and block external printer traffic. Mocked XML tests validate protocol
formatting and result handling, not physical compatibility. No Playwright dependency
or configured suite existed in this checkout; the established CDP harness is reused.
Screenshots go to `/tmp/nakorn-staff-pwa` by default, never to the repository.
Existing identity/order API tests cover refresh, 401/403, CSRF and mutation ownership.
CI runs both browser suites before upload. It does not send printer jobs or restart
the backend on a frontend-only push. The existing Android APK workflow filters only `android-staff/**` and its own
workflow file, so this frontend change does not trigger an APK build.

Before using during service:

1. On the physical iPad, install from the deployed HTTPS origin and confirm standalone
   launch, refresh-cookie restoration, expiry/logout, role navigation, portrait/landscape,
   enlarged text, safe areas, keyboard scrolling and VoiceOver dialog focus.
2. Enable/test sound, then create a controlled incoming order through the established
   customer workflow. Verify one alert, acknowledgement, interruptions, network recovery,
   app switching and screen lock. Background alarms are unsupported.
3. Keep production browser printing disabled. Record the exact Epson variant, firmware,
   IP/DNS, current ePOS settings and trusted certificate chain without changing Wix.
4. Use a separately enabled test build on the same LAN only after approval of any
   necessary printer/certificate settings. Check endpoint, print one test docket, then
   inspect both layouts, quantities/options/notes, payment, pickup time, feed and partial cut.
5. Test offline/paper-out/open-cover, timeout after possible delivery, manual reprint,
   failed acceptance and concurrent order changes. Confirm no polling reprint and no
   automatic retry. Check the working Android/Wix integration remains operational.
6. Only after physical verification, make a separate reviewed release to enable
   production printing; leave automatic printing off until that workflow is accepted.

Automated success does not establish physical Safari audio or Epson compatibility.

### Local verification on 22 October 2026

- Locked dependency install succeeded with `npm ci --include=optional --offline`
  (the dependency cache was available; no dependency changes were required).
- `npm test`: **217 passed, zero failures/skips**, including 12 new PWA unit tests.
- Production build and `test:staff-pwa-build`: passed.
- `test:staff-pwa-browser`: **71 assertions passed**, including 810×1080 and
  1080×810 tablet views, other widths, persistent footer, collapsed queues, dialogs,
  login/logout/expiry, versioned actions, duplicate suppression, failures/recovery,
  sound activation and mocked XML handling.
- `test:staff-layout-browser`: **672 assertions passed**, covering existing staff,
  management and public layout comparisons.
- `test:staff-pwa-install-browser`: **14 assertions passed** against the production
  preview, including actual worker activation and cache/scope inspection.
- Existing ordersDrafts, payments, reservationVerification, reservationAvailability,
  featureAvailability, homepageMenu, menuNavigation, menuAdmin, adminSettings,
  australianPhone and orderEmailLinks browser scripts all passed.
- Workflow YAML parsed and `git diff --check` passed. Backend/Android builds were
  not rerun locally because this change modifies neither implementation.

These are Chromium/synthetic-server results. Physical iPad Safari, installed-mode
sound/interruption behavior, Epson HTTPS/CORS/network access, paper output and partial
cut remain unverified. Production printing remains explicitly disabled. Background
push, raw TCP and Bluetooth printing are unsupported in this PWA release.
