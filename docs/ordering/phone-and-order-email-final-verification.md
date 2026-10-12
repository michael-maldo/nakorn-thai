# Final PostgreSQL and notification verification

Verified on 12 October 2026. **Passed: 373 backend tests; zero failures, errors or skips.** No confirmed application defect was found. Application code, migrations, deployment scripts and production configuration were not changed during this pass. No notifications were sent externally, and nothing was committed, pushed or deployed.

## Database isolation and infrastructure

CI provisions PostgreSQL 16 and enables integration suites with DB_TEST_URL, DB_TEST_USERNAME and DB_TEST_PASSWORD. Local PostgreSQL binaries are available, so a new disposable **PostgreSQL 16.14** cluster was initialized under `/tmp/nakorn-final-pg.RjvzBx/data`, owned by the current unprivileged user. It used only `127.0.0.1:55434` and a Unix socket inside that private temporary directory. Neither the existing development database nor production was accessed.

Three new databases separated verification activities:

- `nakorn_final_verify`: selected integration/unit tests, starting with an empty database.
- `nakorn_migration_verify`: an isolated V33 → V34 upgrade with historical notification fixtures.
- `nakorn_complete_verify`: the complete Maven suite, again starting empty.

DB_TEST_URL was set only on test command invocations for these new databases. No application `.env` file was sourced. Staff environment overrides were explicitly unset during the final selected and complete runs. SMTP fallback was loopback-only on port 1 with a test sender, never a production SMTP server. Notification worker tests used mocked EmailSender/SmsSender, mocked diagnostics and a mocked scheduler; order workflow tests also explicitly mocked senders/diagnostics/scheduling. Configuration diagnostics tests used mocks rather than live provider calls.

The cluster was stopped with pg_ctl after verification; data, harness source and logs remain under `/tmp/nakorn-final-pg.RjvzBx` for local review. These artifacts are disposable and not repository changes.

## Exact commands and results

Cluster creation (the temporary directory was created with mktemp):

```bash
initdb -D /tmp/nakorn-final-pg.RjvzBx/data -U nakorn_test --auth=trust --encoding=UTF8 --locale=C
pg_ctl -D /tmp/nakorn-final-pg.RjvzBx/data \
  -l /tmp/nakorn-final-pg.RjvzBx/postgres.log \
  -o '-h 127.0.0.1 -p 55434 -k /tmp/nakorn-final-pg.RjvzBx' -w start
createdb -h 127.0.0.1 -p 55434 -U nakorn_test nakorn_final_verify
createdb -h 127.0.0.1 -p 55434 -U nakorn_test nakorn_migration_verify
createdb -h 127.0.0.1 -p 55434 -U nakorn_test nakorn_complete_verify
```

The corrected selected-suite command, run from `backend/`:

```bash
env -u STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED -u STAFF_ORDER_EMAIL_ADDRESS \
  DB_TEST_URL=jdbc:postgresql://127.0.0.1:55434/nakorn_final_verify \
  DB_TEST_USERNAME=nakorn_test DB_TEST_PASSWORD=disposable-only \
  JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk \
  PATH=/usr/lib/jvm/java-21-temurin-jdk/bin:$PATH \
  SMTP_HOST=127.0.0.1 SMTP_PORT=1 SMTP_FROM=test@example.test \
  NOTIFICATION_POLL_MS=3600000 \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2 \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
  mvn --batch-mode --no-transfer-progress \
  -Dtest=NotificationDeliveryIntegrationTest,ConfigurationIntegrationTest,VerifiedOrderIntegrationTest,StaffOrderConfigurationTest,SendOrderConfirmationHandlerTest,OrderEmailDetailsTest test
```

Result: **66 tests passed, 0 failed, 0 errors, 0 skipped**. A final staff-creation rollback regression was added afterward and is included in the complete run below. The initial selected run, before expanded verification tests, had **62 passed, 0 failed/errors/skipped**.

Complete suite, run from `backend/`:

