# Foreground audio recovery after iPad lock/unlock

This change prepares foreground audio recovery for physical testing on the reported
iPad 9th generation running iPadOS 26.6.1. Automated verification does not establish
physical-device audibility.

## Findings and request trace

`StaffApp.jsx` creates one `createSoundEngine` during the authenticated workspace
mount and closes it on unmount (including StrictMode cleanup and logout). Both
Enable Sound and Test Sound use `enableSound`: resume the engine, then schedule
the selected two-tone alert. `useOrderMonitor` polls the existing role-filtered
queue; `createPendingTracker` establishes a silent baseline and calls the same
engine only for newly seen Pending IDs. Sound/volume preferences are device-local;
seen/unacknowledged IDs remain in the independent monitor.

Before this change, the engine listened only to AudioContext state changes, reused
every context except a closed one, and checked readiness solely by `state ===
'running'`. There were no visibility/pageshow/focus recovery listeners. A persistent
interrupted context could keep failing resume, and a running-but-stalled context
could schedule inaudible tones while the UI still reported Sound enabled. Closing
the app discarded that context, consistent with the reported successful reopen.

The exact failure on this physical iPad has not been captured. The mechanism is
consistent with WebKit reports of [foreground resume failures and frozen clocks](https://bugs.webkit.org/show_bug.cgi?id=263627)
and [contexts stuck in interrupted](https://bugs.webkit.org/show_bug.cgi?id=273511).
These reports concern older Safari versions; they support the recovery strategy,
not proof of a specific iPadOS 26.6.1 defect. WebKit also maintains an
[interruption lifecycle test](https://github.com/WebKit/WebKit/blob/main/LayoutTests/webaudio/audiocontext-state-interrupted.html).
Neither a running state nor an advancing clock proves the physical speaker is audible.

## Change and deliberate behavior

- On `visibilitychange` to hidden or `pagehide`, stop/disconnect scheduled tones,
  invalidate pending activation checks, detach and close the old AudioContext.
  No audio is kept alive to work around platform background restrictions.
- After unlock/return, `visibilitychange`, `pageshow` (including BFCache) and `focus`
  log the lifecycle and update readiness. With no valid context, the persistent
  control says **Tap to Enable Sound**. A tap creates and resumes a fresh context
  synchronously within the user activation, then plays a test using saved settings.
  Staff no longer need to restart the PWA to request a new audio session.
- If an existing context was suspended/interrupted without a hide event, pageshow
  or focus attempts one bounded resume. Concurrent events share that attempt.
  Running state and a progressing `currentTime` are checked before enabling sound.
  A blocked, stuck, closed, stalled or timed-out engine is retired; the next tap
  rebuilds it. No context is created by a lifecycle event without a user tap.
- Resume is bounded to three seconds. A 120ms clock sample detects a frozen
  `running` context, including a second sample after tone scheduling. Playback
  errors also clear readiness and release resources. This is engine-health
  detection, not an acoustic or device-volume measurement.
- Cleanup removes lifecycle/state listeners, cancels checks and closes resources.
  Stale resume/check completions cannot re-enable an old engine or update a newly
  mounted workspace. Tone nodes are stopped on interruptions so they cannot replay
  stale alerts when a context later resumes.
- Volume, sound choice, order polling, pending-ID tracking and acknowledgement are
  unchanged. Unlock/re-enabling plays only the deliberate test; old order alarms
  are not replayed. New Pending IDs after recovery use the existing alert path.

The deliberate tradeoff is that leaving the page (including lock or switching
apps) requires a fresh sound-enable tap on return. It avoids trusting WebKit's
possibly stale output resources. Merely focusing a healthy visible page does not
turn sound off. Background/locked-screen alarms remain unsupported.

## Diagnostics

Safari's remote Web Inspector console shows entries prefixed `[Nakorn Staff audio]`:
engine/context creation, visibility/pagehide/pageshow/focus, state changes, resume
request/result/error, playback scheduling/errors, stalled-clock detection and cleanup.
Each includes timestamp, visibility, context state and audio-clock value. Scheduling
also includes sound choice and volume. An in-memory ring retains the latest 80
entries; `readAudioDiagnostics()` returns a snapshot for automated diagnostics.
There are no customer/order identifiers, authentication tokens, storage writes or
network uploads. Capture this console on the real iPad during reproduction.

## Verification

Unit regressions simulate lock/unlock, suspended/interrupted/closed contexts,
running-but-frozen clocks, playback failure, autoplay rejection, hung resume,
concurrent taps, BFCache, hiding during activation and cleanup. They verify settings
and duplicate suppression survive recovery.

The existing PWA Chromium suite now changes tab visibility, confirms readiness is
cleared, simulates pageshow/focus, verifies a fresh context after the enable tap,
then verifies one sound for a new order and none for repeated polling. This is a
lifecycle simulation, not a physical screen-lock test.

For physical validation, on the restaurant iPad in Safari and installed mode:

1. Set the desired sound/volume, tap Enable Sound/Test Sound and confirm audibility.
2. Lock for both a short interval and several minutes, then unlock into the PWA.
   Verify Sound not enabled and Tap to Enable Sound. Tap once; verify the test
   is audible without restarting and preferences are retained.
3. Create a controlled Pending order; verify one foreground alert. Refresh repeatedly
   and confirm no duplicates. Confirm acknowledgement remains independent of acceptance.
4. Repeat app switching, repeated locks, orientation changes and back/forward restores.
   Capture diagnostics for any failed tap, interrupted state or stalled clock.
5. Check physical volume, Silent Mode and Focus separately. No alarms are promised
   while the screen is locked or the app is backgrounded.

Only PWA audio/UI, related tests and this guide are modified. Android native code,
backend workflows, order processing and Epson printing are untouched.

Local validation: `npm test` passed **224 tests** (seven new lifecycle cases),
`npm run test:staff-pwa-browser` passed **78 assertions**, and production build,
`test:staff-pwa-build` and `git diff --check` passed. The browser harness captures
actual audio console events, avoiding duplicate module instances during Vite hot
reload. Physical iPad Safari and installed-PWA lock/unlock audibility remain to be
verified using the steps above. Incoming orders only use the existing playback path;
they do not initialize, resume or automatically recover audio. Production Epson
printing remains disabled by the existing deployment configuration.
