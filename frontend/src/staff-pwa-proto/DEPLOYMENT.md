# Independent prototype deployment

Build with `npm run build:staff-proto` from `frontend/`.
All generated/public files live inside `dist-staff-pwa-proto/staff-pwa-proto/`.
Rollup output paths explicitly namespace JS, CSS and image assets beneath
`/staff-pwa-proto/assets/`; no customer `/assets/` upload is needed. The original
Vite entry and deployment workflow remain unchanged.

## Push safety

The existing branch is `main`. `.github/workflows/deploy.yml` automatically deploys
the customer/Staff Web/PWA frontend for `frontend/**` pushes on main; it can also
activate the backend for backend changes. Android has a separate main/path-gated
build and prerelease workflow. Neither workflow deploys this prototype independently.

This focused manual deployment uses `[skip ci]` in its commit body so pushing it
cannot trigger the unrelated website deployment. Verify remote workflow runs after
pushing. Do not dispatch the existing deploy workflow for this prototype.

## Host inspection and domain boundary

The existing Nakorn Thai VPS is SSH alias `node3` (`173.212.214.180`). Its Nginx
HTTPS site is `/etc/nginx/sites-available/nakorn-thai`, enabled through a symlink.
Its configured domain is `nakorn-thai.tech-labs.dev`; the existing website root
is `/var/www/nakorn-thai/current`. The backend stays on `127.0.0.1:8080`, health
on `127.0.0.1:8081`; existing `/api/` and `/media/menu/` proxies are reused unchanged.

At inspection, the requested `nakorn.com.au` resolves to Wix and redirects to
`www.nakorn.com.au`, and no certificate/server_name for it exists on this VPS.
Changing its DNS would affect the protected customer site and is outside this
prototype deployment. Publish on the existing VPS hostname only if that target
is accepted; otherwise leave the public URL blocked pending a routing decision.
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
