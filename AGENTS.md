# Project Overview

Nakorn Thai is a restaurant website and operations application with menu browsing,
pickup ordering, payments, table requests, function enquiries and staff workflows.
The frontend uses React, JavaScript/JSX and Vite; the backend is one Java 21,
Spring Boot 4.1.1 application built with Maven, using Spring MVC, Security,
Validation, Data JPA, Lombok and Flyway with PostgreSQL. Some files and domains
remain empty scaffolds: a filename alone is not evidence of implemented behavior.

# Repository Structure

| Path | Responsibility |
| --- | --- |
| `frontend/` | React application, npm manifest/lockfile and Vite configuration |
| `backend/` | Maven application, Java sources/tests, configuration and migrations |
| `docs/` | Domain guides plus architecture, deployment and operations documentation |
| `infrastructure/` | Nginx, systemd, backend activation and monitoring configuration; also placeholder deployment files |
| `scripts/` | Observability smoke check; `build.sh`, `test.sh` and `dev.sh` are empty |
| `.github/workflows/deploy.yml` | Backend/frontend validation and production deployment on main |
| `TODO/` | Planning notes, not proof of implemented functionality |

Root `docker-compose.yml`, `backend/Dockerfile` and
`infrastructure/docker/docker-compose.prod.yml` are empty placeholders.

# Architecture

The implemented application is a domain-oriented frontend paired with a modular
monolith backend organized into domain/use-case vertical slices. It does not use
global controller/service/repository directories or a uniform mandatory layer chain.

Backend domains under `backend/src/main/java/au/com/nakornthai/` are `identity`,
`menu`, `ordering`, `payment`, `reservation`, `notification`, `customer`,
`restaurant` and `staff`, alongside technical `shared` packages.

- `menu` owns collections, dishes, variations, images and menu administration.
- `identity` owns staff users and sessions; roles are `ADMIN`, `FOH` and `BOH`.
- `ordering` owns pickup checkout, tracking and staff order transitions.
- `payment` owns PayPal operations and PayID reconciliation.
- `reservation` owns both table requests and function/venue enquiries.
- `notification` contains implemented order verification via Twilio Verify;
  confirmation delivery files also exist as scaffolds.
- `restaurant` owns implemented opening-hours, closed-date and availability logic.
- Backend `customer` and `staff` currently contain only empty scaffolds. Frontend
  staff screens use APIs owned by the active business domains rather than a backend
  `staff` implementation.

Menu reads demonstrate controller → handler → domain repository interface → JPA
adapter → Spring Data. Ordering intentionally uses `EntityManager` for creation and
status-transition writes, while order lookup and staff queues use Spring Data.
Reservation creation combines Spring Data with an `EntityManager` advisory lock.
Some staff writes are transactional controller methods. Preserve these local
layers just to make all slices identical.

# Frontend Structure

- `src/main.jsx` mounts `app/App.jsx` with React StrictMode and imports the styles.
- `app/App.jsx` composes `AuthProvider`, `CartProvider`, `AppRouter` and `CartDock`.
  Routing is implemented directly in `app/AppRouter.jsx` using URL hashes and
  `hashchange`, with routes such as `#/menu` and `#/staff/foh`. `Providers.jsx`
  and `routes.js` are empty; there is no React Router dependency.
- `src/domains/<domain>/` groups `pages/`, `components/`, `hooks/`, `api/` and
  `model/`. Not every reserved file is implemented. Pages and components use
  PascalCase `.jsx`; API/model modules use camelCase `.js`; hooks use `use*` names.
- `website/pages/`, `website/components/` and `website/content/` hold public-site
  presentation and content, including the shared public header/footer.
- `shared/` reserves reusable components, API, hooks, constants and utilities,
  but its current files are empty, including `shared/api/httpClient.js`.
  Do not assume an existing shared HTTP abstraction or UI library.
