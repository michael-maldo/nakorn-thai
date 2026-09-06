# Backend deployment

`.github/workflows/deploy.yml` deploys the backend and frontend on pushes to
`main` or manual runs from `main`. It runs Maven verification with a disposable
PostgreSQL 16 service and builds the frontend before connecting to production.
The backend uses Java 21 via [setup-java](https://github.com/actions/setup-java).
Existing secrets `VPS_HOST`, `VPS_SSH_KEY`, and `VPS_KNOWN_HOSTS` are reused.

## One-time VPS setup

Run these commands as an administrator on the existing Ubuntu/Debian VPS,
from a checkout of this repository. The existing `deploy` SSH account and
PostgreSQL database must already exist; see `backend/README.md` for database setup.

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jre-headless curl rsync
sudo useradd --system --user-group --no-create-home --shell /usr/sbin/nologin nakorn-backend
sudo install -d -o deploy -g deploy -m 755 /opt/nakorn-thai/backend/releases
sudo chown deploy:deploy /opt/nakorn-thai/backend
sudo install -d -o root -g root -m 700 /etc/nakorn-thai
sudo install -m 600 backend/.env.prod.example /etc/nakorn-thai/backend.env
sudoedit /etc/nakorn-thai/backend.env
sudo install -m 644 infrastructure/systemd/nakorn-thai-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable nakorn-thai-backend.service
sudo visudo -f /etc/sudoers.d/nakorn-thai-backend
```

Set the actual production database password and admin password hash in
`backend.env`; preserve the loopback listeners on ports 8080 and 8081.
Keep this file on the server, outside releases. Do not rerun the template install
over an existing production environment file. Add this exact sudoers rule:

```sudoers
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart nakorn-thai-backend.service, /usr/bin/systemctl stop nakorn-thai-backend.service
```

The first workflow run uploads the JAR and starts the service. Add the location
block from `infrastructure/nginx/nakorn-thai.conf` inside the existing HTTPS
server block in `/etc/nginx/sites-available/nakorn-thai`. This file is a snippet,
not a complete site configuration; preserve the Certbot certificate settings,
HTTP redirect and frontend root. Then run `sudo nginx -t` before
`sudo systemctl reload nginx`. Its `/api/` proxy must reach `127.0.0.1:8080`.
The workflow does not install Nginx configuration.

## Release behavior and troubleshooting

JARs are stored at `/opt/nakorn-thai/backend/releases/<commit>-<run>-<attempt>/backend.jar`.
The workflow switches `current`, restarts `nakorn-thai-backend.service`, and polls
the private health endpoint before activating the frontend. Failed backend health
checks restore the previous JAR and fail the workflow. On a failed first deployment,
the service is stopped. Releases are retained for manual recovery.

Flyway runs on startup. JAR rollback does not undo database migrations; migrations
must remain compatible with the previous release. Back up production before schema
changes. A frontend upload failure after backend success leaves the new backend running.

```bash
sudo systemctl status nakorn-thai-backend.service
sudo journalctl -u nakorn-thai-backend.service -n 100 --no-pager
curl -fsS http://127.0.0.1:8081/actuator/health
```

If the website reports an invalid menu response, compare the direct API with
the HTTPS route from the VPS:

```bash
curl -i http://127.0.0.1:8080/api/menu/collections/signature-dishes/items
curl -i https://nakorn-thai.tech-labs.dev/api/menu/collections/signature-dishes/items
```

Both should return 200 and JSON containing an `items` array. If only the HTTPS
response contains HTML, check that the `/api/` location is inside the active
HTTPS server block. Otherwise the frontend's `try_files` fallback may serve
`index.html` for API requests. Keep `proxy_pass http://127.0.0.1:8080;` without
a trailing slash so Nginx preserves the `/api/` path. If the direct connection
is refused, start the backend service and inspect its startup journal first.


## Menu photo uploads

V9 adds persistent horizontal/vertical focus (0–100) and zoom (1–3) to menu images.
The dashboard supports JPEG/PNG uploads up to 8 MB and 16 megapixels. Uploads are
validated by decoding and re-encoded as JPEG, with generated filenames. Photos
are public menu assets. Admin authentication and CSRF protect uploads and edits.

Install the updated service unit from your checkout before using uploads:

```bash
sudo install -m 644 infrastructure/systemd/nakorn-thai-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl restart nakorn-thai-backend.service
```

`StateDirectory=nakorn-thai` lets the service write under `/var/lib/nakorn-thai`.
`MENU_MEDIA_DIRECTORY=/var/lib/nakorn-thai/menu-media` stores photos outside JAR
and frontend releases. Back up this directory together with PostgreSQL. Local
runs default to `backend/menu-media` when started from backend/. The existing
`MEDIA_BASE_URL=/media/` setting resolves uploaded images to `/media/menu/<uuid>.jpg`.

Inside the active HTTPS Nginx server, update `/api/` to allow `client_max_body_size
9m;` and add the `/media/menu/` proxy block from `infrastructure/nginx/nakorn-thai.conf`.
Validate with `sudo nginx -t` and reload Nginx. The workflow does not install either
server configuration. The media port remains internal; Nginx serves its public URL.

To use: save a new dish, edit it, choose a photo, adjust horizontal/vertical focus
and zoom in the card-shaped preview, enter descriptive alt text, then choose
**Save photo and focus**. Save text changes first; image saves have a separate
version check. Existing bundled seed photos can be saved into persistent storage
through the same control without choosing a replacement file.

Replacement photos get new URLs. Previous files are retained for rollback and
must be included in storage capacity planning; automatic unused-file cleanup and
photo removal are not implemented. Files from rolled-back uploads are removed
on a best-effort basis. No source directories were restructured.

## Pickup ordering and FOH/BOH dashboards

See [online-ordering.md](../ordering/online-ordering.md) for migration V11, staff account
configuration and the operational workflow. Ordering defaults to closed; set
`ONLINE_ORDERING_ENABLED=true` in the backend service environment and restart only
after configuring staff access. The current Nginx `/api/` proxy covers these routes.

## JWT identity deployment prerequisite

Before deploying migration V12 and the JWT dashboard, generate a key with
`openssl rand -base64 32` and set `JWT_SECRET_BASE64` in
`/etc/nakorn-thai/backend.env`. Keep this key stable across releases and set
`JWT_COOKIE_SECURE=true` for HTTPS. Production refuses to start without a key.
The workflow continues running backend integration and frontend API tests; no JWT
production secret belongs in the build or frontend bundle. Existing bootstrap
password hashes create missing database accounts only. See
[dashboard-identity.md](../identity/dashboard-identity.md) for login and account management.
