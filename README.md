# Nakorn Thai

Repository scaffold for a full-stack restaurant platform covering the public
website, menu browsing, customer profiles, reservations, ordering, payments,
notifications, and staff operations.

> [!IMPORTANT]
> This repository is currently a **structure-only scaffold**. At the time of
> this review, the application source files, build manifests, configuration,
> database migrations, tests, scripts, CI workflows, and supporting documents
> are empty placeholders. The names and directory layout describe the intended
> design, but no runnable behavior or configured dependency versions can yet be
> verified.

## Scaffold overview

```text
nakorn-thai/
├── backend/                     # Intended Java backend
│   ├── pom.xml                  # Maven manifest placeholder
│   ├── Dockerfile               # Backend image placeholder
│   └── src/
│       ├── main/
│       │   ├── java/au/com/nakornthai/
│       │   │   ├── customer/
│       │   │   ├── identity/
│       │   │   ├── menu/
│       │   │   ├── notification/
│       │   │   ├── ordering/
│       │   │   ├── payment/
│       │   │   ├── reservation/
│       │   │   ├── restaurant/
│       │   │   ├── shared/
│       │   │   └── staff/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       ├── application-prod.yml
│       │       └── db/migration/
│       └── test/java/au/com/nakornthai/
├── frontend/                    # Intended React/Vite frontend
│   ├── package.json             # npm manifest placeholder
│   ├── vite.config.js           # Vite configuration placeholder
│   ├── public/
│   ├── src/
│   │   ├── app/                 # Application shell, providers, routing
│   │   ├── domains/             # Feature-oriented UI modules
│   │   ├── shared/              # Reusable API, UI, hooks, and utilities
│   │   ├── styles/              # Global, responsive, and token styles
│   │   └── website/             # Public marketing pages/components
│   └── tests/                   # Unit, integration, and E2E placeholders
├── docs/                        # Feature guides, architecture and operations
├── infrastructure/              # Deployment and observability placeholders
├── scripts/                     # Developer command placeholders
├── .github/workflows/           # CI/deployment workflow placeholders
└── docker-compose.yml           # Local orchestration placeholder
```

## Intended architecture

The naming and package layout suggest a modular monolith backend paired with a
domain-oriented single-page application.

```text
Browser
   │
   ▼
React/Vite frontend
   │ HTTP API
   ▼
Java backend
   ├── use-case controllers and handlers
   ├── domain models and repository interfaces
   ├── persistence adapters
   └── external service adapters
          ├── Stripe / Wix payments
          ├── SMTP / Wix email
          └── Twilio / Wix SMS
   │
   ▼
Relational database managed by versioned migrations
```

This diagram records the apparent intent of the scaffold. Protocols,
frameworks, database vendor, and integrations are not configured yet.

## Backend organization

The backend scaffold contains 181 main-source placeholders under the base
package `au.com.nakornthai`. Features are grouped first by business domain and
then, where appropriate, by use case.

| Domain | Apparent responsibilities |
| --- | --- |
| `identity` | Login, logout, token refresh, current user, roles, and users |
| `customer` | Create, retrieve, and update customer profiles |
| `menu` | List, retrieve, create, update, and delete menu items |
| `reservation` | Check availability, create/cancel/list reservations, reservation rules |
| `restaurant` | Restaurant details, opening hours, and availability |
| `ordering` | Create, retrieve, list, and change the status of orders |
| `payment` | Create/refund payments and process webhooks |
| `notification` | Order/reservation confirmations by email or SMS |
| `staff` | Dashboard, today's orders/reservations, kitchen queue, and alerts |
| `shared` | Configuration, errors, observability, and security concerns |

### Backend layering conventions

File names indicate the following intended conventions:

- `domain/` contains entities, value objects, statuses, policies, and repository
  or provider interfaces.
- Use-case packages such as `createorder/` contain command/query objects,
  handlers, request/response models, and controllers.
- `infrastructure/` contains persistence implementations, JPA entities,
  mappers, and third-party adapters.
- `shared/` contains cross-cutting configuration, security, error handling, and
  observability.

The persistence scaffold uses names such as `Jpa*Repository`,
`SpringData*Repository`, and `*JpaEntity`, suggesting an intended JPA/Spring
Data implementation. Framework dependencies cannot be confirmed until
`backend/pom.xml` is populated.

### Database migrations

