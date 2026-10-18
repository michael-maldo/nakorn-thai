# Staff timekeeping — Phase A

## Architecture and scope

Timekeeping lives in `backend/src/main/java/au/com/nakornthai/staff/timekeeping`.
`TimekeepingController` maps validated records to `TimekeepingHandler`, which owns
transactions and uses the existing JPA EntityManager pattern. Persistence entities
and feature-scoped error handling stay in that slice. No new application dependency,
identity model, frontend, Android client, payroll logic or deployment change is introduced.

Existing `staff_user` identities and ADMIN/FOH/BOH roles are reused. The existing JWT
filter validates a live staff session, account status and database role; its principal
is the username. The handler resolves that server-authenticated username to a UUID.
The security allowlist explicitly permits self-service reads for all three roles and
reserves other timekeeping routes for ADMIN. Mutations and administrative reads also
check the database role in the handler. CSRF protection remains enabled.

No architectural conflict was found. Source investigation found migrations through
V37, beyond the older V22 mentioned in AGENTS.md; the next migration is V38. Existing
restaurant scheduling reads the database-configured IANA timezone, initially
Australia/Melbourne. Attendance uses absolute instants rather than reservation-style
local timestamps; no timezone setting or startup data rewrite is needed.

## Database migration

`V38__staff_timekeeping.sql` creates:

- `staff_attendance`: UUID key; staff and creating-administrator foreign keys to
  `staff_user`; `clock_in`, nullable `clock_out`, `created_at` and `updated_at` as
  TIMESTAMPTZ; nonnegative bigint version. End must be strictly after start; infinite
  attendance timestamps are disallowed.
- `staff_attendance_audit`: UUID key; attendance and acting-administrator foreign
  keys; action, occurrence timestamp, JSONB previous/new values and optional reason.
  Manual entries and corrections require a nonblank reason (maximum 1,000 characters).
  Snapshots contain clock-in, clock-out and record version. Initial creation has no
  previous values. All four actions create audit rows.

Foreign keys restrict deletion, preserving references. Indexes support staff/range
listing, global chronological listing and audit history. A partial unique index
allows at most one open shift per employee. A GiST exclusion constraint prevents
any overlapping `[clock_in, clock_out)` ranges for an employee, including concurrent
SQL writes; NULL end is unbounded. Adjacent shifts are permitted. The standard
`btree_gist` PostgreSQL extension provides UUID equality for this constraint.

An UPDATE/DELETE/TRUNCATE trigger makes audit rows append-only. As with other database
controls, a privileged database owner can change the schema or disable triggers;
normal application operations cannot edit or delete audit records.

V38 only creates new objects and installs the extension if absent. It does not modify
existing users or business records, and no older migration is edited. Before a future
production rollout, confirm `btree_gist` is installed/available and the migration role
can create it (or have the database administrator provision it). No production
migration or deployment was performed for this phase.

## Business rules and concurrency

- Live clock-in/out accepts only the employee identifier; timestamps come from the
  server after acquiring the employee lock. Extra client time fields have no effect.
  An existing open shift blocks clock-in; no open shift blocks clock-out with 409.
- Manual creation requires a start, end and reason. Historical, overnight and multiple
  non-overlapping shifts per day are supported. No past-only restriction is imposed.
- Correction replaces both times on the same employee's entry. `clockOut: null` can
  reopen a shift, subject to overlap/open-shift constraints. It requires the current
  version and a reason. The staff identity and creation metadata cannot be changed.
- Every mutation takes a pessimistic lock on the existing employee row. Corrections
  refresh the attendance row after acquiring that lock and compare the submitted
  version. JPA also maintains `@Version`. Concurrent duplicate clock-ins/clock-outs
  have one winner; two corrections using one version have one winner and one 409.
  The database exclusion constraint is an additional independent overlap guard.
- Attendance and audit are flushed and committed in one transaction. Failed writes
  or audit failures roll back both. A no-op correction still advances the version
  and records its reason. There is no delete endpoint or automatic data adjustment.
- Worked seconds are the exact elapsed timestamp difference, including fractional
  seconds and daylight-saving transitions. An open shift has `open: true`, null
  `clockOut` and null `workedSeconds`. It contributes zero completed seconds.
  There are no pay rates, payroll, overtime, rounding or break deductions.

Use ISO-8601 timestamps with `Z` or an explicit offset. Inputs must be within years
0001–9999 and have at most six meaningful fractional digits, matching PostgreSQL's
microsecond precision; higher precision is rejected rather than rounded. Clients
that select local dates should convert restaurant-local midnight boundaries to
explicit-offset instants using the restaurant's configured IANA timezone.

