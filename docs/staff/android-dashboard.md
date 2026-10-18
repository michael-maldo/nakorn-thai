# Android staff dashboard redesign

## Scope and screen status

Implemented directly in the existing `main` working tree. The prior Timekeeping
redesign and its uncommitted files are preserved. No branch, commit, push, release,
installation, publication or deployment was performed.

| Screen / area | Implementation status |
| --- | --- |
| Unified shell | Implemented: deep-green top bar, existing branding, account identity, order-alert indicator, responsive scrollable sidebar/drawer, selected navigation, shared workspace surfaces and existing bottom navigation |
| Orders | Implemented: four live summary tiles, Pending/Accepted/Preparing/Ready queues, status-coloured columns, refreshed cards, compact refresh and retained existing detail/action/confirmation flows |
| Reservations | UI redesign implemented: scrollable heading, capability/status card and service guidance. Native reservation retrieval/management was an existing unimplemented placeholder and remains unconnected; no fake bookings, counts or API integration were added |
| Analytics | Implemented as a read-only live queue overview using the existing role-filtered order monitor; explicitly not historical revenue, sales or reservation reporting |
| Manage | Implemented: grouped Workspace / Staff tools / Session actions, consistent text/icon styling, retained role-based printer visibility and independent-window inset/scroll behavior |
| Printer settings | Implemented: shared page typography, guidance, rounded profile/setup cards and compact outlined secondary actions; existing discovery, compatibility, LAN/Bluetooth setup, persistence and diagnostic/test-print behavior preserved |
| Timekeeping / My Timesheet | Existing completed implementation preserved. Both now have direct shell navigation with the correct role-specific label, while Manage still reaches the same destination |

The visual direction follows the supplied dashboard reference: green branding,
white/light-grey workspace surfaces, mint selection accents, rounded cards,
readable typography, subtle borders and practical minimum 48dp controls. There is
one application navigation system and no Kotlin, Compose or new dependency.

## Architecture and preserved behavior

`MainActivity` owns the shell and keeps the original activity-scoped authentication
and Orders ViewModels. The new alert dialog observes that same Orders ViewModel;
acknowledgement invokes the existing monitor acknowledgement, which does not accept
or mutate an order. The existing audible-alert sequence and ToneGenerator path remain
in place. Protected dialogs are still removed on session expiry.

`DashboardPresentation` contains pure safe-viewport/font-scale decisions.
`QueueSummaries` renders the same four status counts in Orders and Analytics.
Analytics does not create another poller, repository or listener: it observes the
existing activity-scoped Orders ViewModel. Its Refresh requests the existing monitor
refresh. Its metrics are read-only. The backend continues to determine which orders
are visible to ADMIN, FOH or BOH.

Order polling, scheduler ownership, JWT/CSRF handling, repository calls, immutable
server order snapshots, status permissions, payment checks, draft validation,
uncertain-response handling and transitions are unchanged. Order cards continue
opening the existing detail dialog; existing order action dialogs retain their
inputs, checks, duplicate guards and IDs. Printer ViewModel/repository/discovery/
driver/connection logic and role guards are unchanged; its fragment only changes
presentation and the layouts it inflates.

Timekeeping source and tests were not changed during this dashboard pass. Its
restaurant-zone timestamps, server-sourced live state, mandatory correction reasons,
version checks, date filters, API contracts and read-only staff experience remain.

No backend, Flyway, PostgreSQL, React, API definition, deployment workflow, manifest,
build dependency or release configuration was changed.

## Responsive layout

- The existing `WindowInsetsSupport` remains the single activity-window inset owner.
  The existing Manage dialog keeps its own independent window handling. No additional
  competing inset owner is introduced.
- Sidebar versus drawer is selected from safe available width, divided by font
  scaling (840dp effective threshold). Both navigation lists scroll, including in
  reduced-height windows. A collapsed sidebar retains icons, tooltips and accessible
  destination labels. Drawer width is capped to the available safe viewport.
- The top bar shares the brand, notification access and account identity. Account
  text moves out of the top bar on narrow/short windows; it remains in navigation.
- Short or enlarged-font windows compact the top bar and bottom navigation and hide
  the duplicate persistent connection/alert rows to preserve workspace height.
  Bottom-navigation actions remain available as icons; the top-bar alert dialog keeps
  acknowledgement reachable from every screen without accepting the order.
- Orders uses a CoordinatorLayout/AppBarLayout for a naturally collapsible summary
  header and the existing queue RecyclerViews for independently scrollable queues.
  The summary header starts collapsed in short windows. Refresh stays outside that
  header, next to the tab strip, so it remains reachable. This avoids placing the
  queue RecyclerViews inside a second vertical ScrollView.
- Four queues are shown together at 960dp effective content width. Narrower or
  enlarged-font workspaces show the existing scrollable status tabs and one full-width
  queue. Summary tiles wrap from four to two columns at 600dp effective width.
- Reservations, Analytics, Manage, printer settings and existing Timekeeping use their
  scrollable page/form layouts. Typography wraps rather than relying on fixed page
  heights. Printer inputs, discovery and save/cancel controls stay in the scroll owner.

Thresholds use measured content/safe viewport dimensions, not a Samsung model,
orientation lock or hardcoded physical screen size. Real device layout behavior still
requires the verification below.

## Files changed in this dashboard pass

Paths below are relative to `android-staff/app/src/`, with `...` standing for
`au/com/nakornthai/staffapp`. The separate existing Timekeeping changes remain in
the working tree and are documented in [android-timekeeping.md](android-timekeeping.md).

