# Android staff timekeeping — Phase B

## Architecture

The native Java/XML feature lives under
`android-staff/app/src/main/java/au/com/nakornthai/staffapp/timekeeping/`.
It uses the existing app graph, Retrofit client, session cookie jar, JWT identity
repository, lifecycle ViewModels, Navigation component and Material widgets.
No dependency, backend API, migration, React page or deployment workflow was changed.

`TimekeepingFragment` is one shared destination. The existing scrollable Manage
sheet adds **Timekeeping** for ADMIN and **My Timesheet** for FOH/BOH. Orders,
reservations, analytics, printers (ADMIN only) and sign-out remain in Manage.
Backend authorization remains authoritative. Non-admin layouts hide all mutation,
staff-selection and audit controls; the repository also rejects administrative
operations from non-admin sessions and routes staff reads exclusively to `/me`.

The fragment uses a retained `TimekeepingViewModel` and a feature repository.
Attendance models match the actual backend DTO names. Retrofit interfaces extend
`StaffApi`, so timekeeping shares the configured origin, JWT/session rotation,
CSRF cookies, no redirects and disabled connection retries. Timekeeping uses its
own serial executor; individual network/session operations synchronize with the
existing identity monitor. Open-shift pagination releases that monitor between
pages so it does not occupy the order-monitor executor. Account generation checks
fence queued operations and results after logout, account or role changes.

The activity's existing `WindowInsetsSupport` remains the single inset owner for
its window. This feature does not add a second inset listener. Its page is one
NestedScrollView with full-width controls and minimum 48dp action/input targets.
Forms, validation messages, confirmations and paging live in the scrollable page;
there is no fixed-height custom form dialog. Platform date/time pickers use their
normal system window fitting and are dismissed when the fragment view is destroyed.
The existing Manage sheet's independent-window insets and scrolling are preserved.

## API integration

The contract is documented in [backend timekeeping](timekeeping.md).

| Endpoint | Android use |
| --- | --- |
| GET `/api/restaurant/availability` | Configured IANA timezone and server evaluation timestamp; no hardcoded restaurant timezone |
| GET `/api/identity/users` | ADMIN account names and active-account selector; disabled accounts remain nameable in historical records |
| GET `/api/staff/timekeeping/csrf` | CSRF header/token before each mutation, using shared cookies |
| POST `/api/staff/timekeeping/clock-in` | ADMIN live Time In; body contains only `staffUserId` |
| POST `/api/staff/timekeeping/clock-out` | ADMIN live Time Out; body contains only `staffUserId` |
| POST `/api/staff/timekeeping/entries` | ADMIN manual historical start, end, staff ID and reason |
| PATCH `/api/staff/timekeeping/entries/{id}` | ADMIN correction with original version, replacement times and reason; explicit JSON null supports reopening |
| GET `/api/staff/timekeeping/entries` | ADMIN paginated date/staff listing and unfiltered open-shift scan |
| GET `/api/staff/timekeeping/me` | Authenticated staff's own paginated date listing; never includes an employee identifier |
| GET `/api/staff/timekeeping/entries/{id}/audit` | ADMIN paginated previous/new values, administrator, timestamp, action and reason |

The existing identity CSRF/login/refresh/logout endpoints are unchanged. Safe reads
can refresh credentials once after 401. Writes proactively obtain authorization,
but never replay a submitted attendance mutation after a lost response or a 401.
A 401 from CSRF or the submitted write clears the session and returns to sign-in.

## Administrator workflows

1. Open Manage → Timekeeping. The initial date range is today in the configured
   restaurant timezone, derived from server evaluation time. The separate open-shift
   overview covers all dates and all staff, independent of the date/staff filter.
2. Select an active staff account. Choose **Time In** or **Time Out**, review the
   target account and operation, then choose **Confirm attendance change**. Only
   the server response establishes success and attendance timestamps/status.
3. Choose **Manual historical entry**, enter or pick both timestamps, and enter a
   reason. Include the following calendar date for an overnight shift. Review the
   normalized times in restaurant time, then confirm. Manual entries require an end.
4. Choose **Correct attendance** on a listed row. Existing times and version are
   captured; the reason starts blank. The confirmation shows previous and proposed
   values. A correction may explicitly leave/reopen a shift, subject to backend
   overlap constraints. Employee identity and creation metadata are not editable.
