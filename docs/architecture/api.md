# API reference

This document describes the implemented Spring Boot API through migration **V16**.
It is a reference to current controllers, request records and handlers, not a proposed
API. Empty scaffold packages do not expose endpoints.

## Connection and conventions

- Public production requests use the website origin, for example
  `https://nakorn-thai.tech-labs.dev/api/...`, through Nginx.
- Local backend defaults to `http://127.0.0.1:8080`; Vite proxies `/api/` and `/media/`.
- Management defaults to `http://127.0.0.1:8081`; it is separate from business APIs.
- JSON requests use `Content-Type: application/json`. Image writes use multipart.
- IDs are UUID strings. Timestamps are ISO-8601. Reservation times are local
  Melbourne datetimes without an offset; function dates are `YYYY-MM-DD`.
- Public menu and order prices use integer AUD cents (`priceMinor`, `totalMinor`).
  Staff menu price writes use decimal AUD dollars (`amount`). Do not interchange them.
- `204` responses have no body. Collection/list response shapes differ by feature;
  there is no universal response envelope or universal pagination contract.
- Routes are currently unversioned. There is no implemented Swagger/OpenAPI endpoint.

## Authentication and CSRF

Protected routes require `Authorization: Bearer <accessToken>`. HTTP Basic is disabled.
Access JWTs expire after 15 minutes. Refresh sessions last 12 hours and use the
`nakorn_staff_refresh` HttpOnly, SameSite=Strict cookie scoped to `/api/identity`.
Production uses Secure cookies. Refresh rotates the token; reuse revokes the session.
Account/session state and current roles are checked on authenticated requests.

**All POST, PUT, PATCH and DELETE requests require CSRF**, including guest submissions,
login, refresh and logout. Fetch the relevant CSRF endpoint, retain its session cookie,
then send the returned `token` under the returned `headerName`. Clients should use
fresh tokens before writes and retain cookies across requests. A CSRF response has
at least `headerName` and `token`; some endpoints also serialize `parameterName`.

| CSRF endpoint | Access |
|---|---|
| `GET /api/identity/csrf` | Public |
| `GET /api/orders/csrf` | Public |
| `GET /api/reservations/csrf` | Public |
| `GET /api/functions/csrf` | Public |
| `GET /api/staff/menu/csrf` | ADMIN |
| `GET /api/staff/orders/csrf` | ADMIN, FOH, BOH |

Successful CSRF reads return `200`. CSRF session cookies do not authenticate staff;
JWTs and refresh cookies have separate responsibilities. Browser clients use
same-origin cookies. Signing keys and refresh tokens do not belong in frontend code.

## Identity

| Method and path | Access | Success |
|---|---|---|
| `POST /api/identity/login` | Public + CSRF | 200; access token, expiry, user; sets refresh cookie |
| `POST /api/identity/refresh` | Refresh cookie + CSRF | 200; same shape as login; rotates cookie |
| `POST /api/identity/logout` | Cookie + CSRF; no bearer required | 204; revokes matching session and clears cookie |
| `GET /api/identity/me` | Authenticated staff | 200; current user |
| `GET /api/identity/users` | ADMIN | 200; user array sorted by username |
| `POST /api/identity/users` | ADMIN + CSRF | 201; created user |
| `PUT /api/identity/users/{id}` | ADMIN + CSRF | 204 |

Login body: `username` and `password`, both required; maximum lengths 50 and 72.
Login normalizes username to lowercase. Invalid credentials return 401. Attempts
are limited per username within each backend process (10/minute), returning 429.

Login/refresh response:

```json
{
  "accessToken": "<JWT>",
  "expiresAt": "2026-09-06T09:15:00Z",
  "user": {
    "id": "11111111-1111-4111-8111-111111111111",
    "username": "front-desk",
    "role": "FOH",
    "enabled": true,
    "version": 0
  }
}
```

User reads/creation return the `user` shape above, without password hashes.
Create body: `username`, `role`, `password`. Usernames match
`[a-z0-9][a-z0-9._-]{2,49}`; roles are ADMIN, FOH or BOH. New/reset passwords require
12–72 characters and no more than 72 UTF-8 bytes.

