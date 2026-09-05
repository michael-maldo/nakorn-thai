# Booking reservations

Customers can request a table through the homepage Reservations navigation or
Book a table buttons at `/#/reservations`. The form takes a name, phone number,
party size, requested date/time and optional notes. All times are local Melbourne
time. Requests must be in the future, within 90 days, for 1–20 guests.

This version records **requests, not guaranteed table availability**. Staff must
check opening hours and seating availability and call the guest before confirming.
There is no table allocation, automatic capacity calculation, deposit, email/SMS
notification or public booking lookup. Customers should contact the restaurant to
change or cancel their request, quoting the reference shown after submission.

## Staff workflow

Sign in at `/#/staff` using JWT authentication and open Reservations. ADMIN and FOH
can access `/#/staff/reservations`; BOH cannot view guest booking details. Select a
Melbourne date and refresh to see requests. Add a staff note when updating status.

- REQUESTED → CONFIRMED, DECLINED or CANCELLED
- CONFIRMED → SEATED, NO_SHOW or CANCELLED
- Final statuses cannot be reopened in this version.

The record stores its latest staff note, acting username and update time. It does
not yet keep a separate history of every transition. Updates use a version check
and database row lock: stale updates return 409 and staff must refresh before retry.

## Persistence and API

Flyway `V13__create_reservations.sql` creates the reservation table and date index.
The old empty V3 migration remains untouched. New source files use the existing
reservation scaffold. No additional environment variables are needed; the database
and JWT configuration in [dashboard-identity.md](dashboard-identity.md) still apply.

| Endpoint | Access |
|---|---|
| `GET /api/reservations/csrf` | Public CSRF token |
| `POST /api/reservations` | Public, CSRF required |
| `GET /api/staff/reservations?date=YYYY-MM-DD` | ADMIN or FOH |
| `PATCH /api/staff/reservations/{id}` | ADMIN or FOH, CSRF required |

Create body: `requestId` (client-generated UUID), `customerName`, `phone`,
`partySize`, `requestedAt` (local ISO datetime without timezone), and `notes`.
The response contains a reference and a request acknowledgment, without contact
information. Exact retries with the same UUID return the same acknowledgment;
changed details require a new UUID. The form retains the UUID during retries on
that page; reloading the page starts a new request.

Update body: `version`, `status`, `staffNote`. Staff reads contain guest details
and send `Cache-Control: no-store`. Public requests never expose a booking list.

## Local verification

Start PostgreSQL and the backend with the existing `.env.dev` configuration, then
run `npm run dev` in `frontend`. Request tomorrow's booking through the website,
log in as FOH or ADMIN, select tomorrow and confirm it after reviewing availability.
Refresh and mark it seated. Verify a BOH account cannot access Reservations.

Backend database integration tests cover persistence, retries, validation, role
restrictions and stale updates. Frontend API tests cover CSRF and server messages.
Deploy through the existing workflow after setting the production JWT signing key.
