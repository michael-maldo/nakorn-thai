# Android verification — 17 October 2026

Latest printer provisioning results are in **Printer provisioning Phase A** below. Earlier sections retain Phase 1 history.

**Status: implementation built; runtime/device acceptance remains incomplete.** No running Spring backend or production data was used for testing. The Android UI has not yet been exercised successfully on a device/emulator in this environment.

## Executed results

| Check | Observed result |
| --- | --- |
| Standard `./gradlew` bootstrap | PASS; downloaded Gradle 8.13 and verified the pinned checksum |
| `testDebugUnitTest` | PASS: **25 tests, 0 failures, 0 errors, 0 skipped** |
| `assembleDebug` | PASS; debug APK generated |
| `assembleDebugAndroidTest` | PASS; instrumentation APK generated, including seven Espresso cases and one Keystore case |
| `lintDebug` | PASS: **0 errors, 33 warnings** |
| `connectedDebugAndroidTest` | BLOCKED/FAILED: `DeviceException: No connected devices!`; instrumented cases did not execute |
| Existing application preservation | PASS: `git diff --exit-code -- frontend backend .github infrastructure docker-compose.yml` |

Final unit/lint invocation:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk ./gradlew \
  -g /tmp/nakorn-gradle-home testDebugUnitTest lintDebug --no-daemon