Seven ordered migration placeholders exist:

1. `V1__create_identity_schema.sql`
2. `V2__create_menu_schema.sql`
3. `V3__create_reservation_schema.sql`
4. `V4__create_order_schema.sql`
5. `V5__create_payment_schema.sql`
6. `V6__create_customer_schema.sql`
7. `V7__create_restaurant_schema.sql`

Their naming follows Flyway's versioned migration convention, but the files do
not yet contain SQL and Flyway is not yet declared in a build manifest.

### Backend tests

Twelve test placeholders describe planned unit and integration coverage for:

- menu listing and menu item creation;
- availability checking and reservation creation;
- order creation and status changes;
- payment creation and payment webhook processing;
- the staff dashboard and kitchen queue.

## Frontend organization

The frontend scaffold contains 109 source placeholders and follows a
feature/domain-based layout.

| Area | Structure and intent |
| --- | --- |
| `app/` | `App`, router, providers, and route definitions |
| `domains/identity` | Authentication context, login, protected routes, and role guards |
| `domains/customer` | Customer form, summary, API, hooks, model, and page |
| `domains/menu` | Customer menu and admin UI, filters, categories, item cards/options |
| `domains/reservation` | Availability and reservation forms, confirmation, API and hooks |
| `domains/ordering` | Cart, checkout, order status, APIs, hooks, models, and pages |
| `domains/payment` | Payment form/status, API, hook, model, and page |
| `domains/restaurant` | Contact/details/opening-hours UI and supporting layers |
| `domains/staff` | Staff dashboards, operational queues, alerts, and management pages |
| `website/` | Home, about, gallery, functions, header, footer, and hero sections |
| `shared/` | HTTP client, reusable components, constants, hooks, validation and formatting |

Each domain generally reserves folders for `api`, `components`, `hooks`,
`model`, and `pages`, keeping feature code together while placing broadly
reusable code in `shared`.

## Operations and documentation scaffolds

The repository reserves infrastructure paths for:

- local orchestration in `docker-compose.yml`;
- a backend container in `backend/Dockerfile`;
- production composition in `infrastructure/docker/`;
- an Nginx site configuration in `infrastructure/nginx/`;
- Prometheus and Grafana monitoring in `infrastructure/monitoring/`;
- database backup and deployment scripts in `infrastructure/scripts/`;
- backend, frontend, and deployment workflows in `.github/workflows/`.

The [documentation index](docs/README.md) groups guides by menu, identity, ordering,
reservations, functions and staff, with supporting architecture, development,
deployment and operations folders. The index identifies the remaining scaffold
placeholders.

## Current project status

| Capability | Status |
| --- | --- |
| Repository and domain directory layout | Scaffolded |
| Backend classes and application logic | Empty placeholders |
| Frontend components and application logic | Empty placeholders |
| Database schema and seed data | Not implemented |
| Maven/npm dependency configuration | Not configured |
| Local and production environments | Not configured |
| Automated tests | Named but not implemented |
| CI/CD pipelines | Not configured |
| Architecture and operational documentation | Named but not written |

Because `pom.xml`, `package.json`, `docker-compose.yml`, and the helper scripts
are empty, there are currently no valid install, build, test, or run commands.

## Suggested implementation sequence

1. Record architecture decisions and API contracts in `docs/`.
2. Populate `backend/pom.xml`, the application entry point, environment
   configuration, security model, and a database connection.
3. Implement and test one vertical backend slice, including its migration,
   domain model, repository adapter, handler, and controller.
4. Populate `frontend/package.json` and Vite configuration, then implement the
   application shell, routing, providers, and HTTP client.
5. Connect a frontend feature to the completed backend slice.
6. Configure local Docker services and make `scripts/dev.sh`, `build.sh`, and
   `test.sh` executable developer entry points.
7. Add CI checks, production configuration, secrets handling, monitoring,
   backup, and deployment automation.

## Definition of runnable

Before adding setup instructions to this README, the repository should provide:

- pinned Java, Maven, Node.js, and package-manager versions;
- populated dependency manifests and lockfiles;
- documented environment variables with safe example values;
- a complete local database configuration and executable migrations;
- working development, build, and test commands;
- meaningful automated tests with passing CI;
- no committed production credentials or secrets.

Until those items exist, this README should be treated as documentation of the
planned project shape rather than instructions for a working application.