5. Choose **View audit history** to inspect the acting administrator and before/after
   times and versions. Audit history and attendance lists have independent paging.
6. Use native From/Through date pickers, Today, Apply or Refresh. Dates are inclusive
   in the UI and converted to `[start midnight, midnight after Through)` instants.
   All matching completed shifts contribute their full durations, even when they
   cross the range boundary. Page size is 25, using backend deterministic sorting.

A reviewed command is frozen. Duplicate confirmation taps are rejected synchronously
before work is queued, and mutation controls are disabled while busy. A version,
overlap, duplicate-in or no-open-shift error is shown using the backend message.
Conflicts disable writes until an explicit read refresh. Refresh discards old drafts
and confirmations so a correction must start from a newly loaded version.

Lost/invalid mutation responses and server failures do not report success and do not
trigger an automatic write retry. The UI tells the administrator to refresh and
inspect attendance/audit before beginning a new action. A confirmed mutation followed
by a failed list refresh is explicitly reported as server-confirmed, with a separate
request to refresh the list; it is not submitted again.

## Staff workflow

FOH/BOH open Manage → My Timesheet. They select an inclusive date range, apply or
refresh, and page through their own attendance. Rows show clock-in/out in restaurant
time, worked hours or an explicit OPEN/incomplete indicator. The summary uses the
backend's completed-seconds total across all matching pages; open shifts are excluded.
There are no live, manual, correction, delete, staff-selection or audit actions.
Empty ranges show an explicit empty-timesheet message. Network/authorization errors
provide read retry or sign-in behavior rather than optimistic attendance changes.

## Time handling

- The restaurant timezone must load successfully before time-dependent actions are
  enabled. A timezone failure is retryable; there is no device-timezone fallback.
- Every displayed attendance/audit timestamp includes its numeric UTC offset and
  IANA zone, distinguishing repeated daylight-saving times.
- Text entry uses explicit-offset ISO timestamps representing restaurant local time;
  the offset must be valid for that local date/time in the configured zone. UTC input
  is valid when it matches that zone's offset. Date/time pickers reject a spring-forward
  gap and require an explicit choice between offsets during a repeated fall-back hour.
- Requests normalize to UTC instants with at most microsecond precision. Correction
  fields preserve the original timestamp precision, including when left unchanged.
- Worked seconds and completed totals come from the backend. Hours use four decimal
  places for presentation only; no payroll rounding, breaks, rates or overtime are
  calculated, and open duration is never represented as completed zero hours.
- In-memory form drafts survive activity recreation/rotation. They are discarded on
  a principal change, explicit refresh, navigation away or process death. Pending
  operations are never persisted or automatically replayed after process death.

## Changed files

Paths below are relative to `android-staff/app/src/` unless otherwise specified.

| Files | Purpose |
| --- | --- |
| `main/java/.../timekeeping/model/Attendance.java` | Backend DTOs for entries, totals, audit and restaurant metadata |
| `main/java/.../timekeeping/model/AttendanceTime.java` | Restaurant-zone display, explicit-offset validation and range boundaries |
| `main/java/.../timekeeping/model/AttendanceCommand.java` | Validated, frozen live/manual/correction commands |
| `main/java/.../timekeeping/data/TimekeepingApi.java` | Retrofit endpoint contract |
| `main/java/.../timekeeping/data/TimekeepingRepository.java` | Authorized reads/writes, CSRF, generation checks and uncertain-outcome handling |
| `main/java/.../timekeeping/ui/TimekeepingViewModel.java` | Lifecycle state, duplicate guard, drafts, confirmations, filtering and paging |
| `main/java/.../timekeeping/ui/TimekeepingFragment.java` | Role-aware native screen, date/time pickers and scrollable forms |
| `main/java/.../app/AppGraph.java`, `StaffViewModelFactory.java` | Feature wiring into existing graph/factory |
| `main/java/.../shared/network/StaffApi.java` | Inherit the owning feature's API declarations on the shared client |
| `main/java/.../shared/navigation/ManageSheet.java` | Role-appropriate Manage label and shared destination |
| `main/res/layout/fragment_timekeeping.xml`, `main/res/values/timekeeping_strings.xml` | Accessible scrollable layout and form labels |
| `main/res/layout/sheet_manage.xml`, `main/res/navigation/staff_navigation.xml` | Preserve existing actions while adding the destination |
| `test/java/.../TimekeepingRulesTest.java` | Role labels, overnight input, reasons, ordering, DST and formatting |
| `test/java/.../TimekeepingApiTest.java` | Real HTTP contracts, self-service isolation, CSRF/refresh, errors and no replay |
| `test/java/.../TimekeepingViewModelTest.java` | Duplicate confirmations, read-only state, conflicts, drafts and server dates |
| `androidTest/java/.../FixtureApplication.java` | Isolated timekeeping HTTP fixtures without changing order fixtures |
| `androidTest/java/.../TimekeepingUiTest.java` | Navigation, live/manual/correction/audit forms, staff restrictions, failures and recreation/keyboard flows |
| `androidTest/java/.../WindowInsetsTest.java` | New layout in the 320×180dp/200%-font scroll check, plus synthetic keyboard/safe-viewport reachability |
| `docs/staff/android-timekeeping.md` (repository root) | Architecture, workflows, endpoints, checks and device review requirements |

