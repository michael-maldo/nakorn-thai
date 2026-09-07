# Restaurant scheduling — Phase 4

The restaurant domain owns the timezone, recurring weekly windows, local closed
dates and the single `RestaurantSchedule.isOpen(Instant)` evaluator. Ordering and
reservation creation consume it. Menu-specific availability remains in menu;
[Phase 5 Lunch Special](../menu/lunch-special.md) composes this capability with a
menu-owned daily cutoff in V22.

## Activation and staff administration

V21 (`V21__add_restaurant_scheduling.sql`) follows V20 and creates
`restaurant_settings`, `restaurant_opening_hours` and `restaurant_closed_date`.
Only the singleton timezone, `Australia/Melbourne`, is seeded. No opening hours or
closure dates are seeded. With no active hours, new orders and bookings fail closed.
Existing homepage opening-hours copy is presentation content, not scheduling data.

An ADMIN can open **Staff home → Restaurant scheduling** (`#/staff/restaurant`)
to change the IANA timezone, add/edit/remove multiple weekday windows, toggle
windows active and manage closed dates with optional reasons. Configure verified
hours before accepting orders/bookings. Ordering also requires the existing
`ONLINE_ORDERING_ENABLED` flag and existing menu/payment availability checks.

All staff scheduling endpoints require ADMIN. Writes require CSRF. Reads use
`Cache-Control: no-store`. Updates/deletes require the resource's current `version`;
stale edits return 409. Settings and entries have audit timestamps and JPA optimistic
versions. A settings-row read/write lock keeps availability snapshots consistent
with schedule administration within the creating transaction.

| Endpoint | Behavior |
| --- | --- |
| `GET /api/restaurant/availability` | Public timezone, evaluatedAt instant and current open flag; no closure reasons |
| `GET /api/staff/restaurant/csrf` | Staff CSRF token |
| `GET /api/staff/restaurant/schedule` | Settings, weekly windows and closed dates |
| `PUT /api/staff/restaurant/settings` | `{timezone, version}` |
| `POST /api/staff/restaurant/hours` | `{dayOfWeek, opensAt, closesAt, active, displayOrder}` |
| `PUT /api/staff/restaurant/hours/{id}` | Window fields plus `version` |
| `DELETE /api/staff/restaurant/hours/{id}?version=N` | Remove window |
| `POST /api/staff/restaurant/closed-dates` | `{closedDate, reason}` |
| `PUT /api/staff/restaurant/closed-dates/{id}` | Closure fields plus `version` |
| `DELETE /api/staff/restaurant/closed-dates/{id}?version=N` | Remove closure |

Weekdays are 1 (Monday) through 7 (Sunday); times are local `HH:mm:ss` values with
whole seconds. Equal endpoints are invalid. Closed dates use `YYYY-MM-DD`; reasons
are optional, up to 500 characters. The database enforces weekday bounds, distinct
time endpoints, nonnegative order/version values, singleton settings and unique
closed dates. Its active-weekday index permits multiple windows per day. Audit
triggers reuse the existing `menu_set_updated_at()` function.

## Availability semantics

The evaluator converts the supplied instant using the persisted restaurant
`ZoneId`, never the JVM, server or browser default. A closed local date rejects
the entire local date first. Otherwise, active current-day windows are checked
with opening inclusive and closing exclusive. Multiple windows permit a midday
gap. A closing time before opening denotes an overnight window, owned by its
starting weekday; the previous day's overnight windows cover the after-midnight
portion. Sunday-to-Monday rollover is supported.

For Monday 17:00–Tuesday 01:00, a closed Tuesday permits Monday 23:00 but rejects
Tuesday 00:30. A closure on Monday alone does not close Tuesday's local portion.
No matching active windows means closed. DST offsets follow the configured zone.

## Ordering and reservations

`CreateOrderHandler` resolves stored successful retries before checking current
availability. For a new order it takes one injected `Clock` instant and reuses it
for restaurant availability, menu collection availability and order audit/snapshot
timestamps. Closed orders return HTTP 409 with `code: RESTAURANT_CLOSED`, without
persisting an order. `/api/orders/options` includes current restaurant availability
in its existing `enabled` field. A successful replay bypasses current availability.

`CreateReservationHandler` also preserves successful retries. New requests still
use a local ISO datetime without an offset, now interpreted in the configured
restaurant timezone. The requested time must be open, future, within 90 days and
minute-aligned; the party-size and other existing rules remain. DST gaps and
ambiguous repeated local times return 400 rather than being silently shifted or
assigned an arbitrary offset. Operation audit timestamps use one injected-clock
instant. Closed requested times return HTTP 409 and `RESTAURANT_CLOSED` without
saving. Current closure does not prevent requesting an open future time.

Existing reservation datetimes remain local timestamps; changing the timezone
changes how those local values are interpreted, without rewriting stored bookings.
Function enquiries, seating capacity and staff status transitions are unchanged.
The reservation pages display the configured timezone; browsers do not evaluate
restaurant schedules. Backend enforcement remains authoritative if a page is stale.

## Validation

Focused tests cover timezone independence and DST, normal/split/overnight windows,
local closed-date overrides, empty/inactive hours, new orders/bookings, stored
replays, a single operation instant, API errors, ADMIN/CSRF restrictions, validation,
versions and frontend CSRF/error behavior.

