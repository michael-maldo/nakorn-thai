# Booking reservations

Customers can request a table through the homepage Reservations navigation or
Book a table buttons at `/#/reservations`. The form takes a name, phone number,
party size, requested date/time and optional notes. All times use the configured restaurant timezone (initially Australia/Melbourne). Requests must be in the future, within 90 days, for 1–20 guests.

This version records **requests, not guaranteed table availability**. Staff must
check seating availability and call the guest before confirming. New requests are
validated against [restaurant scheduling](../restaurant/scheduling.md); closed requested
times are rejected by the backend.
There is no table allocation, automatic capacity calculation, deposit, email/SMS
notification or public booking lookup. Customers should contact the restaurant to
change or cancel their request, quoting the reference shown after submission.

## Staff workflow

Sign in at `/#/staff` using JWT authentication and open Reservations. ADMIN and FOH
can access `/#/staff/reservations`; BOH cannot view guest booking details. Select a
restaurant-local date and refresh to see requests. Add a staff note when updating status.

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
and JWT configuration in [dashboard-identity.md](../identity/dashboard-identity.md) still apply.

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


## Verified contacts and confirmation delivery

Customers supply a phone, an email, or both and verify at least one before requesting
 a table. The public reservation page handles OTP entry and clears verification on
contact edits. Creation remains REQUESTED; verification never confirms a table.

Public endpoints (writes retain CSRF from `/api/reservations/csrf`):
- GET `/api/reservations/contact-verifications/options`: SMS/email availability.
- POST `/api/reservations/contact-verifications`: `{channel: "SMS" | "EMAIL", destination}`;
  returns `id`, `expiresAt`, `resendAt`.
- POST `/api/reservations/contact-verifications/{id}/verify`: `{code}`;
  returns `id`, `verified`, `expiresAt`.
- POST `/api/reservations`: existing fields plus optional `email`,
  `phoneVerificationId`, `emailVerificationId`. Phone is optional. At least one
  successful, unexpired, matching challenge is required. Each supplied challenge
  is consumed atomically with creation. Retrying the same request UUID/payload
  returns the existing receipt. A different request cannot reuse that challenge.

Twilio Verify manages OTP generation/storage for both channels behind
ContactVerificationProvider; email OTP requires the Verify service's email integration.
Set VERIFY_SMS_ENABLED/VERIFY_EMAIL_ENABLED plus TWILIO_ACCOUNT_SID,
TWILIO_AUTH_TOKEN and TWILIO_VERIFY_SERVICE_SID. Missing/invalid configuration
leaves the channel unavailable without preventing startup. Codes expire after
10 minutes locally; configure the Verify service consistently. Checks are limited
to five attempts; requests have a 60-second cooldown and five-per-hour limit per
normalized destination/channel, serialized by PostgreSQL advisory locks.
Email matching preserves local-part case and normalizes the domain. Australian
local phone numbers accept spaces and normalize to E.164. No OTPs, complete
contacts, provider errors or credentials are included in operational logs.

V29 adds contact_verification and notification_delivery, plus optional email and
verified-channel flags on reservation. Existing reservations remain valid and
unverified; staff can still manage them, but they do not generate automatic messages.

The existing staff PATCH transaction queues RESERVATION_CONFIRMED work only on
REQUESTED -> CONFIRMED, one row per verified channel. Unique
(reservation_id,type,channel) prevents duplicate work. Message content is snapshotted
by the reservationconfirmation handler; delivery adapters only send content.
The scheduled worker polls every NOTIFICATION_POLL_MS (default 10000) and processes
up to 10 notifications per poll, each in its own REQUIRES_NEW transaction. Each
transaction selects one due row with FOR UPDATE SKIP LOCKED, makes one provider
call, records its outcome and commits before selecting the next row. The one row
stays locked through that call so concurrent workers cannot deliver it simultaneously.
Provider HTTP/SMTP timeouts bound normal delivery waits. Other unlocked rows remain
available to concurrent workers, and a later transaction failure cannot roll back
earlier notifications' committed outcomes. Delivery starts after the staff transaction.
Each channel has independent PENDING/SENT/FAILED status, five maximum attempts,
and 60/120/240/480-second retry delays. Errors remain generic, without provider PII.
SMTP delivery uses SMTP_HOST, SMTP_PORT (587), SMTP_USERNAME, SMTP_PASSWORD,
SMTP_FROM and SMTP_STARTTLS (true). SMS delivery uses the shared Twilio credentials
and TWILIO_SMS_FROM. The only added dependency is Spring's mail starter.

Delivery is at least once: a process/database failure after a provider accepts a
message but before that notification's transaction commits can resend that message.
Previously committed messages are unaffected. SMTP/Twilio Messaging cannot
provide exactly-once delivery across a database transaction. SENT work is not
normally retried. After five failures, investigate provider configuration and
reset failed rows operationally for an explicit replay; no admin replay UI is in scope.
Provider acceptance is recorded as SENT, not a handset/inbox delivery receipt.
Monitor notification_failed events and failed rows. Low-cardinality Micrometer counters
reservation.contact.verification.requests, reservation.contact.verification.checks
and notification.delivery.attempts report channel/result/type without destinations
or challenge IDs in metric labels. Configure provider spending
limits and public endpoint edge throttling for abuse across many destinations.
Tests use fake providers; no real messages are sent during validation.