```

The full wrapper build also ran `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` successfully. Temporary SDK: `/tmp/nakorn-sdk`; ignored local SDK configuration points there. Configure your own permanent SDK when importing the project or after temporary storage is removed.

Observed build JDK: Temurin 21.0.12. Application source and target compatibility: Java 17. Host default JDK 25 was not used for Gradle. Android Studio was not discovered in the inspected locations; CLI tools were downloaded into temporary storage. No physical Android device was attached and `/dev/kvm` was absent.

Wrapper distribution SHA-256, matched against Gradle's published checksum:

```text
20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78
```

## Artifacts and reports

- Application: `app/build/outputs/apk/debug/app-debug.apk`
- Test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Lint report: `app/build/reports/lint-results-debug.html`

Artifacts/reports are generated and ignored by Git. This is a development-signed APK, not a production-signed release. No production distribution or deployment occurred.

Lint warnings comprise English-only dynamic UI strings, a count-pluralization notice, weighted layout/overdraw notices, launcher-icon resource placement and a newer-Gradle notice. They are not suppressed or represented as a clean warning-free result. Dependencies remain pinned to the built combination.

## JVM coverage

- `ApiContractTest`: 11 MockWebServer cases for login/CSRF, HTTP 204 writes, concurrent refresh coordination and rotation, persisted session restoration, expiry, logout, PayID/409 payload, origin/path cookie isolation, uncertain transport without retry, BOH endpoint/payment permissions, queued-principal cancellation, repeated 401 invalidation and memory-only CSRF session cookies.
- `OrderWorkflowTest`: 7 cases for role actions, original-version independent drafts, pickup/cancel/handover/receipt validation, payment prerequisites, grouping/counts/empty columns, snapshot JSON mapping, alert baseline/deduplication and detail privacy. Some cases exercise several related assertions.
- `OrderMonitorTest`: 6 cases for non-overlap/resume, uncertain mutation and duplicate prevention, stale snapshot retention/reconnection, payment gates, bounded backoff/background cancellation and session-clear race handling.
- `OrdersViewModelTest`: 1 case for draft identity/version retention across refreshes, independently retained payment/status forms, missing-order drafts, explicit discard and clearing on session change.

These tests use test fixtures/mocks. They do not prove live-backend interoperability, Android rendering, Keystore behavior or audible sound on the restaurant tablet.

## Instrumentation blocker

Android Emulator 37.2.12 with API 35 x86_64 was provisioned temporarily. Both unaccelerated boot attempts crashed with native segmentation faults (exit 139), before installing/launching this application:

1. 1280×800, 160 dpi, two virtual CPUs, 2048 MB RAM, SwiftShader.
2. Conservative retry: one virtual CPU, 1536 MB RAM, SwiftShader indirect and Vulkan disabled.

The second attempt reached Android system-service startup before crashing. No emulator remained available for the connected-test task. This is host/emulator execution evidence, not an application crash report. Eight compiled instrumented tests remain **unexecuted**:

- Footer/Manage/destination coordination.
- Navigation retention through orientation and Activity recreation.
- Pickup validation and HTTP 204 acceptance.
- Sign Out/footer hiding.
- Session expiry/login-required state.
- Sidebar/overlay behavior, restoration and footer/content separation.
- Board column equality/alignment, responsive visibility, footer centering and Refresh touch size.
- Actual Android Keystore envelope restore/rotation/clearing and absence of plaintext preferences.

Run `./gradlew connectedDebugAndroidTest` with a working emulator or attached development tablet before declaring runtime acceptance.

## Remaining acceptance work

1. Run all instrumented tests on an accelerated API 35 emulator and the intended physical tablet.
2. Verify supported Android/API levels, large text, TalkBack, keyboard/IME, portrait/landscape, narrow-window behavior, sidebar and footer geometry.
3. Run against a dedicated development/staging backend with ADMIN, FOH and BOH accounts; verify real cookie/CSRF rotation, the fixed 12-hour expiry/revocation and server redaction.
4. Exercise real acceptance, preparation, ready, handover, cancellation, PayID receipt confirmation and PayPal checks without production/customer data. Include two-terminal conflicts and response loss after committed writes.
5. Test native sound/acknowledgement, volume/DND, rotation deduplication, another terminal accepting an order and the silent initial/restart baseline. BOH cannot detect NEW orders because its existing API intentionally excludes them.
6. Test lock/background/resume, airplane mode, Wi-Fi changes and process death. Monitoring is explicitly foreground-only; no reliable screen-off alert claim is made.
7. Validate secure storage on the actual tablet. Memory-only drafts are discarded after process death; do not infer disk persistence from rotation behavior.
8. Approve signing, update/distribution and a restaurant pilot/rollback process before operational deployment.

No backend changes are required by the implemented Phase 1 scope. Uninterrupted operation beyond the existing 12-hour session would require a separately approved backend device-session strategy. Physical printing remains excluded until hardware identification and a separate reliability design.

## Screenshot-based visual refinement

The supplied Wix image was inspected directly. It contains an empty four-column board and centered footer, but no populated order cards or expanded sidebar. Accordingly, the app follows its spacing/surface hierarchy while preserving Nakorn Thai branding and the required native navigation.

Changed `activity_main.xml`, `fragment_orders.xml`, `view_order_column.xml`, `item_order.xml`, theme/string resources and added palette, dimensions, selectors and column/badge shapes. Small changes in `MainActivity` and `OrdersFragment` bind separate counts, apply dp-based gutters, center the footer and maintain selected navigation styles. No business/repository/session architecture changed.

Final wrapper run: `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` — **BUILD SUCCESSFUL**. The 25 JVM tests passed again; both APKs were regenerated. The instrumentation APK now includes the additional board/footer geometry test. `adb devices` was checked again and listed no devices, so none of the eight instrumented cases was executed during this visual pass. The earlier native emulator crashes remain the documented host limitation; rendered Android screenshots are unavailable.

On a physical tablet verify: four equal usable columns at the actual display density; populated cards with long names/options/notes; 200% font scaling; column scroll independence; narrow-window tabs and full-width selected column; rail icon centering/selection in both sidebar modes; centered footer selection and touch targets; and system-bar, cutout, keyboard and dialog separation. Color/contrast and text wrapping are source-reviewed, not device-verified.

## Printer provisioning Phase A — 17 October 2026 (latest)

**Source implemented and build verified; Android runtime and physical printer acceptance remain unverified.** This section supersedes the earlier Phase 1 printing exclusion for provisioning/test dockets only. Automatic/order printing remains excluded.

Final invocation from `android-staff/`:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk \
GRADLE_USER_HOME=/tmp/nakorn-gradle-home \
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug \
  -PapiBaseUrl=https://nakorn-thai.tech-labs.dev/ --offline --no-daemon
```