Here `...` is `au/com/nakornthai/staffapp`. No backend, React, build dependency or
deployment workflow file was changed.

## Verification and remaining device work

Run from `android-staff/` with the existing SDK configuration and Java 21:

```bash
./gradlew --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

JVM tests cover API 400/401/403/409 behavior, duplicate/no-open/overlap/conflict
messages, a disconnected mutation response with exactly one request, explicit-null
corrections, reasons, overnight/DST timestamps, server date defaults, staff self-view
without an ID parameter, frozen versions and duplicate submission blocking.
Instrumentation covers actual screen navigation and forms, plus reduced-height,
enlarged-text, keyboard and inset geometry. Compilation is distinct from execution.

ADB reported no attached device/emulator. Instrumentation execution and physical
Samsung behavior remain unverified. Before rollout, run `connectedDebugAndroidTest`
and verify on a Samsung tablet:

- Fullscreen and landscape; split-screen and reduced-height/freeform windows.
- Gesture navigation, three-button navigation, taskbar/caption bars and cutouts.
- IME open on start/end/reason fields, scrolling to Review/Confirm/Cancel, font scale
  200%, TalkBack labels and touch targets.
- Rotation with an unfinished draft or in-flight mutation, logout/session expiry,
  concurrent corrections from a second administrator and a response lost after commit.
- Existing ordering, reservations, analytics, Manage actions and printer workflows.

Other limits: the current backend has no open-only filter, so the admin overview
scans unfiltered pages until it finds all reported open shifts. Large histories may
require multiple reads; pages are separate server snapshots, so concurrent changes
can temporarily affect the overview until refreshed. No automatic polling or offline
attendance mutation queue was added. The UI is English, matching existing screens;
lint may report translation/style warnings. No deployment, commit or push is included.

## Executed results

Final validation completed successfully on Java 21 using the existing Android SDK
(compile/target SDK 35):

```text
./gradlew --offline testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
BUILD SUCCESSFUL
JVM: 66 tests, 0 failures, 0 errors, 0 skipped
```

The total includes 24 new timekeeping JVM tests (11 HTTP-contract, 7 ViewModel,
6 rule/time-format tests), alongside the existing order, monitor and printer tests.
Lint completed with zero errors and 73 warnings across the application, including
existing and new English-text/translation/style warnings. No timekeeping missing-label
accessibility warning remains. The seven new timekeeping UI tests, new keyboard
geometry test and expanded existing reduced-height test compiled successfully;
none were executed because `adb devices -l` listed no attached device/emulator.
`git diff --check` passed.

Built review artifacts (relative to repository root):

- `android-staff/app/build/outputs/apk/debug/app-debug.apk`
- `android-staff/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `android-staff/app/build/reports/tests/testDebugUnitTest/index.html`
- `android-staff/app/build/reports/lint-results-debug.html`

The debug APK retains the existing default emulator development origin
`http://10.0.2.2:8080/`. For physical-device integration, configure the approved API
origin using the existing `apiBaseUrl` build property or follow the project's localhost
ADB-reverse workflow. No application was installed or deployed by this implementation.
