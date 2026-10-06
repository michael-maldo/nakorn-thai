# Dashboard settings and integrations

ADMIN uses **Settings and integrations** in the existing staff dashboard. FOH/BOH cannot view integration metadata, edit business settings, replace credentials or run tests. Existing Restaurant screens continue to manage timezone, hours and closed dates. The online-order pause control is now ADMIN-only; FOH may read its state.

Business settings are separate from provider configuration. Configured means required fields are present, not that the integration is enabled or delivery has been proved. Connection validation records VALID/INVALID and an audit event. Twilio account/Verify-service access is checked without sending an SMS; SMS delivery remains NOT_TESTED. SMTP tests establish connection/authentication, not inbox delivery. Sending a test email requires a separate explicit action and recipient. PayPal tests perform OAuth only, never order creation or capture. PayID is structurally validated and remains manually reconciled against the bank account.

## Persistence and precedence

V33 adds a JSON settings map to the existing restaurant singleton, four integration configuration rows, and immutable-by-API audit events. Updates use versions and return 409 for stale pages. Setting fields explicitly managed in PostgreSQL override their environment/default counterparts. Unmanaged fields continue using the existing deployment environment. Secrets are **not imported on startup**. Saving a blank secret preserves its current value; explicit clearing stores a tombstone that suppresses environment fallback. Replace requires a whole new value. APIs return only non-secret configuration, configured booleans, source metadata, versions and test/audit metadata. Browser storage never stores credentials.

Legacy environment/default feature flags are preserved during transition, including installations with incomplete optional providers. A dashboard enable operation validates its dependencies; later integration changes also validate explicitly dashboard-managed features. Missing providers continue to fail closed for verification/payment and retry notification work. Public order/reservation options report unavailable if mandatory SMS verification is unconfigured; existing successful challenges can still be consumed safely. Migrate deliberately: configure/test integrations, then save intended business flags. Retire fallback by explicitly managing all fields and removing old provider variables only after controlled testing. `PAYPAL_RETURN_URL` remains deployment-managed and must point to the application's `/#/order-confirmation` route; live requires HTTPS. `NOTIFICATION_POLL_MS` remains a deployment worker tuning value.

## Deployment master key

Generate a unique 32-byte key on the deployment host:

```sh
openssl rand -base64 32
```

Set the output as `NAKORN_CREDENTIAL_MASTER_KEY` in the protected service environment. Never put the generated key in source control, the database, audit history or dashboard. There is no default development or production key. Development uses the same explicitly generated key mechanism. Without a valid key, environment-only installations can start, but secret persistence is refused. If encrypted dashboard credentials already exist, startup validates decryption and fails if the key is missing, malformed, incorrect or ciphertext authentication fails. It never substitutes environment credentials for an unreadable stored value.

Encryption uses JDK AES-256-GCM, a fresh random 96-bit nonce per value, a 128-bit authentication tag and category/field authenticated context. Stored values contain a `v1` format/key-generation marker, nonce and ciphertext/tag. Keep the master key stable across process restarts and application instances. Back up the key **separately** from encrypted PostgreSQL backups, with restricted access and a tested restoration procedure. Losing the key makes those credentials irrecoverable; obtain new provider credentials and perform a controlled administrative/offline recovery before restoring service. Simply changing the key breaks decryption. Online multi-key rotation is not implemented: a future coordinated re-encryption migration must decrypt with the old key, encrypt with a new key/version, verify all records, and coordinate all instances before retiring the old key. Provider-secret replacement through the dashboard does not rotate the master key. Changing the Twilio account/Verify service can invalidate codes issued under the previous service; coordinate changes outside active checkout periods or let those customers request a new code.

## Provider setup and controlled validation

