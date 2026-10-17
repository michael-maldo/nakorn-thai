# Nakorn Thai Staff — Android Java

Native Phase 1 tablet client. All new application files are contained here. The React site, backend, database migrations and production deployment remain unchanged. Phase A adds local ADMIN-only printer provisioning, diagnostics and test dockets; order printing remains excluded.

## Build and run

Pinned toolchain: AGP 8.11.1, Gradle 8.13 (standard wrapper with distribution SHA-256), compile/target SDK 35, Build Tools 35.0.0, Java source/target 17, minSdk 26. Use JDK 17 or 21 to run Gradle; do not use the host default JDK 25 with this wrapper. Core-library desugaring supports the Java collection APIs used on older devices. Android Studio with support for AGP 8.11 may import this directory as a standalone project; Android Studio is not required for CLI builds.

Install Android platform 35, build-tools 35.0.0 and platform-tools, set `ANDROID_HOME` or an untracked `local.properties` SDK path, then:

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./gradlew connectedDebugAndroidTest
```

The default **debug** origin is `http://10.0.2.2:8080/`, the emulator's route to the host development backend. It never defaults to production. Start the existing backend separately using its documented PostgreSQL/environment configuration. This app does not provision, seed, migrate or start the backend. Use an existing development staff account; no credentials are bundled.

For a USB-connected tablet with a host-local backend:

