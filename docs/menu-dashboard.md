# Initial menu dashboard

The homepage now reads `GET /api/menu/collections/signature-dishes/items`.
Names, descriptions, visibility, availability and order come from PostgreSQL.
Loading, empty and unavailable states are explicit; there is no static menu fallback.
The existing four photographs remain bundled frontend assets associated with the
seeded item IDs. An API image takes precedence. New dishes without media show a
photo placeholder. Variation creation, food declaration editing,
and creating categories/collections are not part of this first dashboard.

## Seed data

The existing Flyway migration `V8__seed_signature_dishes.sql` already creates:

- Yellow Curry and Green Curry in Curries.
- Crispy Pork Stir-Fry and Crispy Pork & Broccoli in Stir-fries.
- The published Signature Dishes collection with all four memberships.

Starting the backend against a newly provisioned database applies these migrations
and inserts the initial data. Existing migrated databases retain all edits: do not
rerun the seed SQL manually or edit an applied migration. No new tables or migration
are needed for this change. No prices or dietary/allergen claims are invented.

## Local startup

1. Configure PostgreSQL and `backend/.env.dev` as described in `backend/README.md`.
2. Generate a bcrypt password hash interactively (Ubuntu package `apache2-utils`
   provides `htpasswd`):

   ```bash
   htpasswd -nBC 12 admin
   ```

   Copy only the hash after `admin:`. Add these lines to your ignored local env
   file, replacing the placeholder. Single quotes preserve the dollar signs:

   ```dotenv
   MENU_ADMIN_USERNAME=admin
   MENU_ADMIN_PASSWORD_HASH='$2y$12$REPLACE_WITH_YOUR_COMPLETE_BCRYPT_HASH'
   ```

   Do not put these variables in frontend env files. An empty hash skips initial account creation; a malformed nonempty hash prevents backend startup. There is no default
   password. Dev and prod should have separate credentials.

3. Start the backend from `backend/` with Java 21:

   ```bash
   set -a
   source .env.dev
   set +a
   mvn spring-boot:run
   ```

4. In another terminal, from `frontend/`:

   ```bash
   npm ci --include=optional
   npm run dev
   ```

5. Open `http://localhost:5173/` for the website and
   `http://localhost:5173/#/staff/menu` for the dashboard. Vite forwards `/api/`
   requests to `127.0.0.1:8080`. Update the proxy if the local backend port changes.

Sign in using the username and original password, not the bcrypt hash. All staff
dashboards now share JWT authentication, with access tokens in memory and a rotating
HttpOnly refresh cookie. Browser writes also require CSRF tokens. Manage individual
users at `/#/staff/users`. Production requires a persistent signing key and HTTPS;
see [dashboard identity](dashboard-identity.md) before deploying.

## Editing

Use **Add dish**, enter a unique lowercase slug, choose a category and collections,
and save. A dish appears on the homepage only when it is PUBLISHED, belongs to the
active Signature Dishes collection, and has an active category. Unavailable dishes
remain visible with an unavailable label. New dishes default to DRAFT.

A dish can belong to several collections. Display order is nonnegative and applies
to its selected collection memberships when saved. Existing collection-specific
orders remain until that dish is edited. Name or description changes clear verified
dietary badges on the item and its variants and mark food profiles NEEDS_REVIEW;
existing allergen warnings are retained. The editor does not certify food suitability.

**Archive** is the delete operation. It preserves related data and hides the dish
from public collections. Select **Show archived**, edit and change publication to
restore it. Updates and archive requests carry the last-read version; stale changes
return 409. Reload the menu before retrying a conflict. If a write succeeds but the
follow-up reload fails, the UI blocks further edits until the list reloads.

## API contracts

All staff endpoints require ROLE_ADMIN. Request bodies are validated DTOs, never
entities. Responses and CSRF tokens use `Cache-Control: no-store`.

| Method | Path | Result |
|---|---|---|
| GET | `/api/staff/menu/csrf` | CSRF header name/token and session cookie |
| GET | `/api/staff/menu/items` | All items, category options and collection options |
| POST | `/api/staff/menu/items` | 201 with new item ID |
| PUT | `/api/staff/menu/items/{id}` | 204 after update |
| DELETE | `/api/staff/menu/items/{id}?version=0` | 204 after archive |

