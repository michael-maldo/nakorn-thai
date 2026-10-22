# Staff PWA foreground audio recovery — Phase 2

Phase 2 adds best-effort foreground recovery while preserving manual activation.
Phase 1 (`fb556c7`) was physically verified on the restaurant's iPad 9th generation,
iPadOS 26.6.1: manual Enable Sound after lock/unlock restores audible alerts without
restarting. Phase 2 retains that fallback and adds best-effort automatic recovery.
Phase 2's physical behavior is not yet verified.

## Findings and request trace

`StaffApp.jsx` creates one sound engine per authenticated workspace and closes it on
cleanup/logout. Manual Enable Sound and Test Sound use the existing gesture handler.
`useOrderMonitor` first publishes the authoritative queue, then sends newly detected
Pending IDs to the engine without awaiting recovery. Detection, role authorization,
10-second polling, the silent initial baseline and acknowledgement semantics remain
unchanged. The tracker now exposes a copy of the latest arrivals to the audio adapter;
IDs remain only in memory and never enter diagnostics.

Phase 1 intentionally closes the AudioContext on hide/pagehide, which made a new tap
necessary. A suspended/interrupted context may also fail to resume, and WebKit has
reported contexts whose state is running while their clocks are frozen. See
[WebKit foreground recovery report](https://bugs.webkit.org/show_bug.cgi?id=263627)
and [stuck interruption report](https://bugs.webkit.org/show_bug.cgi?id=273511).
Those reports concern older releases and do not diagnose this exact iPadOS version.

The [Web Audio specification](https://www.w3.org/TR/webaudio-1.0/#dom-audiocontext-resume)
permits browsers to withhold activation; resume can remain pending until allowed.
An automatic call is legal to attempt, but a poll, order arrival, focus or visibility
event is not a staff gesture. No browser restriction is bypassed here.

## Recovery policy

- Staff must manually enable sound once in the current workspace/shift. No new
  context or sound is created automatically before that successful initial action.
  Reload/login resets that intent; no permission claim or token is persisted.
- Hide/pagehide still stops/disconnects tone nodes, cancels in-flight checks and
  closes the context. No audio is kept alive for locked-screen/background alarms.
- On visible restoration, pageshow (including BFCache) or focus, attempt recovery
  only if sound was enabled and a previous automatic failure has not latched the
  manual-activation fallback. These events never generate audible test tones.
- Resume an existing context when possible; replace a closed one directly. An
  interrupted, stalled or failed existing resource gets at most one fresh-context
  replacement. A new context gets one attempt. Blocked or timed-out resume is not
  retried. A shared three-second deadline bounds resume plus clock checks across
  both possible resources, rather than three seconds per retry.
- All restoration and incoming-order callers share one recovery promise. A failure
  latches automatic recovery off until a manual Enable Sound action. Later focus,
  polls, genuinely new orders and subsequent locks do not keep creating contexts.
- A manual tap preempts an automatic resume that might be waiting for activation.
  It creates/resumes the fresh resource synchronously in the tap, preserving the
  physically verified manual path. Duplicate enable calls share the activation.
- A resolved resume promise is insufficient: require running state and clock
  progress over 120ms. Incoming notifications repeat this silent health check even
  for an apparently running engine. A second check after scheduling detects a
  subsequently frozen clock. There are no diagnostic beeps or silent media tricks.
- On success, publish Sound enabled; on known failure, publish Sound not enabled
  and retain the persistent **Tap to Enable Sound** control. No recovery dialogs
  interrupt staff. A running/advancing engine is technical readiness, not proof of
  physical audibility, speaker routing, device volume or notification delivery.

## Incoming-order delivery and no-replay policy

`createPendingTracker` retains its silent first snapshot and seen/unacknowledged
sets. Only genuinely new Pending IDs enter `notifyPending`. The engine reserves
IDs synchronously before awaiting anything, so repeats cannot produce another
notification attempt. Existing settings are read at actual scheduling time, preserving
sound choice and the latest volume even if staff adjust it during recovery.

The established one-sound-per-poll-batch behavior is preserved. Concurrent new batches
waiting on the same audio recovery coalesce into one normal alert. Each included ID
is consumed at most once, including on failure. No per-order/customer data is logged.

On success, schedule the ordinary alert after clock verification (normally about
120ms; never hold queue rendering for this). On failure, leave the order and visible
acknowledgement banner intact and require manual activation. There is no persistent
notification backlog, retry on later polls, historical-order replay on restoration,
or replay of a failed notification after manual activation. A manual tap cancels a
pending automatic notification and plays only its deliberate test. A genuinely new
order after that can alert normally. Hide/logout also cancel delayed playback; late
promises cannot alert a different workspace. Manual reactivation is the explicit
fallback; physical testing must confirm whether automatic recreation is allowed.

## Diagnostics and changed files

Console prefix: `[Nakorn Staff audio]`. The latest 80 entries remain in memory and
are available through `readAudioDiagnostics()`. Entries include timestamp, context
state, visibility, clock value and recovery source/outcome. Outcomes include
`successful`, `blocked`, `interrupted`, `stalled`, `timed-out` and `failed`; resume
errors identify intermediate failures even when a replacement succeeds. Scheduling
records sound/volume. Context retirement logs cancellation/cleanup reasons.
No order IDs, contacts, credentials, storage writes or diagnostic uploads are added.

| File | Change |
| --- | --- |
| `frontend/src/staff-pwa/features/notifications/alerts.js` | Bounded shared recovery, clock verification, batch notification deduplication and local outcomes |
| `frontend/src/staff-pwa/features/orders/useOrderMonitor.js` | Pass only genuinely new IDs after queue publication; no awaited audio |
| `frontend/src/staff-pwa/StaffApp.jsx` | Invoke the notification adapter; preserve manual control and update guidance |
| `frontend/src/staff-pwa/tests/core.test.js` | Automatic/rejected/stalled/concurrent recovery, cancellation, settings and no-replay regressions |
| `frontend/tests/e2e/staffPwa.browser.mjs` | Native Chromium tab restoration plus controlled rejection/hung-resume scenarios and queue independence |
| `docs/staff/ipad-audio-recovery.md` | Findings, behavior, validation and physical procedure |

No backend, database, PayPal, Android native, authentication, deployment workflow or
Epson printer implementation is changed. Production printing remains disabled.

## Physical iPad verification

For physical validation after deployment, test Safari and installed mode:

1. Confirm the expected app version. Set sound/volume, tap Enable Sound once, confirm
   the test is audible and record the baseline preferences.
2. Lock briefly, unlock and wait up to three seconds. If automatic recovery reports
   Sound enabled, create a controlled new Pending order and confirm exactly one
   audible normal alert. Restoration itself must not beep or replay old orders.
3. Repeat after several minutes, app switching, repeated locks and back/forward
   restoration. Check both portrait and landscape and collect Web Inspector logs.
4. If automatic recovery fails, verify the prominent activation control and a single
   bounded attempt in logs. Confirm new orders still appear promptly. Further focus,
   polls and new orders must not continuously create contexts or play stale alarms.
5. Tap Enable Sound; confirm the test is audible without restarting. Old failed
   notifications must not replay. A subsequent genuinely new order should sound once.
6. Test several arriving orders during recovery, repeated refresh, acknowledgement,
   and a volume change while recovery is pending. Expect one sound per/coalesced
   batch and no duplicate per order. Verify the saved volume has not changed.
7. Test logout while recovery is pending and sign in again. No late sound/data should
   cross the session; manually enable sound for the new workspace.

No automated test proves Safari's real autoplay permissions or physical speaker
output. The app cannot override Silent Mode, Focus, routing or physical volume.
Background/locked-screen alarms remain unsupported. Automatic recovery may always
need the manual fallback on this iPad; that result is acceptable and must be recorded
rather than treating a successful API/clock check as audible delivery.

## Local verification results

- Full `npm test`: **232 passed**, zero failures/skips (27 PWA core cases, eight
  additional cases beyond the Phase 1 baseline plus updated lifecycle expectations).
- Production `npm run build` and `test:staff-pwa-build`: passed.
- `test:staff-pwa-browser`: **84 assertions passed**, including native Chromium
  restoration, simulated Safari denial/hung resume, manual takeover and no replay.
- `test:staff-pwa-install-browser`: **14 assertions passed** on the production preview.
- `test:orders-browser`: passed existing drafts, versions, CSRF/status/payment,
  concurrent writes, failures, filtering and principal/session cleanup regressions.
- `test:staff-layout-browser`: **672 assertions passed**.
- `git diff --check`: passed. Deployment status is reported separately from these
  local verification results.

These are local mocks/Chromium checks, not iPadOS 26.6.1 automatic-recovery validation.