## Authorization and endpoints

All routes start with `/api/staff/timekeeping` and return `Cache-Control: no-store`.
Use the existing identity login/refresh flow and `Authorization: Bearer <accessToken>`.

| Method and suffix | ADMIN | FOH / BOH | Success |
| --- | --- | --- | --- |
| GET `/csrf` | Yes | Yes | 200 |
| POST `/clock-in` | Yes | No | 201 entry |
| POST `/clock-out` | Yes | No | 200 entry |
| POST `/entries` | Yes | No | 201 entry |
| PATCH `/entries/{id}` | Yes | No | 200 entry |
| GET `/me` | Own only | Own only | 200 timesheet |
| GET `/entries` | All/filter by staff | No | 200 timesheet |
| GET `/entries/{id}/audit` | Yes | No | 200 audit page |

Unauthenticated access receives 401; forbidden roles or missing CSRF receive 403.
The existing security layer returns empty 401/403 bodies. Feature errors use
`{"message":"..."}`: 400 for invalid fields, times, reasons, ranges or pagination;
404 for missing staff/attendance; 409 for overlaps, missing/existing open shifts,
stale versions or conflicting database writes. Internal constraint/SQL details are
not returned to clients.

For browser writes, GET `/csrf` using the same cookie session and include the returned
`headerName`/`token` in the write request. The CSRF session cookie is distinct from
the staff JWT and refresh credential. Example response:

```json
{"headerName":"X-CSRF-TOKEN","token":"<csrf-token>"}
```

### Live clock-in and clock-out

POST `/clock-in` or `/clock-out` with:

```json
{"staffUserId":"11111111-1111-4111-8111-111111111111"}
```

Clock-in response (times and IDs are illustrative):

```json
{
  "id":"22222222-2222-4222-8222-222222222222",
  "staffUserId":"11111111-1111-4111-8111-111111111111",
  "clockIn":"2026-10-18T06:00:00Z",
  "clockOut":null,
  "open":true,
  "workedSeconds":null,
  "createdBy":"33333333-3333-4333-8333-333333333333",
  "createdAt":"2026-10-18T06:00:00Z",
  "updatedAt":"2026-10-18T06:00:00Z",
  "version":0
}
```

Clock-out returns the same entry shape with the server's `clockOut`, `open: false`,
a numeric `workedSeconds`, later `updatedAt` and incremented `version`.

### Manual historical entry

POST `/entries`:

```json
{
  "staffUserId":"11111111-1111-4111-8111-111111111111",
  "clockIn":"2026-10-16T22:00:00+11:00",
  "clockOut":"2026-10-17T02:00:00+11:00",
  "reason":"Enter missed attendance from the manager's shift record"
}
```

Returns the entry shape above with normalized UTC times, `open: false`,
`workedSeconds: 14400` and `version: 0`.

### Correction

PATCH `/entries/22222222-2222-4222-8222-222222222222`:

```json
{
  "version":0,
  "clockIn":"2026-10-16T21:45:00+11:00",
  "clockOut":"2026-10-17T02:00:00+11:00",
  "reason":"Correct start time against the shift record"
}
```

Returns the corrected entry with `workedSeconds: 15300`, `version: 1` and unchanged
creation metadata. Reusing version 0 receives 409:

```json
{"message":"Attendance changed; reload before saving"}
```

### Timesheet reads

GET `/me?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z&page=0&size=25`.
ADMIN can use `/entries` with the same parameters and an optional `staffUserId`.
`/me` resolves the user exclusively from authentication and rejects additional
parameters, including any supplied employee ID. Staff cannot access the admin
listing or audit endpoint even when an employee ID is manipulated.

- `from` is inclusive and `to` exclusive. Either may be omitted. If both are supplied,
  `to` must be later. Matching uses interval intersection, not only the start date.
- Totals include each matching completed shift's **full** duration; they are not
  clipped at range boundaries. Open shifts that intersect the range are identified
  and counted separately.
- Zero-based `page`, default 0; `size` 1–100, default 25. Unsupported integer offsets
  receive 400. Attendance sorts by clock-in descending, then UUID descending.
- Counts and completed totals cover all matching rows, independent of the page.
  Each request uses a repeatable-read snapshot for consistent rows/counts/totals.
  Separate pages are separate snapshots; concurrent writes may change pagination.

Response shape (`entries` contains full entry objects):

