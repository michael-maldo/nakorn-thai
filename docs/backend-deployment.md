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

The first workflow run uploads the JAR and starts the service. Install the existing
`infrastructure/nginx/nakorn-thai.conf` site configuration using your normal Nginx
setup, then run `sudo nginx -t` before `sudo systemctl reload nginx`. Its `/api/`
proxy must reach `127.0.0.1:8080`. The workflow does not install Nginx configuration.

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
