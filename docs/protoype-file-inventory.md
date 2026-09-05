Here’s the file inventory for the GitHub → VPS deployment and HTTPS setup. VPS paths are based on the commands and outputs you shared; certificate files apply once Certbot completes successfully.

| File / path | Purpose |
|---|---|
| `.gitignore` | Excludes dependencies, build output, local environment files, IDE files, and logs from Git. |
| `docs/git-init-repo.md` | Instructions for initializing Git and uploading the project to GitHub. |
| `.github/workflows/deploy.yml` | Builds the frontend on pushes to `main`, uploads it over SSH, and activates the release. |
| `frontend/package.json` | Defines frontend dependencies and the `npm run build` command. |
| `frontend/package-lock.json` | Locks dependency versions for reproducible installation with `npm ci`. |
| `frontend/dist/` | Generated production website uploaded by the workflow. |
| `/home/gnostex/Projects/current/merkado/notes/commit-message.format.txt` | Your commit-message convention: `<type>(<scope>): <description>`. |

**Removed workflow files**

| File | Reason |
|---|---|
| `.github/workflows/.backend-ci.yml` | Empty, unused workflow causing failures. |
| `.github/workflows/.frontend-ci.yml` | Empty, unused workflow causing failures. |

**SSH files**

| File / location | Purpose |
|---|---|
| `~/.ssh/nakorn-thai-deploy` — local computer | Deployment private key used to populate `VPS_SSH_KEY`. |
| `~/.ssh/nakorn-thai-deploy.pub` — local computer | Corresponding public key installed on the VPS. |
| `/tmp/vps-known-hosts` — local computer | Verified VPS host-key entry used to populate `VPS_KNOWN_HOSTS`. Earlier instructions used `/tmp/nakorn-vps-known-hosts`. |
| `/home/deploy/.ssh/authorized_keys` — VPS | Authorizes the deployment key to log in as `deploy`. |
| `~/.ssh/id_ed25519` — GitHub runner | Temporary deployment private key; removed by the workflow’s cleanup step. |
| `~/.ssh/known_hosts` — GitHub runner | Trusted VPS host key used for strict SSH verification. |

**VPS website and Nginx files**

| File / path | Purpose |
|---|---|
| `/etc/nginx/sites-available/nakorn-thai` | Website configuration: domain names, document root, React route fallback, and HTTPS settings after Certbot. |
| `/etc/nginx/sites-enabled/nakorn-thai` | Symlink enabling the website configuration. |
| `/etc/nginx/sites-available/nakorn-thai.before-ssl` | Backup proposed before Certbot modifies Nginx. |
| `/var/www/nakorn-thai/releases/<release-id>/` | Separate directory for each uploaded release. |
| `/var/www/nakorn-thai/current` | Symlink selecting the release Nginx serves. |
| `/var/www/nakorn-thai/current/index.html` | Built frontend entry page. |
| `/var/www/nakorn-thai/current/assets/` | Built JavaScript, CSS, and images. |
| `/var/www/nakorn-thai/current/favicon.ico` | Browser favicon. |
| `/var/www/nakorn-thai/current/nakorn-thai-favicon-source.png` | Favicon source image included in the deployment. |
| `/var/www/nakorn-thai/current/manifest.webmanifest` | Currently empty web-app manifest placeholder. |
| `/var/www/nakorn-thai/current/robots.txt` | Currently empty crawler-instructions placeholder. |
| `/var/log/nginx/error.log` | Nginx error log used during troubleshooting. |

**Let’s Encrypt files — created by successful Certbot setup**

The certificate directory name may have a numeric suffix if another certificate already exists.

| File / path | Purpose |
|---|---|
| `/etc/letsencrypt/live/nakorn-thai.tech-labs.dev/fullchain.pem` | Server certificate and intermediate certificate chain. |
| `/etc/letsencrypt/live/nakorn-thai.tech-labs.dev/privkey.pem` | Private TLS key; keep on the VPS and out of Git. |
| `/etc/letsencrypt/archive/nakorn-thai.tech-labs.dev/` | Versioned certificate/key files referenced by the `live` symlinks. |
| `/etc/letsencrypt/renewal/nakorn-thai.tech-labs.dev.conf` | Certificate renewal configuration. |
| `/etc/letsencrypt/options-ssl-nginx.conf` | TLS settings managed by Certbot’s Nginx integration. |
| `/etc/letsencrypt/ssl-dhparams.pem` | TLS parameters referenced by Certbot’s Nginx configuration. |
| `/var/log/letsencrypt/letsencrypt.log` | Certificate issuance and renewal logs. |