| Files | Purpose |
| --- | --- |
| `main/java/.../app/MainActivity.java` | Shared shell navigation, role labels, compact viewport presentation and alert access; existing sound path retained |
| `main/res/layout/activity_main.xml` | Green top bar, account identity, scrollable sidebar/drawer, content host and existing footer |
| `main/java/.../shared/dashboard/DashboardPresentation.java` | Pure width/font/height presentation decisions |
| `main/java/.../shared/dashboard/QueueSummaries.java` | XML-backed four-status count component for Orders and Analytics |
| `main/java/.../ordering/ui/OrdersFragment.java` | Summary tiles, responsive queue presentation and collapsible header |
| `main/java/.../ordering/ui/OrderAdapter.java` | Status badges and shared card text/resources without modifying server snapshots |
| `main/java/.../ordering/ui/OrderAlertsDialog.java` | Persistent-shell acknowledgement/view-orders dialog using the same Orders ViewModel |
| `main/java/.../ordering/ui/OrderDetailDialog.java` | Inflate shared secondary action styling; permissions and handlers retained |
| `main/res/layout/fragment_orders.xml`, `item_order.xml`, `dialog_order_action.xml` | Board workspace/header, refreshed order cards and primary/secondary confirmation styling |
| `main/java/.../reservation/ui/ReservationsFragment.java`, `main/res/layout/fragment_reservations.xml` | Styled, honest reservation capability page and existing Orders navigation |
| `main/java/.../analytics/ui/AnalyticsFragment.java`, `main/res/layout/fragment_analytics.xml` | Current queue counts and synchronization state from the existing monitor |
| `main/res/layout/sheet_manage.xml` | Grouped, icon-based staff tools; existing IDs and actions retained |
| `main/java/.../printing/ui/PrinterManagementFragment.java`, `main/res/layout/fragment_printers.xml` | Shared printer profile/setup presentation; behavior unchanged |
| `main/res/layout/item_dashboard_action.xml`, `item_dashboard_metric.xml`, `item_dashboard_printer.xml` | Reusable compact actions, summary tiles and profile cards |
| `main/res/values/dashboard_{strings,styles,colors}.xml` | Shared text, typography, rounded cards, action styles and semantic colours |
| `main/res/drawable/dashboard_{pending,accepted,preparing,ready}.xml`, `ic_dashboard_{bell,printer,manage,logout}.xml` | Queue surfaces and native vector navigation icons |
| `test/java/.../DashboardPresentationTest.java` | Safe-width, narrow/large-font queues, compact chrome and summary wrapping |
| `androidTest/java/.../StaffUiTest.java` | Update destination assertions and queue breakpoint expectations for the new pages/layout policy |
| `androidTest/java/.../DashboardUiTest.java` | Shell role navigation, live Analytics, honest Reservations, alert acknowledgement and monitor continuity |
| `androidTest/java/.../DashboardLayoutTest.java` | Inflated summary grids and short/large-font page scrolling |
| `docs/staff/android-dashboard.md` (repository root) | Implementation status, architecture, files, checks and limits |

## Executed verification

From `android-staff/`, using Java 21 and the configured SDK 35:

```text
./gradlew --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL
JVM: 78 tests, 0 failures, 0 errors, 0 skipped
Lint: 0 errors, 81 warnings across the application
```

The full JVM suite includes the existing order workflow, order-monitor/alert,
identity/API contract, printer and Timekeeping tests, plus four dashboard presentation
tests. Timekeeping's previously passing tests still pass. Existing instrumentation
navigation tests were updated for the new pages/breakpoints. Four dashboard workflow
tests and two layout tests compiled successfully, as did the existing Timekeeping,
printer, secure-storage and inset suites. Compilation is not execution.

`adb devices -l` returned an empty device list. **Instrumentation was not executed**,
and no screen rendering, audio output or printer hardware behavior was physically
verified. Lint warnings include existing and new translation/style/API-deprecation
warnings; no lint errors were suppressed by a baseline. `git diff --check` passed.

Review artifacts:

- `android-staff/app/build/outputs/apk/debug/app-debug.apk`
- `android-staff/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `android-staff/app/build/reports/tests/testDebugUnitTest/index.html`
- `android-staff/app/build/reports/lint-results-debug.html`

The debug artifact retains the existing default emulator origin
`http://10.0.2.2:8080/`. Use the existing approved `apiBaseUrl` build property for a
physical-device/VPS integration build. No deployed application or API origin was
changed here.

## Manual verification before rollout

Run `connectedDebugAndroidTest` with a device/emulator. On the target Samsung tablet,
verify portrait/landscape, split-screen/freeform/reduced-height windows, 200% font and
display scaling, taskbar/gesture/three-button navigation, cutouts and keyboard-visible
forms. Specifically inspect:

- Top bar, expanded/collapsed navigation, drawer scrolling and compact footer labels.
- Summary wrapping, all four queues/tabs, independent scrolling, collapsed header and
  always-accessible Refresh, retained drafts and order details/actions.
- New-order audible output, visible bell count, acknowledgement without acceptance,
  polling while navigating between screens and reconnect/expired-session recovery.
- Live Analytics synchronization/stale states and accurate role-filtered counts;
  Reservations must continue showing its explicitly unconnected Android capability.
- Timekeeping role labels and unchanged live/manual/correction/audit flows, including
  stale/concurrent updates and keyboard-open forms.
- Printer discovery, permission prompts, endpoint editing, unknown-driver restrictions,
  save/restore, connection diagnostics and deliberate physical test printing. There
  is still no automatic order-to-printer routing introduced by this redesign.

Native reservation management and historical business analytics remain separate
functional integrations, not completed features inferred from these redesigned pages.
