# Google Reviews backend-only release

This procedure uses the existing versioned backend release directory, systemd unit, and `activate-backend.sh`. It runs no GitHub workflow, frontend build, frontend upload, or frontend symlink switch. The public site and staff PWA assets are outside the release scope.

## Release identity

| Item | Exact reference |
| --- | --- |
| Source base | `988bf039d8863d22fbd3c9922de435ca31dc27d3` |
| Candidate release ID | `2e1f09a34a2509a0c5c71a9a93a3a0f4b700889a86965cff9b965ca6ab838973-1795338001-1` |
| Candidate JAR SHA-256 | `2e1f09a34a2509a0c5c71a9a93a3a0f4b700889a86965cff9b965ca6ab838973` |
| Rollback release ID | `7bc54e7321324996503f4dfd49efd48d7fd53e93-40298141725-1` |
| Rollback JAR SHA-256 | `3f25d1ba06e10b948f5338b5cad7108f70db76ef903bdc4e27b3c06979d3fc1a` |
| Activation script SHA-256 | `6f545ad258386376183797fba5d2201bf4b51d49f944f26eab0edd16f6a72430` |

Local artifacts are in `backend/target/google-reviews-release/`. Preserve this directory before cleaning Maven output. `source-manifest.json` records the base revision, every included source digest and the working-tree delta. That delta comprises `README.md`, `GOOGLE_REVIEWS.md`, the four classes in `restaurant/googlereviews`, `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `V40__google_business_reviews.sql`, and the two Google Reviews test classes. The source archive freezes their exact contents and the remaining backend build inputs. This runbook was written after freezing those inputs. The candidate contains no Android or frontend work. The rollback JAR was streamed from the recorded production release and matched its previously observed checksum.

### Rebuild locally

Use Temurin Java 21.0.12, Maven 3.9.11, and the Maven dependencies from the verified build. The frozen source archive contains backend source and build inputs. From the candidate directory:

```bash
sha256sum -c SHA256SUMS
build_dir=$(mktemp -d)
tar -xzf backend-source.tar.gz -C "$build_dir"
(
    cd "$build_dir/backend"
    mvn -o -Dproject.build.outputTimestamp=2026-11-22T00:00:00Z -DskipTests package
)
printf '%s  %s\n' \
  2e1f09a34a2509a0c5c71a9a93a3a0f4b700889a86965cff9b965ca6ab838973 \
  "$build_dir/backend/target/nakorn-thai-backend-0.1.0-SNAPSHOT.jar" |
  sha256sum -c -
```

Prefer the already-tested JAR. Any rebuilt JAR with a different digest needs its own release identifier and verification record. Run Maven verification with a dedicated local PostgreSQL database as described in the backend README.

## Rehearsal results

- The exact rollback JAR started with the production Spring profile on disposable PostgreSQL and applied migrations through V39. The baseline passed identity login and refresh rotation, role and CSRF rejection, order and reservation reads, timekeeping, and malformed PayPal callback rejection.
- The candidate started on a copy of that seeded V39 database. Flyway recorded one successful V40 migration and one Google Reviews connection row. The candidate passed the baseline checks and returned HTTP 200 for the disabled public feed, HTTP 200 with `MISSING_OAUTH_SETTINGS` for ADMIN status, and the expected denied responses for unauthorized access and missing CSRF.
- The exact rollback JAR restarted against the migrated copy without changing the V40 history or review connection row. The business smoke suite passed and Google Reviews returned the expected pre-release HTTP 401.
- Maven verification from the frozen source passed **511 tests** with Java 21 and disposable PostgreSQL. Its packaged JAR matched the recorded candidate SHA-256.
- The rehearsal ran with generated local credentials and no live payment, notification, or Google credentials. The copy was built locally from the previous JAR and contains no production records.

## Predeployment checklist

- Confirm the live `current` symlink still points to the exact rollback release and verify its JAR digest. Stop if production has changed since investigation.
- Confirm the successful Flyway version is 39, V40 is absent, restaurant row `1` exists, and the three Google Reviews tables are absent. Check for failed migrations and unexpected pending scripts.
- Take a consistent PostgreSQL backup under the approved backup procedure. Verify the archive is readable and restorable to a disposable database. Record the backup location, completion time and recovery point. Protect the backup and keep its credentials out of command output.
- Confirm the candidate and activation script checksums, sufficient release storage, the low-traffic window and the operator responsible for rollback. Record the existing frontend symlink target with a read-only check if verifying release isolation on the host.
- Record predeployment smoke baselines on authorized test accounts. Keep JWT secrets, payment settings, provider enable flags, and credential encryption settings stable across the restart.
- Confirm public publication is disabled. Google client values can be omitted for this release. With the integration enabled by default and OAuth values absent, the admin API returns `MISSING_OAUTH_SETTINGS` and the public feed remains empty. The public endpoint and Flyway still operate.
- **Authorization gate. Stop before the first production write.** The production backup creation, release-directory creation, artifact upload, symlink switch, service restart and startup migration require explicit production authorization. Read-only baseline checks and local rehearsal are preparation.

## Backend-only activation after authorization

Run from the local candidate directory, with the verified deployment host assigned to `DEPLOY_TARGET` using the existing SSH configuration and known-host entry. This example targets only the backend release tree. The first remote command verifies that the current release is still the recorded rollback, then creates the candidate directory. If any step fails, stop before activation and investigate.

```bash
set -euo pipefail
: "${DEPLOY_TARGET:?Set the verified deploy SSH destination}"
candidate=2e1f09a34a2509a0c5c71a9a93a3a0f4b700889a86965cff9b965ca6ab838973-1795338001-1
rollback=7bc54e7321324996503f4dfd49efd48d7fd53e93-40298141725-1
base=/opt/nakorn-thai/backend
ssh_opts=(-o BatchMode=yes -o StrictHostKeyChecking=yes -o UpdateHostKeys=no)
sha256sum -c SHA256SUMS

ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" \
  "test \"\$(readlink -e '$base/current')\" = '$base/releases/$rollback' &&
   printf '%s  %s\\n' '3f25d1ba06e10b948f5338b5cad7108f70db76ef903bdc4e27b3c06979d3fc1a' '$base/releases/$rollback/backend.jar' | sha256sum -c - &&
   mkdir '$base/releases/$candidate'"