Update body: `version`, `role`, `enabled`, optional `password`. Omitted/blank passwords
preserve the existing password. Updates revoke the account's sessions. Disabling or
demoting the last enabled ADMIN returns 409. There is no user-delete endpoint.
Refresh failure returns 401 and clears the cookie; its body may be empty.

## Public menu and images

| Method and path | Access | Success |
|---|---|---|
| `GET /api/menu/collections/{slug}/items` | Public | 200; collection and items |
| `GET /media/menu/{name}` | Public | 200; JPEG, or 404 |

Collection slugs currently include `signature-dishes`, `chefs-special-recommendations`,
`regular-menu`, `lunch-specials` and `drinks`. Unknown or non-visible collections
return 404. Publication/date windows and category/item visibility affect results;
unavailable published dishes can remain visible.

Collection response: `id`, `slug`, `name`, `description`, `items`.
Each item has `id`, `slug`, `name`, `description`, `available`, nullable `image`,
`profileScope`, `profile` and `variations`.

- Image: `url`, `alt`, `focusX`, `focusY`, `zoom`.
- Variation: `id`, `name`, `priceMinor`, `currency`, `available`, `defaultVariation`, `profile`.
- Food profile: `allergenReviewStatus`, `allergenReviewedAt`, `dietaryTags`, `allergens`.
- Dietary tag: `code`, `name`, `notes`, `verifiedAt`.
- Allergen: `code`, `name`, `declaration`, `notes`, `verifiedAt`.

Variation profiles do not inherit item claims. Empty declarations do not assert
absence of allergens. Imported lunch items remain unavailable for online ordering.

## Staff menu

All routes below require ADMIN; writes also require CSRF.

| Method and path | Success | Purpose |
|---|---|---|
| `GET /api/staff/menu/items` | 200 | Dashboard data: `items`, `categories`, `collections` |
| `POST /api/staff/menu/items` | 201: `{ "id": "<UUID>" }` | Create dish |
| `PUT /api/staff/menu/items/{id}` | 204 | Update dish and supplied prices |
| `DELETE /api/staff/menu/items/{id}?version=0` | 204 | Archive dish; not physical deletion |
| `POST /api/staff/menu/items/{id}/image` | 204 | Upload image or update focus metadata |

Dashboard items contain `id`, `name`, `slug`, `description`, `categoryId`, `status`,
`available`, `displayOrder`, `collectionIds`, `version`, `image`, and `prices`.
Category/collection options contain `id` and `name`. Each staff price contains
`id`, `name`, and decimal-dollar `amount`.

Create/update body:

```json
{
  "name": "Example dish",
  "slug": "example-dish",
  "description": "Description of the dish.",
  "categoryId": "11111111-1111-4111-8111-111111111111",
  "status": "DRAFT",
  "available": true,
  "displayOrder": 0,
  "collectionIds": [],
  "version": 0,
  "prices": [{ "id": null, "amount": 23.90 }]
}
```

Use real category/collection/variation IDs from dashboard data. Name max 150,
slug max 180 (lowercase hyphen-separated words), description max 10,000. Status:
DRAFT, PUBLISHED or ARCHIVED. Display order is nonnegative; at most 100 collection
IDs and 100 price entries. Version is required for updates and deletion.

Price amounts are nonnegative, with up to seven integer digits and two decimal
places. An existing variation ID updates that price. One null-ID price creates a
Standard variation only for an unpriced dish. Omitted/empty price lists preserve
prices; this is not a general variation create/delete API. Name/description changes
invalidate dietary review. Stale versions return 409; reload before editing again.

Image multipart fields: `version`, `alt`, `focusX`, `focusY`, `zoom`, optional `file`.
Alt text is required (max 255), focus coordinates are 0–100, zoom is 1–3. JPEG/PNG
uploads are limited to 8 MiB and 16 megapixels, then re-encoded to JPEG. Omit `file`
to update an existing image's metadata; an item without an image needs a file.
Send CSRF in the header and let the multipart client generate its boundary.

## Pickup ordering