- Active domain API modules use browser `fetch` and domain request wrappers.
  `identity/api/identityApi.js` provides `fetchWithIdentity`, bearer-token refresh
  and a retry after 401 for bearer requests. Write wrappers acquire CSRF tokens
  and send same-origin cookies. Follow the owning wrapper's behavior.
- Hooks are optional in existing flows: menu uses `hooks/useMenu.js`, while
  reservation and checkout pages call API modules directly. Auth/cart hooks are
  exported from their `model/*Context.jsx` implementations.
- State uses React hooks/context. Identity access state is held in memory;
  cart and pending checkout data use `sessionStorage`.
- Styling uses ordinary CSS classes, custom properties in `styles/variables.css`,
  global rules in `styles/globals.css` and media queries in `styles/responsive.css`.
  Some dynamic image presentation uses inline styles. Follow nearby class names.
- Vite serves development on port 5173 and proxies `/api` and `/media` to
  `http://127.0.0.1:8080` (`frontend/vite.config.js`).

# Backend Structure

- Base package: `au.com.nakornthai`; entry point: `NakornThaiApplication`.
  Lowercase use-case packages such as `menu/listmenu`, `ordering/createorder`
  and `reservation/createreservation` keep related controllers and handlers together.
- Naming includes `*Controller`, `*Handler`, `*Command`, `*Query`, `*Request`
  and `*Response`. Handlers commonly use `@Service` and `@Transactional`;
  controllers map HTTP input and responses. Some command/query files are empty.
- `domain/` holds implemented domain types/interfaces where used, and placeholders
  elsewhere. `infrastructure/` holds `*JpaEntity`, `SpringData*Repository`,
  `Jpa*Repository`, mappers and technical services such as `MenuAdminService`
  and `MenuImageService`. Do not assume every slice uses a domain repository.
- DTOs commonly use Java records and Jakarta validation constraints. Existing
  endpoints also return maps or JPA entities; follow the relevant contract.
  Persistence entities use Jakarta JPA annotations and commonly Lombok; menu has
  shared audit/UUID entity base classes. JSON uses camelCase; SQL uses snake_case.
- `shared/security/` implements JWT authentication, live staff-session checks,
  password encoding and endpoint permissions. Security denies unlisted routes;
  frontend `ProtectedRoute` is only a UI guard. Keep CSRF handling intact.
  Access JWTs last 15 minutes and are accepted only while their database-backed
  `staff_session` and `staff_user` remain valid. Staff sessions/refresh credentials
  last 12 hours and rotate on refresh. Refresh cookies are HttpOnly, SameSite Strict,
  scoped to `/api/identity`, and secure in production. Browser writes obtain a CSRF
  token from the owning API before submission.
- `shared/observability/CorrelationIdFilter.java` implements request logging and
  correlation. Domain-specific exception handlers exist in infrastructure packages;
  several `shared/error` and `shared/config` files remain empty.
- `src/main/resources/application.yml` supplies datasource, JPA/Flyway, logging,
  management and tracing settings. `application-dev.yml` and `application-prod.yml`
  specialize profiles; `backend/.env.dev.example` and `.env.prod.example` document
  environment inputs. Feature settings also appear in Java `@Value` declarations.
  Defaults use API port 8080 and a separate management port 8081. Ordering, payment
  providers and verification channels are configuration-gated; configuration files
  show available settings, not what is enabled in a running environment.

# Database

- PostgreSQL tables use unqualified names in a common schema (integration tests
  inspect `public`), not one PostgreSQL schema per Java domain. CI uses PostgreSQL 16.
- Flyway migrations live in `backend/src/main/resources/db/migration/` and follow
  `V<number>__snake_case_description.sql`, currently V1–V22. Some early versions
  reserve domains without creating tables; use the actual SQL, not filenames.
- **Never modify an already-applied migration. Schema changes require a new
  migration with the next unused version.** V1 explicitly documents this rule.
  Flyway owns DDL; Hibernate uses `ddl-auto: validate`, with Flyway clean disabled.