POST/PUT fields: `name`, `slug`, `description`, `categoryId`, `status`, `available`,
`displayOrder`, `collectionIds`, `version`. Version must be null/omitted for POST
and supplied for PUT. DELETE also requires the version. Valid statuses are DRAFT,
PUBLISHED and ARCHIVED. UUIDs must refer to existing categories/collections.
Invalid data returns 400; missing items 404; duplicate slugs/stale edits 409.
Anonymous staff reads return 401; insufficient roles or missing CSRF return 403.

The dashboard's initial list is unpaginated, intended for a single restaurant menu.

## VPS integration

The current GitHub workflow uploads only `frontend/dist/`. It does not start or
upgrade the Java backend, configure PostgreSQL, or change Nginx. Before publishing
this frontend change, deploy the backend JAR using the existing backend setup,
configure its production database and the admin hash in its private service
environment, and ensure it listens on `127.0.0.1:8080`.

Add the location block in `infrastructure/nginx/nakorn-thai.conf` **inside the
existing HTTPS server block** in `/etc/nginx/sites-available/nakorn-thai`. The file
is a snippet, not a replacement for your Certbot-managed configuration. Keep the
HTTP-to-HTTPS redirect and certificate settings. It preserves `/api/` in upstream
requests and disables proxy caching. Do not expose the private management port.

```bash
sudo nginx -t
sudo systemctl reload nginx
curl -fsS http://127.0.0.1:8080/api/menu/collections/signature-dishes/items
curl -fsS https://nakorn-thai.tech-labs.dev/api/menu/collections/signature-dishes/items
```

Both should return JSON with the four seeded dishes on an unchanged database.
Then visit `https://nakorn-thai.tech-labs.dev/#/staff/menu`. With no backend/proxy,
the new homepage shows its menu-service error state instead of static dishes.

## Verification

From `frontend/`: `npm test` and `npm run build`.
From `backend/`: `mvn verify` (database tests require `DB_TEST_URL`,
`DB_TEST_USERNAME` and optional `DB_TEST_PASSWORD` for a disposable PostgreSQL DB).

The tests cover public API behavior, authenticated/authorized staff access, CSRF,
validation, transactional create/update/archive/restore, stale and collection-only
edits, duplicate slugs, invalid membership rollback, and frontend request contracts.
Existing schema/entity tests remain in place. No source folders were restructured.

Photo upload and focal positioning are now available for saved dishes. See
[backend deployment](backend-deployment.md#menu-photo-uploads) for storage and proxy setup.

## Public restaurant menu

The homepage header's **Menu** link and **View full menu** link open `/#/menu`.
The existing `domains/menu/pages/MenuPage.jsx` renders the published
`chefs-special-recommendations` collection through the public collection API.
It displays descriptions, AUD prices, variation choices, availability and photos,
with search plus loading/error/empty states. Header navigation marks Menu active.
The four signature dishes remain a separate homepage section.

Deploy backend migration V10 before using this page so the Chef's collection
exists. No new API endpoint or source directory is required. Staff changes to
collection membership, publication, availability and photographs appear on the
public page when it is loaded again.


## Editing prices

Edit a dish and use **Prices (AUD)**. Each active variation has its own amount
field (for example all four lamb curry choices). Amounts are entered in dollars
with at most two decimal places and stored as integer cents. An unpriced dish
can receive a **Standard** price using **Add price**, including during creation.
Save prices with **Save dish**. Existing variation identities and food profiles
are preserved on price-only edits. The initial editor does not add/remove
multiple variation choices or edit inactive variations.

The staff response includes `prices: [{id, name, amount}]`. Create/update bodies
accept optional `prices: [{id, amount}]`, with decimal AUD amounts (JSON numbers
or decimal strings). Omitted/empty lists preserve prices. A null ID creates one
Standard variation only when the item has no variations. Existing IDs must belong
to an active variation of that item; duplicates, negative amounts, more than two
decimal places, and amounts above 9999999.99 are rejected. Item version checks
also protect price changes. No schema migration is needed for this feature.