| Method and path | Access | Success |
|---|---|---|
| `GET /api/orders/options` | Public | 200: `enabled`, `fulfilment: PICKUP`, `payment: PAY_AT_RESTAURANT` |
| `POST /api/orders` | Public + CSRF | 201; order response |
| `GET /api/orders/{id}` | `X-Order-Token` | 200; private tracking response |
| `GET /api/staff/foh/orders?history=false` | ADMIN/FOH | 200; order array |
| `GET /api/staff/kitchen/orders` | ADMIN/BOH | 200; order array |
| `PATCH /api/staff/orders/{id}/status` | Staff role appropriate to action + CSRF | 204 |

Create body: required `requestId` (UUID), `trackingToken` (64 lowercase hexadecimal
characters generated securely by the client), `customerName` (max 100), `phone`
(6–30 characters), `notes` (required, may be empty, max 1,000), and `items`.
Items contain 1–30 distinct lines: `variationId`, `quantity` (1–20),
`expectedUnitPriceMinor` (nonnegative cents).

The server checks ordering availability and authoritative menu prices. Exact retries
with the same request ID/token/body return the original order; changed requests
conflict. Keep the tracking token private and send it as `X-Order-Token`, never in
a URL. A missing/wrong token or missing order returns 404.

Order response fields: `id`, `reference`, `status`, `totalMinor`, `currency`,
`createdAt`, `estimatedReadyAt`, `paidAt`, `cancellationReason`, `customerName`,
`phone`, `notes`, `version`, `items`. Lines contain `dishName`, `variationName`,
`quantity`, `unitPriceMinor`. Guest/kitchen responses suppress customer name and
phone; FOH responses include them.

Queues are capped at 200 records. FOH defaults to active orders; `history=true`
returns completed/cancelled orders created in the preceding 24 hours. Kitchen lists
ACCEPTED, PREPARING and READY orders. These endpoints are not a complete history API.

Status body: `version`, `status`, optional `pickupMinutes`, `paymentCollected`,
optional `reason` (max 500).

| Transition | Role and requirements |
|---|---|
| NEW → ACCEPTED | ADMIN/FOH; `pickupMinutes` 5–180 |
| ACCEPTED → PREPARING → READY | ADMIN/BOH |
| READY → COMPLETED | ADMIN/FOH; `paymentCollected=true` |
| Any active status → CANCELLED | ADMIN/FOH; nonblank reason |

Version conflicts and invalid transitions return 409. Completion records payment
acknowledgment; it does not charge a card. See [ordering](../ordering/online-ordering.md).

## Table reservations

| Method and path | Access | Success |
|---|---|---|
| `POST /api/reservations` | Public + CSRF | 201: `reference`, `message` |
| `GET /api/staff/reservations?date=YYYY-MM-DD` | ADMIN/FOH | 200; date-filtered reservation array |
| `PATCH /api/staff/reservations/{id}` | ADMIN/FOH + CSRF | 204 |

Create body: `requestId` (UUID), `customerName` (required, max 100), `phone`
(required, 6–30 characters), `partySize` (1–20), `requestedAt` (required local
Melbourne datetime), and `notes` (required, may be empty, max 1,000).
The time must be in the future, within 90 days, with minute precision.

Staff rows contain `id`, `customerName`, `phone`, `partySize`, `requestedAt`, `notes`,
`status`, `staffNote`, `updatedBy`, `createdAt`, `updatedAt`, `version`. The date query
is required; results are ordered by requested time, without pagination.

Update body: `version`, `status`, `staffNote` (required, may be empty, max 500).
REQUESTED can become CONFIRMED, DECLINED or CANCELLED. CONFIRMED can become SEATED,
NO_SHOW or CANCELLED. Terminal states cannot be reopened.

Submission is a request, not a confirmed table. Exact retries reuse the reference;
changed details with the same UUID return 409. There is no public lookup/cancellation
or implemented availability endpoint. See [reservations](../reservations/reservations.md).

## Functions / venue enquiries