```sh
adb reverse tcp:8080 tcp:8080
./gradlew assembleDebug -PapiBaseUrl=http://127.0.0.1:8080/
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a remote development/staging environment:

```sh
./gradlew assembleDebug -PapiBaseUrl=https://your-staging-host.example/
```

Origin must end in `/`, with no credentials/path/query/fragment. Remote origins require HTTPS. Cleartext exceptions exist **only in debug** and only for emulator/loopback hostnames. Release has no cleartext exceptions, its build guard requires HTTPS, and runtime rejects HTTP even on localhost. Supply an HTTPS origin for a release build. The application ID is `au.com.nakornthai.staffapp`; debug app ID adds `.debug`. Release signing/distribution credentials are intentionally not supplied. Production tablets and Play distribution policies need separate validation; this is not a production-release declaration.

## Architecture

Java source under `app/src/main/java/au/com/nakornthai/staffapp/`:

- `app`: Application composition, one Activity, ViewModel factory.
- `identity/data`: existing cookie/token session protocol. `identity/ui`: login and authentication ViewModel.
- `ordering/data`: Retrofit-backed repository; `model`: server snapshots and independent status/payment drafts.
- `ordering/monitoring`: one application-scoped scheduler, queue state, new-order baseline/deduplication.
- `ordering/ui`: board, RecyclerView cards, details/actions and shared Orders ViewModel.
- `reservation/ui`, `analytics/ui`: native placeholders, with no API requests.
- `shared/network`: Retrofit API, response/error mapping, origin-bound cookie jar.
- `shared/security`: Android Keystore AES-GCM credential envelope.
- `shared/navigation`: Manage bottom sheet.

Fragments use View Binding, Activity-scoped ViewModels/LiveData and one Navigation Component controller. XML sizes with the available window; four columns require 800 dp of workspace, otherwise scrollable status tabs show one independently scrolling column. A permanent sidebar is used from 840 dp window width, an overlay drawer below that. Sidebar expansion is saved across recreation and launches. Footer stays outside the content region. System bars remain visible and root WindowInsets include system bars, cutouts and IME.

## Verified existing backend contracts

All paths below are existing endpoints. Sources inspected include `frontend/src/domains/ordering/pages/OrdersAdminPage.jsx`, `orderApi.js`, `orderModel.js`, `staffOrderDrafts.js`, identity context/API, StaffShell, and backend identity/order/payment controllers, SecurityConfig and ChangeOrderStatusHandler.

| Operation | Endpoint |
| --- | --- |
| CSRF/login/refresh/logout | GET `/api/identity/csrf`; POST `/api/identity/login`, `/refresh`, `/logout` |
| Current user | GET `/api/identity/me` (declared, not required by refresh-based restoration) |
| ADMIN/FOH active queue | GET `/api/staff/foh/orders?history=false` |
| ADMIN/BOH kitchen queue | GET `/api/staff/kitchen/orders` |
| Detail | GET `/api/staff/orders/{id}` (repository available; board details use immutable queue snapshots) |
| Staff write CSRF | GET `/api/staff/orders/csrf` |
| Status | PATCH `/api/staff/orders/{id}/status`, HTTP 204 |
| PayID | POST `/api/staff/payments/{id}/payid-confirm` |
| PayPal | POST `/api/staff/payments/{id}/check` |

ADMIN/FOH accept/cancel/handover; ADMIN/BOH prepare/ready. BOH never requests FOH queues, and UI additionally suppresses contact/payment presentation. Server RBAC and redaction remain authoritative. Pending=NEW, Accepted=ACCEPTED, Preparing=PREPARING, Ready=READY. Completion/cancellation remove an order from this active board. There is no invented delivery or refund flow. Phase 1 does not expose the React history selector, menu administration or other dashboard features outside the requested scope.

## Session behavior and security

Access tokens are memory-only. Only the refresh cookie is persisted in a private AES-GCM encrypted envelope; its key stays in Android Keystore. Cookie context is origin-bound; CSRF/session cookies remain in memory. Backups are disabled. Credential replacement uses a synchronous atomic SharedPreferences commit before session publication. Cookies are path/domain/expiry/secure matched. Redirects and automatic transport retries are disabled. Network/session operations serialize on the identity repository to coordinate refresh and prevent CSRF acquisition/write races. One 401 retry obtains fresh bearer and CSRF values. No HTTP-body/token logging is installed.

Login restoration calls refresh using the saved cookie; it does not persist access JWTs. Existing backend lifetime is a **fixed 12 hours**, with rotating refresh credentials and 15-minute access JWTs. There is no UI inactivity timeout. Pausing, locking and rotation do not log out. Expired/revoked sessions return to Login, clear protected UI/drafts and suspend monitoring. A refresh transport failure is handled conservatively as login-required because the server may already have rotated the credential; old refresh credentials are not replayed after uncertain rotation.

Explicit Sign Out clears local credentials even if server revocation cannot be confirmed. The login message reports that situation. The unreachable server session remains bounded by its unchanged expiry. Keystore invalidation requires login again. Device screen lock is not backend authentication.

For uninterrupted operation beyond 12 hours, future backend work must define a separately approved device-session model: individually registered/revocable terminals, constrained capabilities, rotating device credentials, audit/health, staff attribution and a bounded expiry/renewal policy. No permanent staff tokens or unlimited sessions are implemented.

## Mutation and draft safety

Each status/payment draft captures its original server version independently. Pickup range, cancellation reason, receipt acknowledgement and handover confirmation receive client validation; Spring remains authoritative. Payment prerequisites disable acceptance/handover. The monitor synchronously gates duplicate taps and does not move cards optimistically. Success, 409 and uncertain network outcomes all require authoritative reload before another write. A failed/uncertain draft is retained and invalidated; staff discard it explicitly before starting a fresh action. HTTP transport retries are disabled, and an uncertain acceptance is never automatically resubmitted.

Drafts for orders no longer in the queue remain available for explicit discard, with no submit action. Drafts remain in memory across Activity rotation; they are not stored on disk or in saved instance state. Process death deliberately discards drafts and reconciles server state. Dismissed payment/status forms retain independent drafts within the session. Session changes clear all drafts.

## Monitoring and alerts

One application-scoped ScheduledExecutorService performs queue reads and mutations. ProcessLifecycleOwner starts/stops foreground polling. Successful polling waits 15 seconds between requests; temporary failures back off to 30/60 seconds. There are no overlapping reads. Resume, network availability and manual refresh trigger reconciliation. Navigation, Manage and per-column UI do not create polling loops. The last good queue remains on network errors with a stale warning and timestamp; writes are disabled while unsynchronized.

The first successful snapshot of each process/session is a **silent baseline**. Later unseen NEW IDs produce a short native notification-stream tone and a persistent visible acknowledgement banner. IDs remain deduplicated across rotation/navigation for the current process. Acknowledgement only clears the alert indicator; it never accepts an order. Another terminal accepting the order removes its alert on reconciliation. After process restart the next initial snapshot is silent again. Pending column counts remain visible even for baseline orders. Sound depends on device notification volume/DND; no bypass is attempted.

Phase 1 provides **foreground-only** monitoring. Screen-off/background timing is not guaranteed. There is no foreground service, WorkManager alarm, push registration or notification permission request. Future background alerts require backend push, device compatibility/permission handling, reconciliation and an operational fallback. Keep the app visible on a powered terminal during service; do not regard a backgrounded client as an unattended alert receiver.

## Printer provisioning — Phase A

Open **Manage → Printers** as ADMIN. Other roles cannot open the destination through the Activity navigation method, and the Fragment and ViewModel independently guard access and writes. Existing authentication, server RBAC, order/payment actions and polling are unchanged. Printer profiles remain on this tablet across ordinary staff sign-out; a later ADMIN can administer them. FOH/BOH cannot configure or test printers.

Choose **Add Printer**, select a transport, discover a device or enter its endpoint, choose a driver, confirm the restaurant timezone, assignment and enabled state, then save. Edit, enable/disable and remove actions are available per profile. Kitchen/FOH assignments are descriptive independent destination memberships: multiple printers are allowed, with no primary printer, fan-out or automatic routing in Phase A. A saved profile does not imply an online printer.

### Bluetooth

Classic discovery includes paired and discoverable devices. Select a result and pair, if necessary, through the provided Android Bluetooth Settings button. Connections use secure RFCOMM Serial Port Profile (SPP); other Classic profiles are unsupported. Device discovery is not proof of printer or ESC/POS compatibility. Disabled/unavailable adapters and permission failures are reported. BLE advertisements can be discovered and saved, but **BLE connection diagnostics and printing are unsupported** because there is no generic GATT printing protocol. Manufacturer/model remain Unknown when unavailable; no model is invented.

On Android 12+, discovery contextually requests Nearby Devices scan/connect permissions; testing a saved Classic device can request CONNECT alone. On Android 8–11, discovery requires foreground fine-location permission and may require system location services enabled; the app does not collect location. Legacy Bluetooth permissions are limited to API 30. SCAN uses neverForLocation, which may filter some BLE advertisements. No background location, advertising, USB or background-service permissions are introduced. Discovery stops on navigation/background and after 15 seconds; use Stop discovery to cancel sooner.

### Network / LAN

NSD discovers `_pdl-datastream._tcp`, `_printer._tcp`, `_ipp._tcp` and `_ipps._tcp` advertisements, with names, resolved endpoints and advertised service/attribute keys. Manufacturer/model detection and protocol interrogation are not implemented. **IPP/IPPS/LPR discovery does not implement those protocols.** Profiles start Unsupported/Unverified. There is no subnet scanning or arbitrary discovery port probing.

Manual hostname/IP and port entry is a first-class option. Confirm the port from the device documentation: the initially suggested port is editable and not a detected capability. Wi-Fi/Ethernet describe the restaurant LAN path, not Wi-Fi Direct pairing. Classic uses the selected Bluetooth address instead. A connection test opens only the configured endpoint, sends no test bytes, and reports transport reachability separately from protocol/physical status. DNS and TCP connection deadlines are 4 seconds each; Classic connection and test-write deadlines are 8 seconds. Read timeout is 4 seconds; the initial driver performs no reads/status queries.

Raw printer traffic is unencrypted and unauthenticated. Use only an explicitly selected, trusted restaurant LAN endpoint or paired device. No backend tokens, cookies, customer/order data or credentials enter discovery or test payloads. Existing backend HTTPS/network-security configuration is unchanged; no general cleartext exception was added. IPPS/TLS, printer authentication and vendor-specific transports need dedicated future adapters; do not enter passwords into names or endpoints.

### Drivers and test dockets

Unsupported/Unverified profiles can be saved and connection-tested (except BLE); physical test printing is disabled. To use Generic ESC/POS, explicitly select it and attest compatibility using device documentation or a physical test. This is **operator-confirmed**, not verified by a protocol response. Connection success does not establish compatibility.

The initial ESC/POS adapter generates basic ASCII text only, with conservative 32-column/58 mm or 48-column/80 mm wrapping. Paper width is a layout setting, not device detection; verify actual font/printable width physically. No reset, cutter, cash-drawer, encoding-selection or status commands are sent. Encoding is fixed US-ASCII; non-ASCII/control characters are replaced with `?` (line feeds retained). Thai text, graphics, cutter control and paper/cover status queries are unsupported, so no operational controls for them are offered.

Choose **Print Test Docket**, confirm the endpoint and send one copy. The docket contains restaurant/test headings, profile name, assignment, connection, driver, restaurant-local timestamp, digits/letters and an end marker. Each profile has an explicit editable IANA restaurant timezone, initially Australia/Melbourne; confirm it during setup. Device timezone is not used. Success means **Sent to printer — physical output not confirmed**. Paper-out detection and physical completion cannot be claimed. No device capabilities are response-verified by this initial implementation.

A dedicated printer executor isolates all connection, persistence and print work from authentication/order monitoring. Tests are serialized, duplicate taps are gated, and Activity-scoped state prevents rotation from repeating a request. An uncertain marker is committed before transmission; a process interruption leaves that marker visible. No automatic retry occurs. Inspect the paper before explicitly requesting another test. This is a last-diagnostic record, not a production print queue or copy audit trail. Existing order transitions never depend on printer results.

### Storage, migration and extension

Profiles use a separate private `printer_profiles_v1` SharedPreferences JSON envelope with schemaVersion 1 and synchronous worker-thread commits. It stores only configured profiles, necessary selected Bluetooth addresses/endpoints, driver/settings, assignments and last diagnostic/timestamp. Discovery lists are temporary. No credentials or access tokens are stored. Backup and device transfer remain disabled by the existing app rules; reinstall/clear-data loses settings. There is no cloud synchronization/export. Ordinary app upgrades retain compatible settings.

Loaded profiles are validated. Invalid individual entries are skipped with a warning; saving the remaining valid profiles replaces that list. Malformed envelopes or unknown envelope schemas are retained and saving is blocked rather than silently overwritten. There is no speculative migration of unknown formats; a future schema change must add an explicit tested migration before deployment. Keep physical printer configuration records outside the app for recovery. Unsaved editor state survives Activity recreation but is not promised across process death; saved profiles survive restart.

`printing/driver/PrinterDriver` separates driver capabilities/command generation from `PrinterConnection`; registry entries supply UI driver names. Add a driver in `PrinterDriverRegistry`, implement validation/capabilities and test generation, and add a transport adapter only where necessary. Extend capability-aware settings/schema with migration tests when introducing new options. Connections must remain bounded and closeable. UI, discovery, persistence and diagnostics stay in `printing/`; no order domain dependency is needed.

### Physical hardware acceptance

1. Record tablet/API version, printer model/firmware/interface and documented protocol; use a development tablet and no customer data.
2. Verify ADMIN access and FOH/BOH denial, denied/revoked permissions, disabled Bluetooth, pairing, discovery cancellation/timeouts and navigation/rotation.
3. Verify manual LAN configuration and advertisements, hostname/IP handling, unreachable/refused endpoints and Bluetooth SPP prerequisites. Never infer protocol from an open port.
4. Save multiple profiles and assignments, edit/disable/remove, restart the app, sign out/in, and confirm settings persist with no automatic output.
5. Select a documented compatible driver, confirm timezone/width, print exactly one test and inspect line wrapping, non-ASCII replacement and end marker physically.
6. Disconnect before/during transmission; test paper-out/cover-open and process interruption. Confirm unknown/uncertain status is honest, no automatic second copy occurs, and Orders/polling/session behavior remains intact.
7. Run instrumented tests on the tablet and test supported API levels before restaurant use. No physical printer compatibility has yet been verified.

Automatic order printing, Print Order/Reprint actions, receipts, persistent services, backend jobs and fleet synchronization remain excluded. Reliable future automatic printing requires a separate durable-job and audited-reprint design; acceptance must remain independent from physical print success.

## Tests

`src/test`: JUnit, Mockito and MockWebServer tests for role transitions, snapshots, draft versions/validation, grouping, payment prerequisites, CSRF/cookies/204, refresh coordination/restoration/expiry/logout, alert baseline/deduplication, polling, network retention and uncertain mutation reconciliation.

`src/androidTest`: Espresso tests with an instrumentation-only local HTTP fixture and custom runner. The fixture never contacts the configured application backend, and fake credentials/data are only in the test APK. Covers footer/Manage navigation, placeholders, rotation, acceptance validation, sign-out and expiry. Add physical tablet acceptance, accessibility/audio checks and real development-backend contract tests before pilot.

Build/test execution evidence and remaining verification are recorded in [VERIFICATION.md](VERIFICATION.md). All created files are listed in [FILES.md](FILES.md).

## Orders visual refinement

The supplied `../android-staff-dashboard-ordering.jpg` was inspected as the empty-board reference. It shows pale equal columns, compact headings/counts, generous empty-state space and a quiet centered footer. It does not show populated cards or an expanded sidebar; the native card/sidebar refinements follow the agreed Nakorn Thai requirements rather than inferred Wix behavior.

The refined board uses 12 dp tablet gutters (8 dp in compact resources), separate green count badges, consistent light column surfaces, centered empty states and flatter white cards with a reference/time/items/payment hierarchy. Narrow-screen tabs retain a full-width column without leftover landscape gutters. Sidebar and overlay navigation share soft selected-state colors and 56 dp button targets. Home/Analytics/Manage form a centered footer capped at 480 dp, with equal item sizing and a top divider. Connection/last-sync status stays in the persistent shell; an empty message row and duplicate healthy-status row no longer consume board height. Error/unsaved-draft notices and the 200-order cap remain visible when relevant.

States, API contracts, role guards, action dialogs, polling and alerts are unchanged. The new instrumented geometry case checks equal columns, alignment, footer centering and the Refresh touch target. Physical tablet verification is still required for actual rendering, font scaling, color/contrast, collapsed-rail alignment and system-bar/keyboard behavior; XML/build checks are not screenshot parity evidence.
