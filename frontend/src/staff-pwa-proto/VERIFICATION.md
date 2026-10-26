# Verification results

Evaluation date: 26 October 2026. All browser mutation tests used synthetic,
in-memory APIs. No live restaurant data, payment provider or printer was touched.

| Check | Result |
| --- | --- |
| Existing `npm test` | 237 passed, 0 failed/skipped; includes protected order/audio contracts |
| Prototype `npm run test:staff-proto` | 140 passed, 0 failed/skipped |
| Prototype comprehensive browser (development) | 359 assertions passed; 100 route/viewport renders; no uncaught exceptions |
| Prototype comprehensive browser (production) | 361 assertions passed; 100 route/viewport renders; one initial order-queue request; no uncaught exceptions |
| Prototype menu browser | Passed; item create/edit/pricing/archive coverage, collection creation/metadata, membership create/edit/remove, category create/edit/remove, schedule create/edit/remove, versions/validation/failed reload/Back navigation |
| Prototype order browser | Passed; simultaneous drafts, refresh/reorder/focus, history/filtering, versions/conflicts/discard, duplicate/stale requests, principal/role/session cleanup |
| Prototype settings browser | Passed; business/provider fields, dependencies, write-only secrets, explicit tests, conflicts, draft preservation, navigation/history/sign-out, audit isolation and roles |
| Existing Staff Web order browser | Passed unchanged |
| Existing Staff Web menu/settings browsers | Passed unchanged |
| Existing Staff PWA browser | 90 assertions passed unchanged; order actions, foreground queue, audio/recovery, diagnostics, disabled printing and layouts |
| Existing Staff PWA build checks | Passed |
| Prototype production build | Passed; separate `dist-staff-pwa-proto/` |
| Existing production build | Passed; existing website/staff entries retained |
| Prototype production installation | Passed; manifest/icons, worker scope/control/reload, immutable JS/CSS cache and existing-cache isolation |
| Worker unit checks | API/HTML/media bypass; only prototype namespace cleaned; original PWA/customer caches preserved |
| Protected-source fingerprint tests | 200 PWA/Android/Web shell/style/build files plus 51 original workflow modules unchanged |
| Protected-path `git diff --exit-code` | Passed: original PWA, domains, app/styles/public, original Vite entry, Android and backend unchanged |
| `git diff --check` | Passed |

The existing frontend unit run reports a sandbox-denied Vite WebSocket listener
while its tests still complete successfully. Browser suites use separately allowed
local servers and a disposable Chrome profile.

An initial concurrent run of the existing PWA browser suite lost foreground focus
to another test page and paused its monitor. It was rerun alone and passed all 90
assertions. Foreground/audio suites should run sequentially.

## Responsive and touch evidence

Comprehensive checks render 20 routes/detail sections at each size:

- 810×1080: iPad-style portrait.
- 1080×810: iPad-style landscape.
- 1360×1000: desktop.
- 768×1024: tablet portrait, also representative of Android tablet browser layout.
- 390×844: narrow touch layout.

Checks include page overflow, persistent footer bounds, visible controls at least
44px high (implementation uses 48px), hamburger/modal bounds, 72px collapsed rail,
role navigation, direct restricted routes and absence of unauthorized feature
requests. The menu suite also verifies 1360/820/390px layouts and stacked narrow
menu tables.

Development screenshots/results: `/tmp/nakorn-staff-proto/`.
Production artifacts are recorded separately under `/tmp/nakorn-staff-proto-production/`.
These directories are evaluation artifacts, not committed project assets.

## Functional limits of the evidence

Mock tests prove frontend behavior, request shape and error handling. They do not
replace server integration tests. Backend code/contracts were unchanged; no Maven
or database integration run was needed to modify backend behavior, and none was
performed. Server-side authorization, last-admin protection, password byte bounds,
real schedule evaluation, image dimensions, notifications and provider behavior
are not certified by the mocked tests. The existing development database is
authorized for workflow testing, but this deployment performs no domain writes.

Physical iPad Safari, Android tablet hardware, native installation/update behavior,
virtual-keyboard interactions and production infrastructure were not tested.
No prototype audio or locked-screen alarm capability is claimed. Printing remains
disabled. See DEPLOYMENT.md for the separately authorized prototype deployment.

Same-origin backend refresh cookies remain shared by the applications; frontend
state/cache/storage isolation cannot change that existing server contract. Use a
separate browser profile for manual prototype evaluation.

## Deployment preparation

Namespaced Rollup output now keeps all prototype JS/CSS/images beneath
`/staff-pwa-proto/assets/`. Prototype unit/isolation tests (140) and the production
installation test are rerun for this deployment-only path change. No workflow
logic or protected application source is changed; the prior full browser matrix
is retained rather than repeated.

## Dedicated CI/CD implementation checks

The dedicated workflow uses the verified existing VPS secret names and Node 24.
Local validation passed: 140 prototype tests, 19 deployment/rollback/trigger tests,
prototype production build, generated-file/checksum validation, dedicated workflow
`actionlint`, YAML parsing and Bash syntax checks. The existing workflow's trigger
edit was also checked; its pre-existing checkout/setup-node v3 deprecation warnings
were left outside this change.

The new deployment helper's `smoke` operation was run read-only over SSH against
the active `706b2de` manual release. All 12 public artifact hashes, prototype/fallback
responses, missing-asset 404, existing website/PWA and backend availability/health
checks passed. No release was uploaded/activated and no service was restarted or
reloaded during CI/CD implementation. Automated rollback was tested in temporary
local release trees with injected failures, not by disrupting the live application.

An actual GitHub Actions execution is not claimed: the workflow must first be
published to main. Its first matching push will deploy only the prototype; it can
also be run manually on main. No commit/push is part of these local validation results.


## Shared Orders extraction

The prototype now consumes the original Staff PWA operational Orders implementation through `src/shared/staff-orders`. See [architecture, parity, validation and CI/CD impact](../shared/staff-orders/README.md). Both shells retain independent routing, authentication adapters, preferences, manifests and workers. Coordinated release through the existing GitHub Actions workflows is approved; local validation is documented in the linked report.
