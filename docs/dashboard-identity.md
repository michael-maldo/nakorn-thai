# Dashboard identity

All staff dashboards share JWT authentication through `/#/staff`. ADMIN users can
open `/#/staff/users` to create individual accounts, change roles, reset passwords
and disable access. Existing frontend/backend domain folders remain in use.

## Setup

1. Apply Flyway V12 by starting the backend. It adds `staff_user` and `staff_session`;
   existing migrations and menu/order data are preserved.
2. In the ignored `backend/.env.dev`, keep `JWT_COOKIE_SECURE=false` for local HTTP.
   Optionally set `JWT_SECRET_BASE64` using the output of `openssl rand -base64 32`.
   Without a key, dev uses an ephemeral signing key; restart invalidates access tokens.
3. Set `MENU_ADMIN_USERNAME` and `MENU_ADMIN_PASSWORD_HASH` for the initial admin.
   Generate a bcrypt hash with `htpasswd -nBC 12 admin` and copy only the hash,
   enclosed in single quotes. Load the environment and start the backend as described
   in [backend setup](../backend/README.md). Start the frontend with `npm run dev`.
4. Open `http://localhost:5173/#/staff`, sign in with the original password, then
   open staff accounts. New/reset passwords require 12–72 characters and at most
   72 UTF-8 bytes. Usernames use lowercase letters, digits, dots, underscores and
   hyphens (3–50 characters).

For production, set a persistent **`JWT_SECRET_BASE64`** (at least 32 random bytes
encoded as Base64) in `/etc/nakorn-thai/backend.env` before deployment and set
`JWT_COOKIE_SECURE=true`. Production startup requires the key. Use HTTPS and
restart the backend after environment changes. Do not put secrets in Git or Vite
variables. See [deployment](backend-deployment.md).

`MENU_ADMIN_*`, `FOH_*` and `BOH_*` bootstrap missing accounts only. Changing or
clearing those environment values does not reset or disable existing users.
Manage existing accounts in the dashboard. The last enabled ADMIN cannot be
removed or demoted. Accounts are disabled rather than deleted to retain audit identity.

## Permissions

| Role | Dashboards |
|---|---|
| ADMIN | Staff accounts, menu, FOH and kitchen |
| FOH | Front of house orders |
| BOH | Kitchen orders |

## Authentication lifecycle

Access JWTs expire after 15 minutes and stay in browser memory. The refresh token
uses an HttpOnly, SameSite=Strict cookie scoped to `/api/identity`; production makes
it Secure. Sessions last 12 hours, with refresh-token rotation and only token hashes
stored in PostgreSQL. Reusing an old refresh token revokes the session. Refreshes
are serialized within the app and across tabs where Web Locks is supported.

Every authenticated request checks the session and current database role. Logout,
disabling a user, changing a role or resetting a password revokes access immediately
for subsequent requests. Staff actions retain the authenticated username in the
existing order audit trail. HTTP Basic is no longer supported. CSRF remains required
for writes, including login, refresh and logout. The frontend fetches tokens and
refreshes access automatically. Login attempts are limited per username in each
backend process (10 per minute); this is not a distributed rate limiter.

| Endpoint | Purpose |
|---|---|
| `GET /api/identity/csrf` | Obtain CSRF header/token |
| `POST /api/identity/login` | Username/password login |
| `POST /api/identity/refresh` | Rotate refresh cookie and issue access JWT |
| `POST /api/identity/logout` | Revoke session and clear cookie |
| `GET /api/identity/me` | Current staff account |
| `GET /api/identity/users` | ADMIN account list |
| `POST /api/identity/users` | ADMIN create account |
| `PUT /api/identity/users/{id}` | ADMIN update role, enabled state or password |

There is no public registration, email password recovery or MFA in this version.
Use an ADMIN account for password resets. If cookies are blocked or Secure cookies
are configured on local HTTP, refresh fails: check environment settings and sign in
again. Disabling or changing your own account can require immediate sign-in again.

## Reservation permissions

ADMIN and FOH can manage guest bookings at `/#/staff/reservations`. BOH has no
reservation access. Public booking requests do not require a staff login, but do
require CSRF protection. See [reservations.md](reservations.md) for the workflow.