- Menu tables cover categories, items, variations, collections/schedules, images,
  allergens, dietary tags and configurable option groups. Ordering uses
  `restaurant_order`, item/selected-option snapshots and order events. Other active
  tables include staff identity/session, reservations/functions, payments/tracking,
  and restaurant settings/opening-hours/closed-date tables.
- UUID keys, foreign keys, check constraints, explicit status values and version
  columns are established conventions. Menu/order money uses integer minor units
  and AUD. Preserve server-side price checks and order item snapshots.
- Writes use version checks and transaction locks where appropriate. Guest request
  UUIDs support retry/idempotency; tracking credentials are distinct from staff JWTs.
- Audit instants use time-zone-aware timestamps; reservation requested times are
  local timestamps interpreted in the configured restaurant timezone (initially
  Australia/Melbourne). Hibernate's JDBC time zone is UTC. Do not interchange
  these meanings.

# Architectural Boundaries

Determine the owning domain before adding code. Preserve existing folders/packages
and follow neighboring implementations. Keep feature-specific code within its
domain and use shared/common areas only for genuinely shared concerns. Reuse
existing cross-domain dependencies where needed: checkout reads menu data and
staff pages compose business-domain APIs. Avoid parallel architectures, unnecessary
new top-level directories and unrelated refactoring.

# Cross-Layer Changes

Start at the owning feature and follow only dependencies required for the task,
rather than scanning the entire repository. Identify the affected page/component,
hook or API wrapper, HTTP contract, security rule, controller/handler, persistence
and migration before editing. Keep payloads, validation, permissions, versioning
and error handling consistent across those files.

End-to-end changes must preserve ownership boundaries at every layer: frontend
code stays in the owning frontend domain, backend code stays in the owning backend
slice, and schema changes use a new Flyway migration.

Representative implemented flows:

- Menu: `domains/menu/pages/MenuPage.jsx` → `hooks/useMenu.js` → `api/menuApi.js`
  → `GET /api/menu/collections/{slug}/items` → `menu/listmenu/ListMenuController`
  → `ListMenuHandler` → `MenuItemRepository` → `JpaMenuItemRepository`
  → Spring Data collection/item repositories → PostgreSQL menu tables.
- Reservation: `domains/reservation/pages/ReservationPage.jsx` →
  `api/reservationApi.js` → `POST /api/reservations` → `CreateReservationController`
  → `CreateReservationHandler` → `SpringDataReservationRepository` plus an
  `EntityManager` advisory lock → PostgreSQL `reservation`.
- Checkout: `domains/ordering/pages/CheckoutPage.jsx` → `checkoutApi.js` for menu
  price refresh and `orderApi.js` for submission → `POST /api/orders` →
  `CreateOrderController` → `CreateOrderHandler` → `EntityManager` for menu checks
  and order/item/event persistence → `OrderMapper` response.

Use `docs/tracing/application-trace-map.md` for current file-by-file request traces. See
`docs/architecture/vertical-slices.md`, `domain-map.md` and `api.md` for broader
design context, but confirm documentation against current source before changing
behavior.

# Existing Patterns Win

Existing repository structure and established patterns take precedence over
generic or textbook best practices. Do not reorganize the application merely
because another architecture would also be valid. If an architectural change
appears necessary, identify and explain it rather than silently introducing it
as part of an unrelated feature.

# Development and Build

- Frontend: from `frontend/`, run `npm ci --include=optional`, then `npm run dev`.
  Vite listens on 5173 and expects the backend on `127.0.0.1:8080`.
- Backend: provision PostgreSQL first, copy/configure `.env.dev` from the example,
  explicitly load that trusted file into the environment, then run
  `mvn spring-boot:run` from `backend/`. Spring Boot does not load `.env` files.
- Build artifacts with `npm run build` in `frontend/` and `mvn verify` in `backend/`.
  The empty root helper scripts and Docker placeholders are not build or development
  paths; some infrastructure deployment/backup scripts are placeholders too.