- **PayPal:** save sandbox client ID/secret; saving configuration disables its checkout offer until deliberately enabled. Test OAuth, enable checkout, and run the documented payment sandbox buyer/merchant flow. For live: resolve all unresolved PayPal orders first; save live identity/credentials, test successfully, then explicitly enable. The backend refuses changing PayPal identity while unresolved payments exist. Deployment callback is not editable. Turning off the checkout offer preserves reconciliation of existing PayPal payments. No webhooks/refunds are added by this feature.
- **PayID:** enter public identifier and recipient name. Enable only once complete. Changing instructions with unpaid PayID orders requires explicit acknowledgement; coordinate with staff/customers because existing instructions may differ. Customers still cannot mark themselves paid. FOH/ADMIN must deliberately check the real bank receipt and record its reference using existing versioned reconciliation.
- **Twilio:** configure Account SID, write-only Auth Token, Verify Service SID and E.164 SMS sender. Enable the SMS verification capability before enabling required mobile verification. Connection tests use [account access](https://www.twilio.com/docs/iam/api/account) and [Verify service access](https://www.twilio.com/docs/verify/api/service), without sending unsolicited messages. Sender ownership/delivery needs a controlled manual test. Generic email verification remains reusable; checkout/reservations do not require email OTP.
- **SMTP:** configure host, port, username, write-only password, sender and STARTTLS. Username requires a password. Use a trusted server, require TLS for production authentication, test connection, then explicitly send one test message to an approved recipient. Saving never sends mail.

At least one configured offered payment method must remain when ordering is enabled. Required mobile verification depends on configured SMS Verify. SMS notifications require a sender and verified contacts; disable corresponding SMS updates before disabling mandatory verification. Email updates require SMTP. Business flags control new notification enqueueing; already queued transactional work continues through the existing independent-channel retry worker.

## Audit, rollout and operations

Restaurant timezone, hours, closure and ordering-pause writes also record safe durable events. The most recent 100 audit entries are visible to ADMIN, with actor, timestamp, category, action and changed field names/result only. No secret or ciphertext is recorded; normal dashboard APIs cannot edit/delete history. Database administrators retain database-level privileges. Provider tests execute outside configuration row locks; a concurrent update makes the result stale and requires refresh. Low-cardinality counters record configuration saves and test outcomes without account/customer identifiers.

Before production rollout: back up the database/key, provision the deployment key securely for every instance, review/apply V33 through the normal deployment process, verify all old migrations remain unchanged, run isolated tests, then configure/test providers with controlled sandbox/recipient tests. Monitor audit/test results and existing outbox retries. Automated tests do not prove live provider deliverability or production readiness. Runtime resolution reads PostgreSQL without a cache so saved settings apply immediately. A provider call already in progress can finish using its previously resolved configuration. Existing payment amount/currency/correlation checks, tracking-token authorization and order transitions remain authoritative.

## Alternative notification providers

Twilio remains supported and is the default when provider selections are absent.
SMS delivery and contact verification can use different providers. Select each
at deployment through `NOTIFICATION_SMS_PROVIDER` and
`NOTIFICATION_VERIFICATION_PROVIDER` (`twilio` or `vonage`), or Spring properties:

```yaml
notification:
  sms-provider: vonage
  verification-provider: vonage
```

```yaml
notification:
  sms-provider: vonage
  verification-provider: twilio
```

The opposite combination (`twilio` SMS and `vonage` verification) also works.
Restart after changing selections. Invalid selections fail startup; there is no
silent fallback. Changing verification providers invalidates outstanding challenges;
allow their ten-minute lifetime to elapse during a planned switch.

Vonage uses the existing `RuntimeConfiguration` environment fallback mechanism:

| Environment variable | Purpose |
| --- | --- |
| `VONAGE_API_KEY` | Vonage account API key |
| `VONAGE_API_SECRET` | Secret supplied by the deployment secret store |
| `VONAGE_SMS_FROM` | SMS sender number or permitted alphanumeric ID, e.g. `NakornThai` |
| `VONAGE_VERIFY_BRAND` | Verification brand, 1–18 alphanumeric characters/spaces |
| `VONAGE_VERIFY_SMS_ENABLED` | Enable Vonage SMS verification; default `false` |

Vonage fields and selections are environment/deployment settings in this change;
the current dashboard continues to manage Twilio and does not expose Vonage controls.
Existing Twilio credentials, channel flags and diagnostics remain supported.
Business feature readiness checks use the independently selected providers.
Do not put real credentials in tracked files. Spring does not load `.env` files;
load a trusted environment file explicitly as described in the startup guide.

The adapters use Spring `RestClient`, five-second connection and ten-second read
timeouts, with no SDK dependency. SMS uses the
[Vonage SMS API](https://developer.vonage.com/en/api/sms), checks every returned
message status, and requests Unicode encoding. Success means provider acceptance,
consistent with existing delivery behavior; delivery receipt webhooks are not added.
Sender IDs remain subject to destination-country/account restrictions.

Verification uses [Verify v1](https://developer.vonage.com/en/api/verify) with
API key/secret Basic authentication, six-digit codes, a 600-second PIN lifetime,
and [workflow 6 (one SMS)](https://developer.vonage.com/en/verify/guides/verify-migration-guide).
This assumes the account has access to Verify v1; Verify v2 application/private-key
JWT authentication is not implemented. Vonage supports SMS only here: email is
reported unavailable; selecting Twilio restores its existing SMS/email support.
References remain opaque. Check statuses 101 (missing request), 16 (incorrect
code) and 17 (attempt limit) return false; other provider failures return 503.
Vonage permits three incorrect checks even though the application's existing
maximum remains five. Rate limits, challenge consumption and notification retry
scheduling remain in the existing application/worker logic.

Adapter logs contain provider, operation and outcome only, without customer
numbers, request bodies, secrets, codes or authentication headers.