Run `mvn test` and `mvn verify` in `backend`, and `npm test` / `npm run build` in
`frontend`. PostgreSQL tests require a dedicated disposable database supplied by
`DB_TEST_URL`, `DB_TEST_USERNAME` and `DB_TEST_PASSWORD`; never use production.
The scheduling integration test checks successful V20/V21 Flyway history,
persistence, constraints, indexes, audit fields, versions and API behavior. Skipped
integration tests do not establish that V20/V21 apply successfully.

`MenuSchemaIntegrationTest.v20SeedHasCompleteMainMenuDefaultVariationsAndRequiredReusableOptions`
checks the V20 seed: 82 menu items, 13 categories, 15 dishes with required options,
and Small/Large Sparkling Water variations with Small as the sole default.

## Phase 4 implementation file manifest

- [backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java)
- [backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderExceptionHandler.java)
- [backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateReservationHandler.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateReservationHandler.java)
- [backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationExceptionHandler.java)
- [backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/availability/RestaurantAvailabilityController.java](../../backend/src/main/java/au/com/nakornthai/restaurant/availability/RestaurantAvailabilityController.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/availability/RestaurantAvailabilityService.java](../../backend/src/main/java/au/com/nakornthai/restaurant/availability/RestaurantAvailabilityService.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/domain/OpeningHours.java](../../backend/src/main/java/au/com/nakornthai/restaurant/domain/OpeningHours.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantClosedException.java](../../backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantClosedException.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantRepository.java](../../backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantRepository.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantSchedule.java](../../backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantSchedule.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/ClosedDateJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/ClosedDateJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/JpaRestaurantRepository.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/JpaRestaurantRepository.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/OpeningHoursJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/OpeningHoursJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantAuditJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantAuditJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantExceptionHandler.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantSettingsJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/RestaurantSettingsJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursController.java](../../backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursController.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursHandler.java](../../backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursHandler.java)
- [backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursRequest.java](../../backend/src/main/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursRequest.java)
- [backend/src/main/java/au/com/nakornthai/shared/config/TimeConfig.java](../../backend/src/main/java/au/com/nakornthai/shared/config/TimeConfig.java)
- [backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java](../../backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java)
- [backend/src/main/resources/db/migration/V21__add_restaurant_scheduling.sql](../../backend/src/main/resources/db/migration/V21__add_restaurant_scheduling.sql)
- [backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java)
- [backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java)
- [backend/src/test/java/au/com/nakornthai/reservation/createreservation/CreateReservationHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/reservation/createreservation/CreateReservationHandlerTest.java)
- [backend/src/test/java/au/com/nakornthai/reservation/createreservation/CreateReservationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/reservation/createreservation/CreateReservationIntegrationTest.java)
- [backend/src/test/java/au/com/nakornthai/restaurant/availability/RestaurantClosedApiTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/availability/RestaurantClosedApiTest.java)
- [backend/src/test/java/au/com/nakornthai/restaurant/domain/RestaurantScheduleTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/domain/RestaurantScheduleTest.java)
- [backend/src/test/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursApiTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursApiTest.java)
- [backend/src/test/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/openinghours/OpeningHoursHandlerTest.java)
- [backend/src/test/java/au/com/nakornthai/restaurant/openinghours/RestaurantSchedulingIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/restaurant/openinghours/RestaurantSchedulingIntegrationTest.java)
- [docs/reservations/reservations.md](../../docs/reservations/reservations.md)
- [docs/restaurant/scheduling.md](../../docs/restaurant/scheduling.md)
- [frontend/package.json](../../frontend/package.json)
- [frontend/src/app/AppRouter.jsx](../../frontend/src/app/AppRouter.jsx)
- [frontend/src/domains/reservation/pages/ReservationAdminPage.jsx](../../frontend/src/domains/reservation/pages/ReservationAdminPage.jsx)
- [frontend/src/domains/reservation/pages/ReservationPage.jsx](../../frontend/src/domains/reservation/pages/ReservationPage.jsx)
- [frontend/src/domains/restaurant/api/restaurantApi.js](../../frontend/src/domains/restaurant/api/restaurantApi.js)
- [frontend/src/domains/restaurant/api/restaurantApi.test.js](../../frontend/src/domains/restaurant/api/restaurantApi.test.js)
- [frontend/src/domains/restaurant/pages/RestaurantSchedulePage.jsx](../../frontend/src/domains/restaurant/pages/RestaurantSchedulePage.jsx)
- [frontend/src/domains/staff/pages/StaffDashboardPage.jsx](../../frontend/src/domains/staff/pages/StaffDashboardPage.jsx)

## Validation in this workspace

- `mvn test`: passed; the initial complete run reported 142 tests, zero failures/errors,
  67 skipped. Subsequent API and timezone checks are included in final verification.
- `mvn verify` and final `mvn --batch-mode --no-transfer-progress verify`: passed;
  final result 145 discovered tests, 78 executed successfully, 67 skipped. Executable
  Spring Boot JAR produced.
- `npm test`: 64 passed, zero failures/skips.
- `npm run build`: passed after the final frontend edits.
- `git diff --check`: passed.
- All 67 PostgreSQL-gated tests were skipped because `DB_TEST_URL` was absent;
  `DB_TEST_USERNAME` and `DB_TEST_PASSWORD` were also absent. No database connection
  was configured in the IDE. V20 applied status and V21 PostgreSQL compatibility
  remain unverified. No migrations were applied by this task.
- Maven initially required cache write/download access for missing test and packaging
  dependencies; after that access was granted, validation completed successfully.

AGENTS.md and all pre-existing migrations, including the supplied untracked V20,
were preserved. V21 is the only migration created by Phase 4. No production deployment
or real opening-hour/closure seeding was performed.