```bash
env -u STAFF_ORDER_EMAIL_NOTIFICATIONS_ENABLED -u STAFF_ORDER_EMAIL_ADDRESS \
  DB_TEST_URL=jdbc:postgresql://127.0.0.1:55434/nakorn_complete_verify \
  DB_TEST_USERNAME=nakorn_test DB_TEST_PASSWORD=disposable-only \
  JAVA_HOME=/usr/lib/jvm/java-21-temurin-jdk \
  PATH=/usr/lib/jvm/java-21-temurin-jdk/bin:$PATH \
  SMTP_HOST=127.0.0.1 SMTP_PORT=1 SMTP_FROM=test@example.test \
  NOTIFICATION_POLL_MS=3600000 \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2 \
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 \
  mvn --batch-mode --no-transfer-progress clean verify
```

Result on **Java 21.0.12**: **373 total, 373 passed, 0 failed, 0 errors, 0 skipped**, BUILD SUCCESS; executable Spring Boot JAR packaged. Total elapsed Maven time: 52.614 seconds. Surefire XML totals independently matched Maven's summary. The earlier implementation report's skipped integrations are now verified; no skipped test is counted as a pass.

Relevant suites within that complete run:

| Suite | Passed | Failed/errors | Skipped |
| --- | ---: | ---: | ---: |
| NotificationDeliveryIntegrationTest | 7 | 0 | 0 |
| ConfigurationIntegrationTest | 33 | 0 | 0 |
| VerifiedOrderIntegrationTest | 18 | 0 | 0 |
| StaffOrderConfigurationTest | 2 | 0 | 0 |
| SendOrderConfirmationHandlerTest | 5 | 0 | 0 |
| OrderEmailDetailsTest | 2 | 0 | 0 |
| **Relevant total** | **67** | **0** | **0** |

Intermediate failure/root cause: the first expanded selected run had 65 tests, 48 passed and 17 context errors (no assertion failures/skips). The newly added EmailSender interface mock replaced the SMTP bean, while real ProviderIntegrationDiagnostics required the concrete SmtpEmailSender type. Mocking IntegrationDiagnostics in VerifiedOrderIntegrationTest corrected this **test-only wiring issue**, prevented diagnostic network calls, and produced a clean rerun. This was not an application defect and required no application change.

Migration harness command (its Java source and extracted packaged dependency jars are in the temporary directory):

```bash
/usr/lib/jvm/java-21-temurin-jdk/bin/javac \
  -cp '/tmp/nakorn-final-pg.RjvzBx/lib/*' \
  /tmp/nakorn-final-pg.RjvzBx/MigrationVerification.java
/usr/lib/jvm/java-21-temurin-jdk/bin/java \
  -cp '/tmp/nakorn-final-pg.RjvzBx:/tmp/nakorn-final-pg.RjvzBx/lib/*' \
  MigrationVerification \
  /home/gnostex/Documents/nakorn/application/nakorn-thai/backend/src/main/resources/db/migration
```

Result: **11 migration/constraint checks passed**. This harness uses the application's packaged Flyway and PostgreSQL JDBC dependencies, targets V33 first, inserts historical fixtures, then migrates to V34 and validates. No Spring application or notification worker is started by the migration harness.

Final evidence queries and cleanup:

```bash
psql -h 127.0.0.1 -p 55434 -U nakorn_test -d nakorn_complete_verify -Atc \
  "SELECT current_database(), current_setting('server_version'),count(*),max(version::integer),bool_and(success) FROM flyway_schema_history GROUP BY 1,2"
psql -h 127.0.0.1 -p 55434 -U nakorn_test -d nakorn_migration_verify -Atc \
  "SELECT type,channel,count(*) FROM notification_delivery GROUP BY type,channel ORDER BY type,channel"
pg_ctl -D /tmp/nakorn-final-pg.RjvzBx/data -m fast -w stop
git diff --check
```

Evidence: `nakorn_complete_verify|16.14|34|34|t`; historical event/channel counts remained one each, plus one STAFF_ORDER_RECEIVED EMAIL. The cluster stopped successfully. Whitespace validation passed.

## Flyway and notification constraints

Both fresh Spring test databases successfully applied **all 34 migrations from an empty schema** and passed Hibernate schema validation. The separate upgrade exercise applied V1–V33, inserted 16 pre-V34 notifications covering the four customer-order and four reservation event types on both EMAIL and SMS, then applied only V34. Flyway validation passed, all 34 history entries were successful, and every historical notification retained its subject, body, pending status and attempt count.