| Check | Observed result |
| --- | --- |
| Final Gradle task set | BUILD SUCCESSFUL |
| JVM tests | **42 passed; 0 failures/errors/skips**: existing 25 plus 17 printer cases |
| Debug APK | Generated at `app/build/outputs/apk/debug/app-debug.apk` |
| Instrumentation APK | Generated at `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| Android lint | **0 errors, 52 warnings**; not warning-free |
| ADB availability | `adb devices` listed no attached devices |
| Instrumentation execution | **Not run**; 13 compiled cases (8 existing, 5 new) require a device/emulator |
| Physical printer execution | **Not run**; no hardware model/protocol compatibility verified |
| Existing systems preservation | `git diff --exit-code -- frontend backend .github infrastructure docker-compose.yml` passed |

Toolchain remains JDK 21, Java 17 application compatibility, Gradle 8.13, AGP 8.11.1, minSdk 26 and compile/target SDK 35. Existing temporary SDK `/tmp/nakorn-sdk` was reused. Gradle/ADB required access to local host network interfaces beyond the restricted sandbox. No dependencies, backend services, production configuration or database migrations were changed. Automated printer tests use fake connections/drivers/services and never contact the VPS. Compiling the VPS-origin variant configures its runtime URL; it does not exercise the live backend.

Generated BuildConfig API_BASE_URL was inspected and equals `https://nakorn-thai.tech-labs.dev/`. Default Gradle `apiBaseUrl` behavior remains unchanged. Debug APK SHA-256 for this verification:

```text
3c5a0fafde883e487d8466b48ba226987bcc2df079e00302b0c4d4f561d599b6
```

`PrinterProvisioningTest` covers profile/endpoint/timezone validation; multiple-profile restoration and diagnostics; malformed/future envelope handling; missing identities and duplicate IDs; invalid individual profiles; driver registry/unsupported state and capabilities; explicit compatibility and BLE restrictions; ASCII wrapping and restaurant timezone; ADMIN/role guard; duplicate request gating; uncertain transmission without retry; transport-vs-physical status; timeout/permission errors; pre-transmission session checking; denied Bluetooth discovery without adapter access; cancellation of registered NSD services; write-deadline closure; journal failure preventing transmission; and a deterministic mocked Bluetooth connection deadline.

New compiled `PrinterUiTest` cases cover ADMIN profile saving and recreation, unsupported test-print disablement/footer preservation, FOH Activity navigation denial, BOH direct NavController entry/recreation denial, cancellable NSD discovery, and unsaved ADMIN editor restoration (five test methods, with multiple assertions). These cases use the instrumentation-only local backend fixture, extended with an ADMIN identity. They have **not executed**, so they are not evidence of rendered UI correctness or real Android permission behavior.

Initial verification found ambiguous Espresso imports and three Bluetooth MissingPermission lint errors. These were corrected by qualified test calls and explicit permission-revocation handling in the Bluetooth transport; no lint baseline or error suppression was added. Final lint warnings include existing and new UI string/formatting, layout/resource, permission/API and tool-version advisories. Inspect `app/build/reports/lint-results-debug.html` for details.

Before acceptance, run connected tests on the intended tablet and supported API levels; verify ADMIN-only access through sign-out/in, rotation and direct navigation; real Bluetooth runtime permission grant/denial/revocation, disabled adapter, pairing and SPP; BLE advertisements without implied printing support; NSD visibility and manual LAN configuration; socket failures and interruption; persistence across process restart; and one documented-compatible physical ASCII test. Inspect paper width, actual text wrapping, timezone and output. Verify no duplicate test on rotation, no automatic retry after uncertainty, and no effect on Orders/authentication/payment/polling. Paper-out/cover/cutter/Thai behavior is unsupported by the initial driver and must not be presented as verified.

Phase A is not operational acceptance for an unidentified printer. Hardware-specific drivers, manual order dockets, automatic printing, durable backend jobs and background operation remain separate phases. Full usage, security/storage limits, extension instructions and hardware acceptance steps are in README.md; the exact source inventory is in FILES.md.