| Method and path | Access | Success |
|---|---|---|
| `POST /api/functions` | Public + CSRF | 201: `reference`, `message` |
| `GET /api/staff/functions?status=NEW&page=0` | ADMIN/FOH | 200: `items`, `page`, `hasNext` |
| `PATCH /api/staff/functions/{id}` | ADMIN/FOH + CSRF | 204 |

Create body: `requestId` (UUID), `customerName` (required, max 100), `email`
(required, valid email, max 254), `phone` (required, 6–30 characters), `eventType`
(required, max 80), `guestCount` (1–1,000), nullable `preferredDate`, `preferredTime`
(required, may be empty, max 100), `message` (required, max 2,000).
A supplied date must be today through the next two years in Melbourne time.
The guest limit is input validation, not a promise of venue capacity.

Staff queries default to NEW and page 0. Status can be ALL, NEW, CONTACTED,
CONFIRMED, DECLINED, CANCELLED or COMPLETED; page must be 0–10,000. Pages contain
25 records sorted by creation time then ID. Rows expose `id`, customer/event fields,
`status`, `arrangedDate`, `staffNote`, `updatedBy`, `createdAt`, `updatedAt`, `version`;
`requestId` is represented by `id` on the persisted row.

Update body: `version`, `status`, nullable `arrangedDate`, `staffNote` (required,
may be empty, max 2,000). NEW can become CONTACTED, CONFIRMED, DECLINED or CANCELLED;
CONTACTED can become CONFIRMED, DECLINED or CANCELLED; CONFIRMED can become COMPLETED
or CANCELLED. Same-status saves are allowed for notes, including terminal states.
CONFIRMED/COMPLETED require an agreed date; a new confirmation cannot be in the past.

The submitted preferred date remains separate from the agreed event date. Submission
has exact-retry protection, but does not reserve venue space or send notifications.
There is no public enquiry lookup. See [functions](../functions/functions.md).

## Errors, concurrency and caching

| Status | Typical meaning |
|---|---|
| 400 | Invalid fields, required version/date missing, invalid query |
| 401 | Missing/invalid staff authentication or failed login/refresh |
| 403 | Role denied or missing/invalid CSRF |
| 404 | Missing/non-visible resource; invalid private order tracking token |
| 409 | Stale version, duplicate/conflicting data or invalid state transition |
| 413 | Image upload too large |
| 429 | Login attempt limit reached |
| 503 | Ordering closed/unavailable where reported by the order handler |

Feature exception handlers commonly return `{ "message": "..." }`. Security filters,
refresh failures and framework/proxy errors may return empty or other bodies.
Clients must inspect status and tolerate non-JSON errors; there is no unified error
schema. Use the current version from a fresh read for staff mutations. Never blindly
retry a conflicting update with a fabricated version.

Read caching is explicitly disabled with `no-store` for menu collections, identity
reads, order reads, reservation/enquiry staff lists and their public acknowledgments.
Menu images use `no-cache`. Do not assume every endpoint/error carries identical
cache headers. Credentials, tracking tokens and customer payloads should not be logged.

## Management and unimplemented APIs

`GET /actuator/health` returns health JSON and `GET /actuator/prometheus` exposes
Prometheus metrics on the private management listener. Health details are disabled.
These are not dashboard business endpoints; see [deployment](../deployment/backend-deployment.md)
and [monitoring](../../infrastructure/monitoring/grafana/README.md).

Customer accounts, automatic refunds, order-status notifications, opening-hour
configuration and automatic reservation capacity remain scaffolded. Payment and
requested verification-code APIs are documented below. See the [domain map](domain-map.md) for ownership
and the [identity guide](../identity/dashboard-identity.md) for environment setup.

For the frontend-to-backend execution paths and linked source files, see
[workflow vertical slices](vertical-slices.md).

## V16 payment and tracking additions

Order creation now accepts optional `email` and `paymentMethod`
(PAY_AT_RESTAURANT/PAYPAL/PAYID); responses include `paymentMethod`. Online-payment
orders require confirmed receipt before staff acceptance/handover. Tracking accepts
original tokens or unexpired OTP-issued grants. See the
[payment and verification API reference](../payment/payments-and-tracking.md#api-and-persistence)
for the new endpoints. Refunds and automatic status messages remain unimplemented.