| Check | Result |
| --- | --- |
| Existing order events: RECEIVED, ACCEPTED, READY, CANCELLED, both channels | Preserved and valid |
| Existing reservation events: RECEIVED, CONFIRMED, CANCELLED, DECLINED, both channels | Preserved and valid |
| STAFF_ORDER_RECEIVED with an order owner and EMAIL | Accepted |
| Staff email plus customer ORDER_RECEIVED email for the same order | Coexist |
| Duplicate staff order/type/channel job | Rejected with SQLSTATE 23505 |
| Staff SMS | Rejected with SQLSTATE 23514 |
| Staff with reservation owner instead of order owner | Rejected with SQLSTATE 23514 |
| Staff without an order owner | Rejected with SQLSTATE 23514 |
| Staff with both order and reservation owners | Rejected with SQLSTATE 23514 |

The existing notification_delivery_order_event_unique index remains the independent customer/staff idempotency backstop. V34 extends the type and owner checks and adds the staff EMAIL restriction; it leaves reservation uniqueness intact. Existing reservation delivery/retry integration tests passed as well.

## Transactional workflow and configuration results

Actual order creation through MockMvc and the existing transaction verifies all four customer/staff email toggle combinations. SMS is separately disabled for these email-specific checks. Unverified phone entry remains permitted only when the existing mandatory verification setting is disabled. Staff work does not require customer email or verified phone independently of that order rule.

| Requested behavior | Evidence/result |
| --- | --- |
| Customer enabled / staff enabled | One customer email and one staff email |
| Customer enabled / staff disabled | One customer email; no staff email |
| Customer disabled / staff enabled | One staff email; no customer email |
| Both disabled | Neither email |
| Customer email absent / staff enabled | Staff email queued; no customer email |
| Idempotent creation replay after recipient change and disablement | One order; original customer/staff jobs retained without duplication |
| Creation rollback | Order and staff job both absent after rollback |
| Staff recipient captured at queue time | First recipient retained after settings change |
| Recipient changed after queueing | Existing job targets first recipient; not the replacement |
| Staff disabled after job queued | Existing staff job still delivered/retried by the real worker with a mocked sender; later new order does not queue staff work |
| Independent retries | Staff failure leaves customer SENT; staff retries only when due; customer not resent; staff attempts advance independently |
| Notification delivery during HTTP creation | No mock sender interactions |

Safe default is confirmed with no staff environment override and no persisted staff override: staffOrderEmail=false, fallback address hello@nakorn.com.au. StaffOrderConfigurationTest also verifies a completely empty MockEnvironment and immediate dashboard-over-environment precedence. Admin API tests confirm recipient syntax rejection, required usable recipient when enabled, rejection with missing SMTP configuration, and successful enablement with complete SMTP configuration, taking effect through fresh runtime snapshots without restart. Existing Admin/FOH/BOH permissions tests pass.

“Configured SMTP” uses existing configuration completeness/syntax rules. It does not prove network connectivity, authentication, relay authorization or inbox delivery; none of those were tested against a live provider.

## Safe rollout sequencing — documented, not performed

Inspected `.github/workflows/deploy.yml`, `infrastructure/scripts/activate-backend.sh`, `infrastructure/systemd/nakorn-thai-backend.service`, application Flyway settings and `docs/deployment/backend-deployment.md`.

1. **Migration V34:** Keep staff notifications disabled in both effective dashboard settings and environment fallbacks. Follow the established production backup procedure before schema rollout; no backup/deployment action was performed here. V34 must be applied before new-purpose jobs can be queued. The existing release script has no separate migration command: it switches the JAR symlink and restarts the systemd service; the new application runs Flyway during startup, before its worker is initialized. A separately staged migration is also possible, but is not a new requirement or script introduced by this verification.
2. **Backend and worker replacement:** The configured single systemd restart stops the previous backend and starts the replacement. On any deployment with additional worker processes, replace every old worker before enabling the new event. Old enums cannot deserialize STAFF_ORDER_RECEIVED records. No old worker should remain while staff jobs can be created.
3. **Deployed-application verification:** The activation script polls `/actuator/health` on loopback management port 8081, up to 30 attempts, and activates frontend assets only after backend success. That check is necessary but does not verify notification behavior. Before enabling staff emails, review deployed version/processes, successful Flyway history through V34, startup/worker logs, public and Admin API behavior, effective disabled staff setting, and recipient/SMTP configuration via the existing Admin interface. Do not use the SMTP test-email control without a separately authorized recipient/delivery test.
4. **Explicit administrator enablement:** Only after migration and application/worker verification should an ADMIN deliberately select the usable recipient and enable staffOrderEmail. This begins queueing staff emails for future new orders independently of customer email enablement. No historical orders are backfilled. Settings changes take effect without restarting.