```json
{
  "entries":[],
  "page":0,
  "size":25,
  "totalElements":0,
  "completedSeconds":0,
  "openShiftCount":0
}
```

### Audit reads

GET `/entries/22222222-2222-4222-8222-222222222222/audit?page=0&size=25`.
Uses the same pagination bounds and sorts by occurrence timestamp, then UUID ascending.

```json
{
  "entries":[{
    "id":"44444444-4444-4444-8444-444444444444",
    "attendanceId":"22222222-2222-4222-8222-222222222222",
    "action":"CORRECTION",
    "actorId":"33333333-3333-4333-8333-333333333333",
    "occurredAt":"2026-10-18T06:05:00Z",
    "previousValues":{"clockIn":"2026-10-16T11:00:00Z","clockOut":"2026-10-16T15:00:00Z","version":0},
    "newValues":{"clockIn":"2026-10-16T10:45:00Z","clockOut":"2026-10-16T15:00:00Z","version":1},
    "reason":"Correct start time against the shift record"
  }],
  "page":0,
  "size":25,
  "totalElements":1
}
```

## Validation and review

Tests are in `TimekeepingApiTest` and `TimekeepingIntegrationTest`, following the
existing MockMvc, JUnit and environment-gated PostgreSQL conventions. The integration
suite commits isolated UUID-based test data so independent transactions can exercise
races; use a disposable database. It intentionally retains audit records, which must
not be deleted by test cleanup against a shared database.

From `backend/`, with Java 21 and a dedicated disposable PostgreSQL database:

```bash
DB_TEST_URL=jdbc:postgresql://127.0.0.1:55438/nakorn_timekeeping_test \
DB_TEST_USERNAME=gnostex DB_TEST_PASSWORD='' \
mvn --batch-mode --no-transfer-progress -Dtest=TimekeepingApiTest,TimekeepingIntegrationTest test
```

Without DB_TEST_URL the integration suite is skipped. Before rollout, review the
extension prerequisite, full-duration intersection reporting semantics, ability to
reopen corrected shifts and operational backup/retention policy for immutable audits.
No production database or deployment workflow needs to be touched for review.

## Files changed

| Path (relative to repository root) | Purpose |
| --- | --- |
| `backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java` | Explicit self-service and administrator route permissions; existing CSRF remains intact |
| `backend/src/main/resources/db/migration/V38__staff_timekeeping.sql` | Attendance, immutable audit history, indexes and integrity constraints |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/AttendanceJpaEntity.java` | Versioned attendance persistence |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/AttendanceAuditJpaEntity.java` | Append-only audit persistence and JSON timestamp snapshots |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/TimekeepingDtos.java` | Validated request records, stable responses and elapsed-duration calculation |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/TimekeepingHandler.java` | Transactions, employee locks, overlap/version checks, queries, totals and audit writes |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/TimekeepingController.java` | REST endpoints, authenticated-principal mapping, CSRF token and no-store responses |
| `backend/src/main/java/au/com/nakornthai/staff/timekeeping/TimekeepingExceptionHandler.java` | Validation and sanitized conflict/unavailability responses |
| `backend/src/test/java/au/com/nakornthai/staff/timekeeping/TimekeepingApiTest.java` | Role matrix, self-service parameter manipulation, CSRF, validation and sanitized errors |
| `backend/src/test/java/au/com/nakornthai/staff/timekeeping/TimekeepingIntegrationTest.java` | PostgreSQL migration/constraints, attendance rules, audit snapshots/rollback, races, duration/range queries and real JWT authorization |
| `docs/staff/timekeeping.md` | Architecture, schema, API examples, authorization, rules and review prerequisites |

## Executed verification

Final validation used Java 21.0.12 and a fresh disposable PostgreSQL 16.14 database:

```text
mvn --batch-mode --no-transfer-progress clean verify
Tests run: 434, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

This includes 4 timekeeping API tests and 15 timekeeping PostgreSQL integration tests.
Fresh Flyway migrations V1–V38, Hibernate schema validation and executable backend JAR
packaging succeeded. The suite exercised duplicate/concurrent clock-in and clock-out,
manual and overnight attendance, adjacent and overlapping shifts, version conflicts,
immutable before/after audits, atomic rollback, fractional/DST duration, filtering and
pagination, self-service isolation, real JWT identity/account-role checks, CSRF,
validation and sanitized database errors. Existing backend regression tests also passed.
`git diff --check` passed. No frontend, Android, production database, deployment workflow,
commit or push was part of this implementation.
