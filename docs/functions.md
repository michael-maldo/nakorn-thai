# Functions and venue enquiries

The header **Functions** link opens `/#/functions`. Footer Functions and Private
Dining links use the same page. Customers can request the restaurant as a venue
for birthdays, family gatherings, corporate events, wedding celebrations or private
dining. Ordinary table bookings remain at `/#/reservations`.

## Customer flow

The form requires a name, email, phone, event type, estimated guest count and message.
Preferred date and time are optional for customers who are still planning. Dates
use Melbourne time and may be today through the next two years. The 1–1000 guest
validation range is an input limit, not the restaurant's seating capacity.

Submitting saves an enquiry and returns a reference. It does **not** reserve space,
guarantee availability, issue a quote, take a deposit or send an automatic email/SMS.
The team must contact the customer and agree capacity, date/time, menu and pricing.
Customers should quote their reference when contacting the restaurant about changes.
There is no public enquiry lookup exposing contact details.

Exact retries on the same form reuse a UUID to prevent duplicate submissions. A
changed request receives a new UUID; reloading the page also starts a new request.

## Staff dashboard

Sign in at `/#/staff` and open **Functions & enquiries**, or go directly to
`/#/staff/functions`. ADMIN and FOH can read and update enquiries; BOH cannot.

The default queue shows NEW enquiries. Filter by status or ALL; results are paged
in groups of 25, oldest first. Use Refresh to retrieve new enquiries. Open requests
show contact details, the customer's message and preferred date/time.

| Current status | Permitted next statuses |
|---|---|
| NEW | CONTACTED, CONFIRMED, DECLINED, CANCELLED |
| CONTACTED | CONFIRMED, DECLINED, CANCELLED |
| CONFIRMED | COMPLETED, CANCELLED |
| DECLINED, CANCELLED, COMPLETED | No reopening |

Staff can also save notes without changing status. Confirming requires an agreed
event date; a new confirmation cannot have a past date. Record the agreed time,
space, catering and customer communication in staff notes. The preferred customer
date remains separate from the agreed event date.

Every save records the latest note, acting username, timestamp and version. This
version does not store a separate immutable history of every update. Concurrent
stale updates return 409; refresh before saving again. No automatic capacity checks
or exclusive venue holds are performed, so staff must check their event calendar.

## Backend and deployment

Flyway `V15__create_function_enquiries.sql` adds `function_enquiry` and its queue
index. Existing reservation data and migrations are unchanged. The implementation
uses the existing reservation backend folders, staff frontend folder and scaffolded
`website/pages/FunctionsPage.jsx`; there is no folder restructuring.

| Endpoint | Access |
|---|---|
| `GET /api/functions/csrf` | Public CSRF token |
| `POST /api/functions` | Public enquiry submission, CSRF required |
| `GET /api/staff/functions?status=NEW&page=0` | ADMIN/FOH; paginated queue |
| `PATCH /api/staff/functions/{id}` | ADMIN/FOH; CSRF and version required |

Create fields: `requestId`, `customerName`, `email`, `phone`, `eventType`,
`guestCount`, nullable `preferredDate` (YYYY-MM-DD), `preferredTime` and `message`.
Update fields: `version`, `status`, nullable `arrangedDate` and `staffNote`.
Staff reads and public acknowledgments have `Cache-Control: no-store`.

No new environment variables are needed. Use the existing database and
[JWT identity setup](dashboard-identity.md). Restart the updated backend to apply
V15 and deploy the frontend through the existing workflow. Production still requires
`JWT_SECRET_BASE64` and HTTPS for staff authentication.

## Verify locally

Start the backend with `.env.dev` and run `npm run dev` in `frontend`. Open Functions
from the header and submit an enquiry. Sign in as FOH or ADMIN, find it in NEW,
record contact notes, then confirm with an agreed date. Switch the queue to CONFIRMED
to review it. A BOH account must not access the page or API.

Database integration tests cover CSRF, persistence, duplicate retries, input
validation, staff roles, confirmation dates and stale updates. Frontend API tests
cover CSRF acquisition, retry references and failure handling.