**GitHub configuration — secrets, not files**

| Secret | Purpose |
|---|---|
| `VPS_HOST` | VPS address: `173.212.214.180`. |
| `VPS_SSH_KEY` | Deployment private key. |
| `VPS_KNOWN_HOSTS` | Verified VPS public host-key entry. |
`certbot.timer` is the systemd timer proposed for automatic renewal with the APT installation. The backend and Docker placeholder files are not part of the current deployment.

I’ll check the source tree and identify which files make up the current website versus the remaining placeholders.

The current website uses **15 populated JavaScript/CSS files and 5 images under `frontend/src`**, plus the HTML entry point and public assets.

Paths below are relative to the project root.

| Source file | Purpose |
|---|---|
| `frontend/index.html` | HTML entry point; defines the title, metadata, favicon, and React mount element. |
| `frontend/src/main.jsx` | Starts React and imports the three stylesheets. |
| `frontend/src/app/App.jsx` | Renders the homepage. |
| `frontend/src/website/pages/HomePage.jsx` | Assembles the header, homepage sections, and footer. |
| `frontend/src/website/components/Header.jsx` | Logo, navigation, mobile menu toggle, and order link. |
| `frontend/src/website/components/Hero.jsx` | Main banner, introductory text, booking/menu links, and displayed rating/opening hours. |
| `frontend/src/website/components/SectionTitle.jsx` | Reusable section heading. |
| `frontend/src/website/components/SignatureDishes.jsx` | Dish cards and interactive image previews. |
| `frontend/src/website/components/Features.jsx` | Restaurant feature highlights. |
| `frontend/src/website/components/Reviews.jsx` | Displays locally defined customer testimonials. |
| `frontend/src/website/components/Location.jsx` | Booking prompt, contact details, and embedded Google Map. |
| `frontend/src/website/components/Footer.jsx` | Footer navigation, social links, and newsletter form layout. |
| `frontend/src/website/content/homeContent.js` | Navigation labels, dishes, features, testimonials, contact details, and footer links. |
| `frontend/src/styles/variables.css` | Shared CSS design variables. |
| `frontend/src/styles/globals.css` | Main website styling. |
| `frontend/src/styles/responsive.css` | Responsive layouts for different screen sizes. |

**Image assets**

All five are in `frontend/src/assets/images/`:

| File | Purpose |
|---|---|
| `nakorn-thai-logo.png` | Header and footer logo. |
| `signature-yellow-curry.jpg` | Yellow curry dish image. |
| `signature-crispy-pork-vegetables.jpg` | Crispy pork stir-fry image. |
| `signature-green-curry.jpg` | Green curry image. |
| `signature-crispy-pork-broccoli.jpg` | Crispy pork and broccoli image. |

**Supporting frontend files**

| File | Purpose |
|---|---|
| `frontend/vite.config.js` | Enables the React plugin and sets development port `5173`. |
| `frontend/package.json` | Dependencies and development/build commands. |
| `frontend/package-lock.json` | Exact dependency versions. |
| `frontend/public/favicon.ico` | Browser icon. |
| `frontend/public/nakorn-thai-favicon-source.png` | Source favicon image; also copied into the build. |
| `frontend/public/manifest.webmanifest` | Empty placeholder. |
| `frontend/public/robots.txt` | Empty placeholder. |

**Remaining source scaffolding**

- **99 files under `frontend/src` are empty**, including the additional pages, router/providers, shared utilities, and business-domain modules.
- **All 203 files under `backend/src` are empty**, including Java sources, configuration, migrations, and test placeholders.

The deployed application currently renders the restaurant homepage. Booking, ordering, newsletter submission, and review-carousel controls are not implemented as working services. Testimonials and opening information are hardcoded.

Source changes belong in these repository files; GitHub Actions builds them into `frontend/dist/` and uploads the generated output to the VPS.

Yes—the current frontend follows the existing scaffold:

- `app/` holds the application entry component.
- `website/pages/` assembles public pages.
- `website/components/` holds individual website sections.
- `styles/` separates shared variables, general styling, and responsive rules.
- `domains/` remains reserved for ordering, reservations, payments, and other business features.

We added `website/content/homeContent.js` for homepage content and `assets/images/` for images.

The scaffold guides **code organization**, not the visual design. So far, only the homepage is implemented; the router, business modules, and backend remain placeholders.

## Initial dynamic menu and dashboard

Existing source folders are retained. The homepage now reads backend menu data;
its former static dish records have been removed. Initial data remains in V8.

| File / path | Purpose |
|---|---|
| `frontend/src/app/AppRouter.jsx` | Selects the homepage or `#/staff/menu` dashboard. |
| `frontend/src/domains/menu/api/menuApi.js` | Public reads and authenticated/CSRF-protected staff requests. |
| `frontend/src/domains/menu/api/menuApi.test.js` | Node tests for frontend API contracts and failures. |
| `frontend/src/domains/menu/hooks/useMenu.js` | Loading, cancellation, error and retry state. |
| `frontend/src/domains/menu/model/menuModel.js` | Seeded dish photo presentation; no static menu content. |
| `frontend/src/domains/staff/pages/StaffMenuPage.jsx` | Sign-in, counts, search, create/edit/archive/restore and collections. |
| `frontend/src/website/components/SignatureDishes.jsx` | Renders API dishes and availability with loading/empty/error states. |
| `backend/src/main/java/au/com/nakornthai/menu/{createitem,updateitem,deleteitem,getitem}/` | Initial staff controllers, handlers and DTOs within existing slices. |
| `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuAdminService.java` | Transactional JPA item writes, collection membership, review invalidation and version checks. |
| `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuWriteExceptionHandler.java` | Safe conflict responses without database internals. |
| `backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java` | Configurable bcrypt admin account, role checks and CSRF. |
| `backend/src/test/java/au/com/nakornthai/menu/createitem/` | Mockito staff API tests and PostgreSQL CRUD persistence tests. |
| `backend/src/main/resources/db/migration/V8__seed_signature_dishes.sql` | Existing four specials and memberships; unchanged, applied once by Flyway. |
| `infrastructure/nginx/nakorn-thai.conf` | API proxy snippet for the existing HTTPS server. |
| `docs/menu-dashboard.md` | Setup, API contracts, seed behavior, scope and verification. |

## Pickup ordering and operational staff dashboards

- `backend/src/main/resources/db/migration/V11__create_pickup_ordering.sql`: order,
  line-item snapshot and status audit tables.
- `backend/src/main/java/au/com/nakornthai/ordering/`: create, private read, list and
  transition controllers/handlers plus JPA entities in existing slices.
- `frontend/src/domains/ordering/`: cart, checkout, private confirmation and API code.
- `frontend/src/domains/staff/pages/StaffDashboardPage.jsx`: staff navigation.
- `frontend/src/domains/staff/pages/StaffOrdersPage.jsx`: FOH operational queue and
  shared queue UI, with role-specific actions.
- `frontend/src/domains/staff/pages/KitchenDashboardPage.jsx`: BOH kitchen queue.
- `docs/online-ordering.md`: pickup scope, roles, configuration and usage.

The existing source directories are preserved. Tests are in the existing ordering
backend test folder and frontend ordering API folder.

## Dashboard identity implementation

The earlier scaffold counts are historical. The existing identity folders now
implement JWT authentication and persistent staff management:

| Files | Purpose |
|---|---|
| `backend/src/main/java/au/com/nakornthai/identity/` | Login, refresh, logout, current user, staff CRUD, account/session persistence and bootstrap |
| `backend/src/main/java/au/com/nakornthai/shared/security/` | JWT signing, verification and role-based endpoint access |
| `backend/src/main/resources/db/migration/V12__create_staff_identity.sql` | Staff users and refresh-session hashes |
| `frontend/src/domains/identity/` | Shared login, auth context, route protection and staff accounts dashboard |
| `backend/src/test/java/au/com/nakornthai/IdentityIntegrationTest.java` | Database-backed identity and revocation tests |
| `frontend/src/domains/identity/api/identityApi.test.js` | Token retry, concurrent refresh and logout tests |
| `docs/dashboard-identity.md` | Local and production configuration, permissions and API lifecycle |