rsync -az -e 'ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o UpdateHostKeys=no' \
  backend.jar "$DEPLOY_TARGET:$base/releases/$candidate/backend.jar"
ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" \
  "printf '%s  %s\\n' '2e1f09a34a2509a0c5c71a9a93a3a0f4b700889a86965cff9b965ca6ab838973' '$base/releases/$candidate/backend.jar' | sha256sum -c - &&
   test \"\$(readlink -e '$base/current')\" = '$base/releases/$rollback'"
ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" "bash -s -- '$candidate'" < activate-backend.sh
```

The unit restarts one JVM and Flyway applies V40 during startup. The activation script's loopback health check covers startup. Continue with the smoke checklist before declaring success.

## Smoke checklist

| Area | Check and expected result |
| --- | --- |
| Backend health | Private `GET http://127.0.0.1:8081/actuator/health` returns HTTP 200 and `UP`. The active symlink resolves to the candidate. Read-only Flyway metadata shows one successful V40 record. |
| Google Reviews | Public `GET /api/public/google-reviews` returns HTTP 200 with `{"enabled":false,"curated":true,"reviews":[]}`. Use the authorized ADMIN session to verify `GET /api/staff/restaurant/integrations/google/status` returns HTTP 200 and `MISSING_OAUTH_SETTINGS` while credentials are absent. |
| Staff authentication | Sign in with a designated staff test account. Verify `/api/identity/me` and refresh with both the refresh cookie and the session cookie associated with the CSRF token. Check that the refresh cookie rotates. Keep tokens out of logs. |
| RBAC and CSRF | The admin integration status route returns 401 anonymously and 403 for FOH. An authenticated connect POST without a CSRF token returns 403. Leave Google consent outside this smoke test. |
| Ordering | Public `GET /api/orders/options` and staff `GET /api/staff/foh/orders` return 200 for the appropriate user. Confirm queue data renders. Use local or staging fixtures for order creation and transitions. |
| PayPal callbacks | Post malformed JSON such as the single character `{` with `Content-Type: application/json` to `/api/payments/paypal/webhook`. Expect 400 from parsing, before signature verification, inbox insertion or provider calls. Verify valid-signature handling in staging with sandbox credentials or controlled doubles. Check existing production callback metrics for normal traffic without replaying provider events or initiating payments. |
| Reservations | Public `GET /api/reservations/options` and authenticated `GET /api/staff/reservations?date=YYYY-MM-DD` return 200. Use an approved date and inspect only the response status or on-screen UI. |
| Timekeeping | Authenticated `GET /api/staff/timekeeping/me` returns 200. Use staging for clock-in or clock-out smoke tests. |

## Rollback after authorization

The existing script automatically restores the previous target if candidate startup or health fails. If a smoke check fails after activation, verify the rollback JAR digest and explicitly select that prior release with the same script. Recheck health and the existing business flow endpoints.

```bash
ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" \
  "printf '%s  %s\\n' '3f25d1ba06e10b948f5338b5cad7108f70db76ef903bdc4e27b3c06979d3fc1a' '/opt/nakorn-thai/backend/releases/7bc54e7321324996503f4dfd49efd48d7fd53e93-40298141725-1/backend.jar' | sha256sum -c -"
ssh "${ssh_opts[@]}" "$DEPLOY_TARGET" \
  'bash -s -- 7bc54e7321324996503f4dfd49efd48d7fd53e93-40298141725-1' < activate-backend.sh
```

The previous JAR is expected to return HTTP 401 for the Google Reviews public endpoint after rollback. Leave V40 and its tables in place. Database restore or Flyway history changes require a separate recovery decision. Restoring the backup can discard orders, payments, reservations and other changes accepted after the backup.

## OAuth follow-up and residual risks

OAuth configuration can follow the JAR rollout. A separately authorized change to the protected backend environment supplies the Google client ID, client secret, and exact registered HTTPS redirect URI. The service needs a restart to load those values. Keep the existing encryption key. Verify Google Business Profile API approval and owner access to a verified location before live consent. The public publication policy flag remains false until approval for this use. See [Google Reviews configuration](GOOGLE_REVIEWS.md).

The service restart briefly interrupts API requests and can overlap with PayPal callback delivery. The workflow's single-instance health gate cannot establish live provider readiness or business-flow behavior. The database rehearsal uses a synthetic copy generated by the exact previous JAR and does not cover production volume, locks, or production-specific PostgreSQL overrides. Recheck those during the authorized preflight.
