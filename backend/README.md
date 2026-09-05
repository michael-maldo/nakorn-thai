# Nakorn Thai backend

Spring Boot 4.1.1, Java 21, Maven 3.6.3+, and PostgreSQL (tested with 16).
Flyway creates the twelve menu tables on startup. The backend exposes a read-only
collection endpoint and authenticated menu administration. See
[backend deployment](../docs/backend-deployment.md) for GitHub Actions and VPS setup.

## Create a local database

On an Ubuntu PostgreSQL host, using an administrator account:

```bash
sudo -u postgres createuser --pwprompt nakorn_app
sudo -u postgres createdb --owner=nakorn_app nakorn_thai
```

These are one-time commands for a new role/database. Use a dedicated database;
Flyway does not create PostgreSQL login roles or the database itself.
Do not expose PostgreSQL publicly for the frontend; browsers call the backend API.

## Environment files

| File | Purpose | Commit to Git? |
|---|---|---|
| `.env.dev.example` | Local development template | Yes |
| `.env.prod.example` | VPS production template | Yes |
| `.env.dev` | Local development settings and password | No |
| `.env.prod` | Production settings and password | No |

The working environment files were created locally with placeholder passwords and
permissions 600. They are ignored by Git. On a fresh clone, copy the templates:

```bash
cd backend
cp -n .env.dev.example .env.dev
cp -n .env.prod.example .env.prod
chmod 600 .env.dev .env.prod
```

Replace the password placeholder in the environment you are using with the password
assigned when creating its PostgreSQL role. The dev and prod databases live on
separate hosts; use different passwords even if their role/database names match.
The production template assumes PostgreSQL is on the same VPS at 127.0.0.1:5432.
Change DB_URL if PostgreSQL is hosted elsewhere. These files do not create a role
or database, and the frontend must never receive their contents.

Spring Boot does not automatically read `.env` files. The commands below explicitly
export their contents before starting Java. Only source trusted files. Keep values
literal and quoted where necessary; do not use shell expansion or commands. A
password containing a single quote requires appropriate quoting for the loader used.

## Run locally

From `backend/`, with Java 21+ selected, edit `.env.dev`, then:

```bash
(
  set -a
  . ./.env.dev
  set +a
  if [ "$DB_PASSWORD" = 'REPLACE_WITH_DEV_DATABASE_PASSWORD' ]; then
    echo 'Set the database password in .env.dev first.' >&2
    exit 1
  fi
  mvn spring-boot:run
)
```

The subshell keeps these variables out of your parent shell after the process ends.
For an executable JAR, build with `mvn verify`, then replace the Maven run command
above with `java -jar target/nakorn-thai-backend-0.1.0-SNAPSHOT.jar`.

## Run on the VPS

The production file has been prepared locally, not installed on the VPS. From the
backend directory on the VPS, create `.env.prod` from `.env.prod.example`, set its
actual credentials, and restrict permissions:

```bash
cp -n .env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod
```

For a manual startup, from a backend directory containing the built JAR:

```bash
(
  set -a
  . ./.env.prod
  set +a
  if [ "$DB_PASSWORD" = 'REPLACE_WITH_PROD_DATABASE_PASSWORD' ]; then
    echo 'Set the database password in .env.prod first.' >&2
    exit 1
  fi
  java -jar target/nakorn-thai-backend-0.1.0-SNAPSHOT.jar
)
```

For the systemd service, keep the production file outside release directories,
for example `/etc/nakorn-thai/backend.env`, owned by root with mode 600. Its service
unit can use `EnvironmentFile=/etc/nakorn-thai/backend.env` to load these same literal
assignments. The repository includes a service unit and automatic deployment workflow;
complete the [one-time VPS setup](../docs/backend-deployment.md) before running it.

Both profiles bind HTTP to `127.0.0.1:8080`; an Nginx reverse proxy can later expose
`/api/`. SERVER_ADDRESS and SERVER_PORT override these defaults. Actuator health and Prometheus metrics use a separate loopback listener at port 8081.

```bash
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:8080/api/menu/collections/signature-dishes/items
```

## Migrations

- `V1__create_identity_schema.sql`: reserved identity placeholder, with no identity DDL.
- `V2__create_menu_schema.sql`: twelve tables, foreign keys, checks, partial unique
  indexes, and modification timestamp triggers.
