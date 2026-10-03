# Booking reservations

Customers can request a table through the homepage Reservations navigation or
Book a table buttons at `/#/reservations`. The form takes a name, phone number,
party size, requested date/time and optional notes. All times use the configured restaurant timezone (initially Australia/Melbourne). Requests must be in the future, within 90 days, for 1–20 guests.

This version records **requests, not guaranteed table availability**. Staff must
check seating availability and call the guest before confirming. New requests are
validated against [restaurant scheduling](../restaurant/scheduling.md); closed requested
times are rejected by the backend.
There is no table allocation, automatic capacity calculation, deposit or public
booking lookup. Transactional notifications use the shared outbox and worker. Customers should contact the restaurant to
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
`phoneVerificationId`, optional `email`, `partySize`, `requestedAt` (local ISO
datetime without timezone), and `notes`.
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


## Verified mobile and transactional booking delivery

Reservations and ordering require a mobile successfully verified by SMS. Email is
optional and needs no OTP; when supplied, it also receives transactional updates.
Checkout shares the notification verification component/model with ordering. Mobile
edits immediately clear verification; the backend independently enforces it.
Creation remains REQUESTED and never confirms a table.

Public endpoints (writes retain CSRF from `/api/reservations/csrf`) are unchanged:
- GET `/api/reservations/contact-verifications/options`: generic channel availability.
- POST `/api/reservations/contact-verifications`: `{channel: "SMS", destination}` for
  this business flow; returns `id`, `expiresAt`, `resendAt`.
- POST `/api/reservations/contact-verifications/{id}/verify`: `{code}`;
  returns `id`, `verified`, `expiresAt`.
- POST `/api/reservations`: phone and `phoneVerificationId` are required for new
  requests. Optional email receives messages without verification.
  `emailVerificationId` remains accepted for compatibility but is ignored and
  cannot substitute for SMS. The shared EMAIL capability remains available for
  future uses; neither reservation nor ordering checkout uses it.

The shared handler locks the challenge, checks successful SMS verification,
matching normalized mobile, expiry and absence of any reservation/order consumer.
Consumption, creation and RESERVATION_RECEIVED jobs commit together; rollback
restores challenge eligibility and leaves no booking or jobs. Exact existing
request UUID/payload retries bypass new challenge checks and create no duplicate
jobs. Staff status changes and their notifications also share one transaction.

Twilio Verify manages OTP generation/storage behind ContactVerificationProvider.
Set VERIFY_SMS_ENABLED=true, TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and
TWILIO_VERIFY_SERVICE_SID. Email OTP configuration is not required for bookings.
Missing/invalid configuration leaves the channel unavailable without preventing
startup. Codes expire after ten minutes; five attempts, 60-second request cooldown
and five requests/hour per normalized destination/channel remain shared across
ordering/reservation aliases. Australian mobiles normalize to E.164 with ordering's
normalizer; Australian landlines are rejected for new bookings. Optional email
preserves local-part case and normalizes the domain. Logs omit full contacts,
OTPs, provider errors and credentials.

| Event | Trigger | SMS | Optional supplied email |
|---|---|---|---|
| RESERVATION_RECEIVED | Successful creation, REQUESTED | Yes | Yes |
| RESERVATION_CONFIRMED | REQUESTED -> CONFIRMED | Yes | Yes |
| RESERVATION_DECLINED | REQUESTED -> DECLINED | Yes | Yes |
| RESERVATION_CANCELLED | REQUESTED/CONFIRMED -> CANCELLED | Yes | Yes |

Received says the table is not confirmed. Confirmed includes date/time and party
size. Declined/cancelled have distinct content. SEATED and NO_SHOW queue nothing.
The existing state machine, FOH/ADMIN permissions, row lock and version checks are
unchanged. Unique (reservation_id,type,channel) durably prevents duplicate work;
repeat enqueueing retains existing jobs, including sent/exhausted ones.

V32 only broadens the existing outbox type/owner constraints. V29–V31 remain
unchanged. All previously permitted rows satisfy the broadened constraints, and
no new unique index risks existing duplicates. Historical contact fields and flags
remain unchanged: email-only/unverified-phone records stay readable/manageable,
but new transitions do not queue messages without a verified phone. Already queued
historical jobs remain intact. Historical phone-verified records use the new
notification policy, including email when supplied. No backfill claims verification.

Message content remains in the reservationconfirmation handler; providers only
perform delivery. No extra challenge table, outbox or worker was added.
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