- `.github/workflows/deploy.yml` validates against PostgreSQL 16 with Java 21 and
  Node 24, then deploys the backend JAR and frontend assets on pushes to `main`.
  Infrastructure configuration describes intended deployment; it does not prove
  the current state of a live server.

# Testing and Validation

Run commands from the indicated directory. CI uses Java 21 and Node 24.

| Directory | Command | Purpose |
| --- | --- | --- |
| `frontend/` | `npm ci --include=optional` | Install locked dependencies as CI does |
| `frontend/` | `npm test` | Node built-in test runner over explicitly listed domain tests |
| `frontend/` | `npm run build` | Vite production build |
| `backend/` | `mvn test` | Maven/Surefire tests |
| `backend/` | `mvn verify` | Tests and executable Spring Boot JAR build |
| `backend/` | `mvn --batch-mode --no-transfer-progress clean verify` | CI backend validation/build |
| Repository root | `python3 scripts/check-observability.py` | Optional packaged-backend observability smoke check after Maven verify |

Frontend tests are colocated domain API/model `.test.js` files using `node:test`,
assertions and mocked fetch where appropriate; the npm script enumerates files explicitly.
`frontend/tests/{unit,integration,e2e}` contain placeholders, not configured suites.
Backend tests live under `src/test/java/au/com/nakornthai/`, generally mirroring
domain/use-case packages, using JUnit, Mockito and Spring/MockMvc integration tests.
Some named test files remain empty.

Database integration tests require `DB_TEST_URL`, `DB_TEST_USERNAME` and
`DB_TEST_PASSWORD` for a dedicated disposable PostgreSQL database; tests gated by
`DB_TEST_URL` are skipped when it is absent. Never use production for tests.
The observability script also requires that test database and a built JAR.
No lint/static-analysis command is configured in the inspected npm/Maven manifests.
Do not treat the empty shell helpers as validation commands or report skipped
integration tests as verified behavior.

# Codex Working Rules

1. Read this AGENTS.md before making changes.
2. Identify the owning domain/feature.
3. Inspect the relevant local structure first, checking whether files are active.
4. Follow direct dependencies only as necessary.
5. Identify files that need modification before editing.
6. Preserve architectural boundaries.
7. Reuse existing components, services and patterns.
8. Avoid unrelated refactoring.
9. Do not introduce dependencies without justification.
10. Make the smallest coherent change.
11. Run relevant validation and report limitations or skips.
12. Report files changed and why.
13. Stop when the requested task is complete.

# Repository-Specific Findings

- Root `README.md` describes an earlier structure-only scaffold and contradicts
  populated manifests/source. Prefer current code and configuration, supported by
  the feature guides, over its implementation-status claims.
- Function enquiries belong to `reservation` even though their public page is in
  `website` and their management page is in frontend `staff`.
- Guest orders/reservations store contact details directly; the empty customer
  domain is not a prerequisite. A table request is not a confirmed reservation.
- Payment webhook/refund and alternative-provider filenames include scaffolds;
  their presence does not establish working integrations. Inspect implementations
  before extending an integration or claiming it is supported.
- `/api/identity/me` is implemented but the frontend currently restores identity
  through `/api/identity/refresh`; do not assume the `me` endpoint is in its active path.
- `MenuConfigurationController` implements schedules, collection categories and
  memberships, option groups/options and assignments, but the current React menu
  dashboard exposes only collection availability from that configuration surface.
- Empty command/query/handler files are often bypassed by working slices. Some
  implemented staff writes intentionally keep `@Transactional` logic directly in
  controllers; trace the actual caller before adding or moving layers.
- Confirmation delivery, refund/webhook, alternative payment provider, customer,
  and backend staff-dashboard/queue files include empty scaffolds. Twilio Verify
  order-tracking recovery is implemented; empty SMS/email sender files are not.
- Repository configuration establishes intended deployment behavior, not current
  live infrastructure, enabled feature flags or applied database versions.