- V3–V7: existing empty domain placeholders. Flyway records these as applied;
  future domain work must use NEW migration versions rather than filling them in.
- `V8__seed_signature_dishes.sql`: the four existing homepage dishes, categories,
  and ordered signature collection. No invented prices or dietary/allergen claims.

This implementation assumes these placeholders have never been applied to an
existing application database. Before targeting an existing database, inspect
its `flyway_schema_history`. Do not use repair/baseline to conceal checksum drift;
use a reviewed forward migration if historical files were already applied.
Flyway clean and automatic baselining are disabled. Hibernate is configured to
validate rather than generate DDL. All twelve tables now have @Entity mappings,
so Hibernate validates their mapped columns on startup. PostgreSQL integration
tests additionally verify constraints, relationships, and persistence behavior.
Spring Data repositories support internal saves and authenticated menu administration.
See [the dashboard guide](../docs/menu-dashboard.md) for the initial CRUD scope.

## Menu responses

The endpoint returns collection metadata and an `items` array. Unknown, draft,
future, or expired collections return 404. Published empty collections return 200
with an empty array. Item visibility requires a published item and active category;
unavailable items remain visible. Collection order is deterministic.

Unvaried dishes have `profileScope: ITEM`. Dishes with active variations have
`profileScope: VARIATION_REQUIRED`, `profile: null`, and independently scoped
profiles on each variation. Unverified badges are omitted; prior allergen warnings
remain visible with review status, including warnings referencing inactive vocabulary.
No claims are inferred from empty arrays. The API does not cache scheduled responses.

Images initially return null: persistent storage is not configured. After media
provisioning, store relative, URL-safe keys such as `menu/yellow-curry.jpg` and set
`MEDIA_BASE_URL` to the persistent public media prefix (default `/media/`). The API
only resolves references; it does not upload or serve media. Storage must survive
frontend releases. Do not populate database keys with Vite's generated asset hashes.

Menu item writes are restricted to the configured admin account. The initial editor
enforces version checks and invalidates food verification on name/description edits.
Future variation editors must also enforce the default-variation and independent
review rules; they are outside the initial dashboard scope.

## Tests

`mvn verify` runs unit tests. PostgreSQL integration tests are explicitly skipped
unless `DB_TEST_URL` is set. To run the full suite, provision a dedicated disposable
PostgreSQL database, then:

```bash
export DB_TEST_URL=jdbc:postgresql://localhost:5432/nakorn_menu_test
export DB_TEST_USERNAME=nakorn_test
read -rsp 'Test database password: ' DB_TEST_PASSWORD
export DB_TEST_PASSWORD
mvn verify
```

Never point tests at production: Flyway applies migrations and seed data to the
selected database. Individual test fixtures roll back. Tests cover migration
creation, constraints, collection scheduling/visibility/order, variation profile
isolation, review status, and API behavior using real PostgreSQL.

The detailed design is in [menu-schema-v1.md](../docs/menu-schema-v1.md).

## Observability

HTTP endpoints automatically emit Micrometer metrics/traces and structured request
completion logs through the existing shared/observability components.
Prometheus scrapes the private management listener; Alloy collects JSON logs for
Loki; optional OTLP HTTP export sends sampled traces to Tempo or a collector.
See [configuration and verification](../infrastructure/monitoring/grafana/README.md).

## Libraries carried over from the previous project

- Lombok uses the Spring Boot-managed version. Constructor injection in the menu
  controller/handler uses @RequiredArgsConstructor, and request logging uses @Slf4j.
  Explicit annotation processing applies to main and test compilation. Lombok is
  excluded from the executable JAR. Java records remain API records. Entities use
  @Getter/@Setter/@NoArgsConstructor without @Data, avoiding generated equality or
  toString methods that traverse lazy relationships. The mapper keeps an explicit
  constructor because it normalizes an injected media URL.
- Jakarta Bean Validation declares the collection slug constraints on ListMenuQuery.
  The handler validates them before database access and preserves the existing 404
  behavior for invalid slugs. Future request DTOs can use @Valid and constraints.
- Spring Security explicitly allows GET collection reads and the two private
  management endpoints. Other routes/methods are denied by default. Requests are
  authenticated via short-lived JWT bearer tokens for persistent staff accounts.
  CSRF remains enabled with a session token for browser writes. Staff menu routes
  require ROLE_ADMIN; all other unregistered routes remain denied. There is no
  default password. See the dashboard guide for environment configuration.