The activation script restores the previous JAR if health fails; it does not roll back Flyway. V34 preserves old rows, but **after staff jobs exist, reverting to an older application/worker is unsafe without a controlled plan for those jobs**. Disabling the flag alone does not cancel already queued staff deliveries, as verified. Review environment overrides so rollout does not enable the feature before the intended Admin action.

## Remaining risks and files changed in this pass

No unresolved application/test failure remains. Live SMTP acceptance/inbox delivery, relay authorization, real production configuration and actual production worker topology remain intentionally unverified. Preserve the existing retry caveat: SMTP may accept a message before its database outcome commits, so queue uniqueness is not an exactly-once delivery guarantee. Physical mobile-keyboard/email-client review from the original report remains outside this PostgreSQL pass.

Only tests and documentation were changed during this pass:

- `backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java`: adds transactional enablement combinations, absent customer email, replay/recipient/disablement behavior, rollback and explicit mock senders/diagnostics/scheduler.
- `backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java`: adds real PostgreSQL worker retry/delivery verification after recipient change and disablement, with mock delivery providers and configuration restoration.
- `backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java`: adds an Admin rejection test for enabling staff emails without configured SMTP.
- `docs/ordering/phone-and-order-email-final-verification.md`: this report with commands, evidence and rollout sequencing.
- `docs/ordering/phone-and-order-email-implementation.md`: links this final verification so its earlier skipped-database results are not mistaken for the current outcome.

All other working-tree changes shown below predate this verification and belong to the three enhancements. No application implementation, migration or production/deployment configuration was altered in this pass.

## Git status --short

```text
 M backend/.env.dev.example
 M backend/.env.prod.example
 M backend/src/main/java/au/com/nakornthai/notification/domain/NotificationType.java
 M backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/SendOrderConfirmationHandler.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationFields.java
 M backend/src/main/java/au/com/nakornthai/restaurant/configuration/ConfigurationHandler.java
 M backend/src/test/java/au/com/nakornthai/notification/NotificationDeliveryIntegrationTest.java
 M backend/src/test/java/au/com/nakornthai/ordering/createorder/VerifiedOrderIntegrationTest.java
 M backend/src/test/java/au/com/nakornthai/restaurant/configuration/ConfigurationIntegrationTest.java
 M frontend/package.json
 M frontend/src/domains/notification/components/ContactVerification.jsx
 M frontend/src/domains/ordering/pages/CheckoutPage.jsx
 M frontend/src/domains/reservation/pages/ReservationPage.jsx
 M frontend/src/domains/restaurant/model/integrationSettings.js
 M frontend/src/domains/restaurant/model/integrationSettings.test.js
 M frontend/src/domains/restaurant/pages/IntegrationSettingsPage.jsx
 M frontend/tests/e2e/adminSettings.browser.mjs
 M frontend/tests/e2e/reservationVerification.browser.mjs
?? backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderEmailDetails.java
?? backend/src/main/resources/db/migration/V34__staff_order_email_notifications.sql
?? backend/src/test/java/au/com/nakornthai/notification/orderconfirmation/
?? docs/ordering/phone-and-order-email-final-verification.md
?? docs/ordering/phone-and-order-email-implementation.md
?? frontend/src/shared/components/AustralianMobileInput.jsx
?? frontend/src/shared/utils/australianPhone.js
?? frontend/src/shared/utils/australianPhone.test.js
?? frontend/tests/e2e/australianPhone.browser.mjs
```
