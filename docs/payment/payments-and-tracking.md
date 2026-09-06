# Cart, payments and order tracking verification

## Customer experience

Adding a dish activates a fixed cart bar at the bottom of the viewport. It remains
across hash-route changes, including staff pages, while the cart is nonempty.
The popup holds quantities, removals, total, ordering availability and checkout.
It uses a native modal dialog with Escape dismissal and focus return. The inline
menu cart, checkout count link and visible added-item message have been removed.

Checkout offers configured payment methods: pay at restaurant, PayPal and PayID.
An optional email is stored alongside the existing phone number for tracking recovery.
After submitting, the receipt shows the full order UUID, payment controls and tracking.
Choosing PayPal or PayID requires verified payment before staff accept the order.
Existing orders and saved submissions without a payment method remain pay-at-restaurant.

Payment method is fixed once the order is submitted. A customer needing a different
method should contact the restaurant; there is no self-service method-switch API.
PayPal approval and payment checking occur on the receipt page. After returning from
PayPal, press **Confirm / check PayPal payment** to capture an approved payment and
reconcile its state. A browser redirect alone never marks an order paid.

## PayPal setup

Create a PayPal REST application and use sandbox buyer/merchant accounts first.
Set these values in the ignored backend environment file (or `/etc/nakorn-thai/backend.env`):

```dotenv
PAYPAL_ENABLED=true
PAYPAL_ENV=sandbox
PAYPAL_CLIENT_ID='YOUR_SANDBOX_CLIENT_ID'
PAYPAL_CLIENT_SECRET='YOUR_SANDBOX_SECRET'
PAYPAL_RETURN_URL='http://localhost:5173/#/order-confirmation'
```

For live deployment, switch to `PAYPAL_ENV=live`, use live credentials, and set
`PAYPAL_RETURN_URL='https://nakorn-thai.tech-labs.dev/#/order-confirmation'`.
Keep secrets in the backend environment; restart the backend after changes.
No browser PayPal SDK or frontend secret is required for this redirect integration.

The backend uses OAuth client credentials and Orders v2 with CAPTURE intent. It
creates the provider order using persisted AUD totals and the restaurant order UUID
as `custom_id`. Create/capture use stable PayPal-Request-Id values. The provider order
ID is persisted; repeated checks reconcile it instead of creating another payment.
After capture, the backend reads provider details and checks completed capture status,
custom_id, currency and exact amount before recording payment. Timeouts remain
unpaid locally until a later check confirms provider state.

FOH can use **Check PayPal receipt** to reconcile an already-captured payment. It
does not capture an approved payment on the customer's behalf. Customers use their
private tracking token for creation/capture; staff checks require ADMIN/FOH JWTs.
No webhook, dispute handling or automatic refunds are implemented in this version.
If a paid order is cancelled, process the refund in PayPal and coordinate with the
customer. Cancellation does not claim that money was refunded.