- JJWT 0.13.0 API plus runtime implementation and Jackson adapter are available for
  dashboard JWT authentication. JJWT's adapter uses Jackson 2 internally; the application's
  ObjectMapper and HTTP JSON continue using Boot's Jackson 3. Integration tests
  exercise signing/verification and the JSON API together. See [dashboard identity](../docs/dashboard-identity.md) for signing-key setup,
  rotating refresh cookies and staff account management.
- Boot 4's security-test starter supplies Spring Security test support; its MVC
  test starter already supplies the common test libraries from the old POM.

Existing JPA, PostgreSQL, Flyway, and MVC dependencies use Boot 4-compatible starters.
The old project's Boot 3 version and Maven coordinates are not copied.

## Entity-based persistence

All entity and repository files live in the existing menu/infrastructure folder.
The domain MenuItem record and public response shape are unchanged.

- MenuItemJpaEntity and eleven related @Entity classes map the twelve menu tables.
- MenuAuditJpaEntity supplies a nullable Long @Version for new-entity detection
  and optimistic locking, plus database-generated timestamps. Generated timestamps
  are read back after inserts/updates, including the existing PostgreSQL trigger.
- MenuUuidJpaEntity generates UUIDs for entity inserts. Association tables use
  MenuAssociationId with @EmbeddedId, @AttributeOverrides, and @MapsId relations.
- Each table has a typed Spring Data JpaRepository. JpaMenuItemRepository implements
  the domain interface using the item and collection Spring Data repositories.
- Visibility and collection ordering use JPQL. MenuItemMapper converts entity
  relationships into immutable API records inside the read-only transaction.
- Relationships are lazy and batch-loaded in groups of 64. Avoid fetching multiple
  collection bags in one join. A 32-dish integration test checks bounded query count.
- Relationships have no cascade removal or orphan removal; removing a collection
  membership cannot delete a dish. Database FK deletion rules still apply.
- No native SQL or JSON deserialization is used by the production menu repository.
  Flyway SQL and direct SQL used to test database constraints remain intentional.

Spring Data save/saveAndFlush can now persist entities and detect stale versions.
The initial menu dashboard implements authorized item writes; future variation
editors must also enforce the documented default-variation and food-review
invalidation rules transactionally before saving. Do not expose repositories as REST
endpoints or bind request bodies directly to entities.

Tests also exercise entity insert/update, generated IDs/timestamps, stale detached
saves, every association type, multiple collection memberships, and independent
variation declarations. Tests using JDBC fixtures explicitly clear the persistence
context before rereading externally modified data.

## API tests with JUnit and Mockito

ListMenuApiTest uses JUnit parameterized tests, Spring MVC MockMvc, and @MockitoBean
for MenuItemRepository. The real controller, handler, Jakarta validator, security
filter chain, and correlation filter run without PostgreSQL. No extra JUnit/Mockito
dependency is needed: the existing Boot MVC/security test starters supply them.
Surefire loads Mockito as an explicit Java agent for Java 21+.

From backend/:

```bash
mvn -Dtest=ListMenuApiTest,ListMenuHandlerTest test
```

The 13 API test cases cover the current business endpoint,
GET /api/menu/collections/{slug}/items: JSON item and variation profiles, empty
collections, missing collections, invalid/oversized slugs, public access, denied
writes with and without CSRF, denied unregistered routes, no session cookie, and
per-request correlation IDs. Mockito verifies that invalid or denied requests
never query the repository. The three handler unit tests also run in this command.

Database visibility/scheduling and relationship correctness remain covered by the
PostgreSQL integration tests. Actuator health/Prometheus and real trace export are
covered by scripts/check-observability.py; they are not mocked business controllers.

## Initial menu dashboard

See [menu-dashboard.md](../docs/menu-dashboard.md) for local/VPS startup, admin
credentials, API contracts, seed data and scope. `MenuAdminApiTest` covers staff
security/validation with JUnit and Mockito. `CreateMenuItemHandlerTest` exercises
committed CRUD transactions against PostgreSQL when `DB_TEST_URL` is supplied.

## Pickup ordering

The existing ordering scaffold now implements guest pickup orders, private tracking,
role-restricted FOH/BOH queues and status transitions using JPA. V11 adds three order
tables. See [online-ordering.md](../docs/online-ordering.md) for configuration and
scope. Public writes require CSRF; menu administration remains ADMIN-only.
