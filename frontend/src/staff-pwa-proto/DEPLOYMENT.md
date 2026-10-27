# Independent prototype deployment

Build with `npm run build:staff-proto` from `frontend/`.
All generated/public files live inside `dist-staff-pwa-proto/staff-pwa-proto/`.
Rollup output paths explicitly namespace JS, CSS and image assets beneath
`/staff-pwa-proto/assets/`; no customer `/assets/` upload is needed. The original
Vite application entry remains unchanged; deployment workflows only change their
prototype ownership and trigger isolation.

## Dedicated GitHub Actions pipeline

`.github/workflows/deploy-staff-pwa-proto.yml` deploys the prototype on `main`
pushes affecting its sources, HTML entry, Vite config, deployment helper/tests,
locked npm dependencies, consumed signature photographs or the two shared
browser fixtures. Workflow-only changes do not auto-deploy. It also supports
`workflow_dispatch` on `main` and, for the isolated Epson hardware test, the
exact branch `epson-tmpa-ipad-test`. Dispatch checks out the selected commit SHA.
Feature-branch pushes do not deploy automatically.

Node 24 and `npm ci --include=optional` match repository conventions. The pipeline
runs prototype unit tests, deployment/rollback tests, a prototype-only build,
asset/checksum validation and a mocked production installation browser test.
It never starts another backend/database or sends domain mutations.

Secrets reused (names verified in the repository): `VPS_HOST`, `VPS_SSH_KEY`,
`VPS_KNOWN_HOSTS`. SSH uses `deploy`, batch mode, a private key file with mode 600,
strict host-key checking and the approved known-host entries. Credentials are
removed by an always-run cleanup step. No new secret or variable is required.

Concurrency group `staff-pwa-proto-deploy` never cancels a deployment in progress.
An additional VPS file lock prevents concurrent prototype activations. The helper
is sent over SSH stdin; no persistent executable or Nginx change is installed.

The existing `deploy.yml` excludes prototype-only source/entry/config paths.
Workflow-only changes no longer automatically publish the existing website;
intentional configuration-only deployments still use its manual dispatch.
Ordinary website/backend/infrastructure deployment paths remain active. Shared
package/image changes may legitimately trigger both frontends because both consume
them. No workflow dispatches another workflow, and routine prototype commits do
not require `[skip ci]`.

**Publishing this workflow to main enables automatic prototype deployment.** It
has been tested locally and with read-only live checks; an actual Actions run and
activation are separate from those checks.

## Automated release safety

CI uploads only to `/var/www/nakorn-thai/prototype/releases/<sha>-<run>-<attempt>`.
The upload cannot overwrite an existing release. A generated `release-manifest.json`
contains the intended commit and every public file's SHA-256. Both local and remote
validation reject missing/corrupt files, symlinks, out-of-scope files, incorrect
manifest scope, root-level assets and unsafe worker cache paths.

The activation helper records the previous prototype target and protected state
in `prototype/backups/<release>.json`, then atomically replaces only the prototype
`current` symlink. It fetches live HTML/JS/CSS/images/icons/manifest/worker and
compares their bytes with the release; checks MIME types, fallback and missing-asset
404; checks the existing website/PWA and read-only backend availability/health;
and verifies unchanged protected file hashes, Nginx files and backend PID/release.
A failure restores the previous prototype symlink and verifies the restored release.
The existing manual release is supported without a CI checksum manifest.

The GitHub job summary reports status, revision, release and live URL. No release
pruning is automatic. Retained failed and previous releases support investigation
and rollback. The pipeline does not restart or reload any service.

Run deployment tests locally without touching the VPS:

```sh
python3 -B -m unittest discover -s frontend/src/staff-pwa-proto/deploy -p 'test_*.py' -v
```

## Host inspection and domain boundary

The existing Nakorn Thai VPS is SSH alias `node3` (`173.212.214.180`). Its Nginx
HTTPS site is `/etc/nginx/sites-available/nakorn-thai`, enabled through a symlink.
Its configured domain is `nakorn-thai.tech-labs.dev`; the existing website root
is `/var/www/nakorn-thai/current`. The backend stays on `127.0.0.1:8080`, health
on `127.0.0.1:8081`; existing `/api/` and `/media/menu/` proxies are reused unchanged.

At inspection, the requested `nakorn.com.au` resolves to Wix and redirects to
`www.nakorn.com.au`, and no certificate/server_name for it exists on this VPS.
Changing its DNS would affect the protected customer site and is outside this
prototype deployment. The approved live URL is `https://nakorn-thai.tech-labs.dev/staff-pwa-proto/`.
Do not redirect or replace the Wix customer website.

## Release layout and activation

Use a release ID `<commit>-manual-<UTC timestamp>` under:

```
/var/www/nakorn-thai/prototype/releases/<release>/staff-pwa-proto/
/var/www/nakorn-thai/prototype/current -> releases/<release>
```

Upload the contents of `frontend/dist-staff-pwa-proto/` into that release directory.
Record the previous prototype current target, the existing website current target,
the backend current target/PID/start time, original Nginx config hash and source
file hashes before activation. Atomically replace only the prototype current
symlink. Retain old prototype releases.

Install `deploy/nginx.conf` as `/etc/nginx/snippets/nakorn-staff-pwa-proto.conf`.
Back up `/etc/nginx/sites-available/nakorn-thai` outside `sites-enabled`, then add
only this line inside its existing HTTPS server block:

```
include /etc/nginx/snippets/nakorn-staff-pwa-proto.conf;
```

Run `sudo nginx -t`; reload Nginx only after it passes. Restore the backup if
validation fails. Do not change TLS, API/media proxies, the existing website root,
existing worker/manifest or backend service. Never upload over the existing
website release. No backend restart, database writes or deployment are needed.

## Non-destructive verification

Check prototype HTML, JS/CSS/images/icons, manifest, worker, asset MIME types,
missing-asset 404, hash-route refresh, worker scope and cache namespace. Confirm
customer `/`, Staff Web `/#/staff` and existing `/staff/` plus their static entry
hashes remain unchanged; compare backend PID/start time and health before/after.

`tests/liveSmoke.browser.mjs` checks the real deployed frontend/backend read paths
without mocks or domain mutations. Optional existing staff credentials can be
provided through `PROTO_SMOKE_USERNAME` and `PROTO_SMOKE_PASSWORD`; no credentials
are committed or printed. Without credentials, report authenticated ADMIN/FOH
checks as unverified. No orders/payments/settings are written by this smoke test.

## Rollback

For a subsequent release, atomically point only
`/var/www/nakorn-thai/prototype/current` to its recorded previous release. Nginx
reload is unnecessary when only this symlink changes.

For first deployment rollback, restore the recorded Nginx site backup, validate
with `nginx -t`, then reload. Remove the prototype current symlink only after the
include is removed. Existing website and backend symlinks must remain unchanged.
Retain the uploaded release and backup for diagnosis. No database rollback applies.