Sources: [PayPal Orders v2](https://developer.paypal.com/api/orders/v2),
[PayPal idempotency](https://developer.paypal.com/reference/guidelines/idempotency/).

## PayID setup and reconciliation

Register the restaurant's PayID with its bank and configure:

```dotenv
PAYID_ENABLED=true
PAYID_IDENTIFIER='YOUR_REGISTERED_PAYID'
PAYID_ACCOUNT_NAME='EXACT_BANK_ACCOUNT_NAME'
```

The receipt displays the configured PayID, recipient name, AUD amount and full order
UUID as the bank reference. The customer transfers in their banking application.
A customer click, screenshot or claim of payment never marks the order paid.

FOH/ADMIN must inspect the bank account, verify the amount/reference, then submit
**Confirm bank receipt** with a bank transaction reference. The record retains the
staff username and reference. Protect environment configuration and avoid changing
the PayID while transfers are pending: instructions currently use its configured value.

This is **manual bank reconciliation**, not an automatic bank feed. PayID itself
identifies the receiving bank account; an automatic callback integration needs a
specific bank/PSP API and credentials. No generic or unsigned bank webhook is exposed.
The backend rejects acceptance/handover of unpaid PayID orders. Refunds are manual.

Source: [Australian Payments Plus — PayID](https://www.auspayplus.com.au/solutions/payid).

## SMS and email verification

The implemented provider is Twilio Verify. Email OTPs use its SendGrid integration;
SMS OTPs use the Verify SMS channel. Codes are generated/delivered/checked by Twilio,
not logged or stored as plaintext by this application.

```dotenv
VERIFY_SMS_ENABLED=true
VERIFY_EMAIL_ENABLED=true
TWILIO_ACCOUNT_SID='AC...'
TWILIO_AUTH_TOKEN='YOUR_AUTH_TOKEN'
TWILIO_VERIFY_SERVICE_SID='VA...'
```

Create a Verify service, enable the intended channels and configure the email
integration/template and verified sender through Twilio/SendGrid. Apply provider
fraud protection, service limits and permitted destination countries before enabling
SMS publicly. Configure only the channels you intend to operate. All flags default
false; disabled channels do not issue fake codes or pretend delivery succeeded.

Customers open `/#/track-order`, enter the **full order ID**, choose SMS/email, and
request a code. The server sends only to the contact stored on that order; it does
not accept a replacement destination from the browser. Australian local 10-digit
numbers are normalized to +61; other SMS destinations require international E.164.
An absent order/contact gets a neutral acknowledgment without revealing contact data.

Codes expire locally after 10 minutes; each challenge allows at most five checks.
Sending is limited to once per minute and five per hour per destination across
backend instances using database locks. Provider fraud controls remain necessary
for broader abuse protection. Failed sends still consume the local send allowance.

A successful check creates a random tracking grant valid for 24 hours; only its hash
is stored. Challenges cannot be replayed. The recovered token works for tracking and
customer payment requests. The original receipt token remains valid. Guest contact
fields stay excluded from public order responses. Existing orders without email
cannot use email verification; the original receipt/phone remains the alternative.
This feature sends **requested OTPs**, not automatic order-status SMS/email alerts.

Sources: [Twilio verification](https://www.twilio.com/docs/verify/api/verification),
[verification checks](https://www.twilio.com/docs/verify/api/verification-check),
[email setup](https://www.twilio.com/docs/verify/email).

## API and persistence

All mutations require CSRF (fetch `/api/orders/csrf` with the same cookie session).

| Endpoint | Access / body |
|---|---|
| `GET /api/payments/options` | Public; enabled flags |
| `POST /api/payments/{orderId}` | X-Order-Token; `{ "method": "PAYPAL" }` or PAYID/PAY_AT_RESTAURANT matching checkout |
| `POST /api/payments/{orderId}/check` | X-Order-Token; reconciles and captures approved PayPal orders |
| `POST /api/staff/payments/{orderId}/check` | ADMIN/FOH JWT; reconcile only |
| `POST /api/staff/payments/{orderId}/payid-confirm` | ADMIN/FOH JWT; `version`, `bankReference` |
| `GET /api/order-verification/options` | Public; SMS/email enabled flags |
| `POST /api/order-verification/start` | Public; `orderId`, `channel` (sms/email); returns challengeId |
| `POST /api/order-verification/check` | Public; `challengeId`, numeric `code`; returns requestId/trackingToken/expiresAt |

V16 adds nullable order email, expands payment methods and creates `order_payment`,
`order_verification`, `order_tracking_grant` (22 application tables total). Response
`paymentMethod` distinguishes order methods; `paidAt` remains authoritative locally.
Existing migrations are preserved. Old verification rows/grants currently require
an operations retention policy; there is no automatic purge job in this version.

## Verification before live enablement

Automated tests mock providers and cover tracking authorization, CSRF, exact payment
amounts, repeat capture, bank confirmation roles, verification replay/send/attempt
limits and unpaid-order acceptance. They do not prove delivery or merchant-account setup.

Using sandbox/test accounts, verify a mixed cart across page changes, PayPal approval
and capture, reload/retry after a lost response, PayID manual reconciliation, SMS/email
code recovery on a second browser, expired/wrong codes, and staff payment guards.
No live messages, bank transfers or PayPal charges were sent during implementation.
