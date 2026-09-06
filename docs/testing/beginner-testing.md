Yes. Since your current production VPS is still running the old code, treat it as **untouched reference production** and do all V17–V19/backend validation in a separate test path first.

A practical playbook is:

1. Keep production frozen on the current known-good commit. Do not run V17/V18/V19 there yet.
2. Create a separate PostgreSQL test database on your local machine or VPS, e.g. `nakorn_thai_test`.
3. Point `DB_TEST_URL`, `DB_TEST_USERNAME`, and `DB_TEST_PASSWORD` at that disposable DB.
4. Let Flyway build it from V1 through V19.
5. Run backend unit + integration tests there.
6. Start the new backend on a different port, e.g. `8082`, against the test DB.
7. Hit the APIs manually with curl/Postman/browser and exercise the agreed scenarios.
8. Only after those pass, deploy to a staging-like VPS process/database if you want a closer production rehearsal.
9. Production migration comes last, with DB backup + application deployment coordinated because V19 and the new order writer must go together.

For your repo specifically, I would use a separate PostgreSQL database:

```bash
sudo -u postgres createdb nakorn_thai_test
sudo -u postgres createuser nakorn_test
sudo -u postgres psql
```

Then:

```sql
ALTER USER nakorn_test WITH PASSWORD 'test-password';
GRANT ALL PRIVILEGES ON DATABASE nakorn_thai_test TO nakorn_test;
```

Set your test environment:

```bash
export DB_TEST_URL='jdbc:postgresql://127.0.0.1:5432/nakorn_thai_test'
export DB_TEST_USERNAME='nakorn_test'
export DB_TEST_PASSWORD='test-password'
```

Then from `backend/`:

```bash
mvn test
mvn verify
```

The most important thing is to confirm the integration tests actually run and do not merely say “skipped because DB_TEST_URL missing”.

After that, run the new backend separately from your existing one. For example:

```bash
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:postgresql://127.0.0.1:5432/nakorn_thai_test'
export DB_USERNAME='nakorn_test'
export DB_PASSWORD='test-password'
export SERVER_PORT=8082

java -jar target/*.jar
```

Now you have:

```text
old production backend   -> 8080 -> production DB
new test backend         -> 8082 -> test DB
```

Never let the new backend point at the production DB while you are still validating it.

Then manually test the highest-risk scenarios first. I would use these as your acceptance set:

```text
GET /api/menu/collections
- Main Menu exists
- availability is correct

GET /api/menu/collections/main-menu/items
- categories correct
- price overrides correct
- option groups present
- required options present

POST /api/orders
- missing required option -> rejected
- invalid option -> rejected
- wrong expected price -> rejected
- correct option selection -> accepted
- stored unit price matches server calculation
- collection snapshots persisted
- option snapshots persisted

same item in two collections
- prices stay independent

overnight schedule
- before close -> accepted
- at end boundary -> rejected

idempotent replay
- same request returns stored order
- does not re-evaluate current menu availability
```

Also inspect the DB directly after successful order creation:

```sql
SELECT
    id,
    order_id,
    variation_id,
    snapshot_version,
    collection_id,
    collection_name,
    collection_slug,
    variation_base_price_minor,
    collection_price_override_minor,
    unit_price_minor,
    quantity
FROM restaurant_order_item
ORDER BY created_at DESC;
```

And:

```sql
SELECT
    order_item_id,
    option_group_name,
    option_name,
    price_delta_minor,
    quantity
FROM restaurant_order_item_option;
```

That is how you verify the Java code did what the design says, not merely that the HTTP call returned `200`.

Before production, I would do one final rehearsal with a **copy of production data into a disposable staging database**, then run Flyway V17–V19 against that copy. That catches migration issues caused by real legacy rows.

The production deployment order should be:

```text
1. stop writes / backend
2. backup database
3. deploy new application artifact
4. run Flyway V17/V18/V19
5. start new backend
6. run smoke tests
7. enable frontend traffic
```

For V19 specifically, avoid the dangerous state:

```text
new schema + old backend
```

because the old writer cannot satisfy the V19 snapshot requirements.

If you want, once Codex finishes, I can give you a concrete `TESTING.md` playbook with exact shell commands, SQL checks, curl requests, expected results, and rollback steps for this Nakorn Thai release.



##############################################################################################################


Yes — and for your case, I would use **all three layers**, because they test different things.

The best split is:

```text
JUnit/Mockito
    ↓
Spring integration tests against PostgreSQL
    ↓
curl shell script for black-box acceptance testing
```

Postman is optional. It is useful interactively, but for a repeatable release playbook, I would prefer a **curl-based shell script** because you can put it in Git, run it from CI/VPS, and see pass/fail immediately.

For your new menu/order model, I would structure it like this:

```text
backend tests
├── unit tests
│   ├── pricing rules
│   ├── availability rules
│   └── option validation
│
├── integration tests
│   ├── JPA mappings
│   ├── Flyway V17-V19
│   ├── real PostgreSQL
│   └── transactional order persistence
│
└── scripts/
    └── test-menu-ordering-api.sh
        └── curl against a running backend
```

### Where JUnit and Mockito fit

Use **JUnit** for the actual test framework.

Use **Mockito** when you want to isolate one Java class from its dependencies.

For example, if you have something like:

```java
class MenuPricingService {
    long calculatePrice(...) { ... }
}
```

then JUnit + Mockito is ideal for fast tests like:

```text
variation = 2390
override = 1990
prawns = +600

expected unit price = 2590
```

Mockito is useful when the class depends on repositories or another service and you don't want a real DB involved.

But Mockito alone does **not** prove your JPA mappings or SQL work.

### Where Spring/PostgreSQL integration tests fit

This is probably the most valuable layer for this particular change.

These should actually:

```text
start Spring
↓
run Flyway
↓
connect to PostgreSQL
↓
insert/read real menu data
↓
call CreateOrderHandler/controller
↓
inspect persisted rows
```

That verifies the design across Java + JPA + SQL + transactions.

For example:

```java
@Test
void lunchPriceOverrideAndPrawnOptionArePersistedCorrectly() {
    // seed collection/item/variation/options

    // POST/create order

    // assert response price

    // query restaurant_order_item

    // assert:
    // variation_base_price_minor = 2390
    // collection_price_override_minor = 1990
    // unit_price_minor = 2590

    // query restaurant_order_item_option
    // assert prawns = 600
}
```

That is much stronger than just inspecting the service implementation.

### And then write a curl acceptance script

I strongly recommend this for you.

Something like:

```text
scripts/test-menu-ordering-api.sh
```

It can test the running application from the outside, just like the React frontend would.

For example:

```bash
#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8082}"

echo "Checking collections..."

response=$(curl -fsS \
  "$BASE_URL/api/menu/collections")

echo "$response" | jq .

echo "Checking Main Menu..."

response=$(curl -fsS \
  "$BASE_URL/api/menu/collections/main-menu/items")

echo "$response" | jq .

echo "API smoke test passed."
```

Then run:

```bash
BASE_URL=http://127.0.0.1:8082 \
./scripts/test-menu-ordering-api.sh
```

You can make it much stronger by asserting values:

```bash
count=$(echo "$response" | jq '.items | length')

if [ "$count" -ne 40 ]; then
    echo "FAIL: expected 40 items, got $count"
    exit 1
fi
```

Or check the collection:

```bash
slug=$(echo "$response" | jq -r '.slug')

if [ "$slug" != "main-menu" ]; then
    echo "FAIL: expected main-menu, got $slug"
    exit 1
fi
```

And later test order submission:

```bash
response=$(curl -fsS \
  -X POST \
  "$BASE_URL/api/orders" \
  -H 'Content-Type: application/json' \
  -d '{
    "items": [
      {
        "collectionId": "...",
        "variationId": "...",
        "quantity": 1,
        "expectedUnitPriceMinor": 2990,
        "selectedOptions": [
          {
            "optionId": "...",
            "quantity": 1
          }
        ]
      }
    ]
  }')
```

Then use `jq` to assert the response.

### Postman vs curl

For your workflow, I'd think of them this way:

| Tool                    | Best use                                    |
| ----------------------- | ------------------------------------------- |
| Postman                 | exploratory/manual API testing              |
| curl                    | repeatable release/smoke/acceptance scripts |
| JUnit                   | Java behavior                               |
| Mockito                 | isolated Java units                         |
| Spring integration test | Java + DB + Flyway + transactions           |

So if you ask me which one you should build **first for release testing**, I'd say:

```text
1. JUnit/Spring integration tests
2. curl acceptance script
3. Postman only when useful interactively
```

The nice part is that the same scenario can be tested at different depths.

For example:

```text
"required protein option"
```

can be verified three ways:

```text
JUnit:
OptionValidator rejects missing protein.

Integration:
POST /api/orders through MockMvc rejects it using real DB data.

curl:
running backend returns 4xx and INVALID_OPTIONS.
```

That gives you much more confidence than any one test type alone.

For this V17/V18/V19 work, I would absolutely create a reusable shell test such as:

```text
scripts/test-menu-v2-api.sh
```

and keep it in the repository alongside your automated Java tests.



####################################################################################################




Yes — for true end-to-end testing, you want the test to drive the application through the browser/UI and verify that the backend and database produce the expected result.

The usual tool for this is **Playwright**. Cypress is also valid, but for a modern React app I would prefer Playwright.

The flow becomes:

```text
Playwright browser test
    ↓
React frontend
    ↓
HTTP API
    ↓
Spring Boot backend
    ↓
PostgreSQL
```

That lets you test the exact user journey, not just individual layers.

For your menu/order upgrade, the best E2E scenarios are things like:

```text
Customer:
open menu
→ choose Main Menu
→ open Pad Thai
→ choose Prawns
→ add to cart
→ checkout
→ submit order
→ see confirmation

Then verify:
- UI showed correct effective price
- request succeeded
- confirmation shows selected option
- backend stored final price
- DB stored collection snapshot
- DB stored option snapshot
```

For the staff side:

```text
Staff:
login
→ open menu dashboard
→ create Lunch Menu
→ assign Pad Thai
→ set price override
→ set weekday schedule
→ publish

Then customer:
open menu
→ see Lunch Menu when available
→ see Pad Thai under Lunch Menu
→ see lunch-specific price
```

The important distinction is this:

```text
JUnit integration test
tests backend + DB

Playwright E2E test
tests browser + frontend + backend + DB
```

I would not make Playwright query the database directly for every test. Usually the UI/API behavior should be the primary assertion. But for a few critical release scenarios, it is reasonable to add a backend test utility or direct SQL verification after the browser flow to prove snapshots were persisted correctly.

A clean structure would be:

```text
frontend/
  e2e/
    menu-ordering.spec.js
    staff-menu.spec.js

backend/
  src/test/...
    integration tests

scripts/
  test-menu-v2-api.sh
```

Then your test stack is:

```text
JUnit + Mockito
    unit behavior

Spring integration tests
    backend + PostgreSQL

curl script
    API black-box smoke test

Playwright
    full browser-to-database flow
```

For example, a Playwright test might look roughly like:

```js
import { test, expect } from '@playwright/test';

test('customer orders Pad Thai with prawns', async ({ page }) => {
  await page.goto('http://localhost:5173/#/menu');

  await page.getByText('Main Menu').click();

  await page.getByText('Pad Thai').click();

  await page.getByLabel('Prawns').check();

  await page.getByRole('button', { name: 'Add to cart' }).click();

  await page.getByRole('button', { name: 'Checkout' }).click();

  await expect(page.getByText('$26.90')).toBeVisible();

  // fill checkout details...

  await page.getByRole('button', { name: 'Place order' }).click();

  await expect(page.getByText(/order confirmed/i)).toBeVisible();
  await expect(page.getByText('Prawns')).toBeVisible();
});
```

And the test environment should be isolated:

```text
frontend dev/test server   5173
backend test server        8082
PostgreSQL test DB
```

Do not run E2E against production while developing this. Use a disposable test DB that can be reset between runs.

The ideal automation eventually becomes:

```bash
./scripts/reset-test-db.sh
mvn verify
npm test
npx playwright test
```

That gives you a very strong release gate.

For this particular menu redesign, I would make about **5–8 high-value Playwright E2E tests**, not dozens. The backend integration tests should carry most of the exhaustive rule coverage, while Playwright proves the important real user journeys work end-to-end.



########################################################################################


Yes — that is a very good way to frame your role now.

Initially, treat the application as a **black box**:

```text
known input
    ↓
system under test
    ↓
observed output
    ↓
compare with expected output
```

So instead of starting by reading Codex’s Java implementation, start from the agreed behavior:

```text
Given this menu configuration,
when I call this endpoint or perform this UI action,
then I expect this exact response/state/database result.
```

If the result is correct, you usually do not need to inspect the implementation immediately.

If the result is wrong, then you move inward:

```text
black-box failure
    ↓
identify failing layer
    ↓
inspect logs / API response / DB state
    ↓
inspect relevant code
    ↓
fix
    ↓
rerun test
```

That gives you a very practical workflow:

```text
Design / expected behavior
        ↓
Black-box test
        ↓
PASS ─────────────→ keep going

FAIL
 ↓
diagnose
 ↓
inspect code
 ↓
fix
 ↓
rerun
```

For your Nakorn Thai work, examples are:

```text
Input:
Main Menu + Pad Thai + Prawns

Expected:
2090 + 600 = 2690

Observed:
2590

Result:
FAIL
```

Only then do you inspect whether the bug is in:

```text
menu API response
pricing logic
collection override handling
option lookup
order request mapping
database persistence
```

Likewise:

```text
Input:
Monday 17:00 schedule
request at Tuesday 00:30

Expected:
available

Observed:
unavailable

Result:
FAIL
```

Then you inspect the availability implementation.

This is essentially **behavior-first validation**.

A useful distinction for you is:

```text
Black-box testing
"What does the system do?"

White-box inspection
"How is the system doing it?"
```

Your default can now be:

```text
black box first
white box only when needed
```

That is particularly sensible when Codex is producing much of the code.

Your main responsibility becomes less:

```text
Did Codex write elegant Java?
```

and more:

```text
Does the software satisfy the agreed contracts,
invariants,
security rules,
data rules,
and operational behavior?
```

Then code review becomes a diagnostic and quality tool rather than your first line of validation.

For you, I would organize testing around a simple table like:

| Input                           | Expected            | Actual      | Result |
| ------------------------------- | ------------------- | ----------- | ------ |
| Main Menu + Pad Thai + Prawns   | 2690                | 2690        | PASS   |
| Required protein missing        | 400 INVALID_OPTIONS | 400         | PASS   |
| Lunch override 1790             | 1790                | 2090        | FAIL   |
| Monday 17:00–01:00 at Tue 00:30 | available           | unavailable | FAIL   |

That is a very strong way to work with AI-generated software: **specification first, observation second, implementation inspection third**.



###########################################################################

Yes. The key is to stop thinking in terms of “test the code” and instead ask:

**What promises does the system make that would hurt us if they were wrong?**

For your current menu/order release, I would test these categories first: pricing, option rules, availability, collection behavior, order creation, historical snapshots, idempotency, API validation, persistence, and a few end-to-end customer/staff journeys.

A good starting test matrix is this:

| Area                 | What to test                                | Expected result                        |
| -------------------- | ------------------------------------------- | -------------------------------------- |
| Collection discovery | Published + active collection               | Returned                               |
| Collection discovery | Unavailable but published collection        | Returned with `available=false`        |
| Category fallback    | No collection-specific category             | Canonical `menu_item.category_id` used |
| Price                | Normal variation                            | Variation price used                   |
| Price                | Default variation + collection override     | Override used                          |
| Price                | Override = `0`                              | Zero treated as valid price            |
| Price                | Non-default variation + collection override | Non-default variation price retained   |
| Options              | Required SINGLE missing                     | Reject                                 |
| Options              | SINGLE one option qty 1                     | Accept                                 |
| Options              | SINGLE option qty 2                         | Reject                                 |
| Options              | SINGLE two options                          | Reject                                 |
| Options              | MULTIPLE below min                          | Reject                                 |
| Options              | MULTIPLE above max                          | Reject                                 |
| Options              | Duplicate option ID                         | Reject                                 |
| Options              | Option not attached to item                 | Reject                                 |
| Pricing + options    | Override + priced option                    | Correct final unit price               |
| Cart identity        | Same collection/variation/options           | Merge line                             |
| Cart identity        | Same variation, different collection        | Separate line                          |
| Cart identity        | Same variation, different options           | Separate line                          |
| Availability         | Before start                                | Unavailable                            |
| Availability         | At start                                    | Available                              |
| Availability         | Just before end                             | Available                              |
| Availability         | At end                                      | Unavailable                            |
| Overnight            | Mon 17:00–Tue 01:00 at Tue 00:30            | Available                              |
| Overnight            | Tue 01:00                                   | Unavailable                            |
| Scheduling           | No schedule rows                            | Unrestricted                           |
| Scheduling           | Rows exist, all inactive                    | Unavailable                            |
| Scheduling           | Weekly rule match                           | Available                              |
| Scheduling           | Specific-date rule match                    | Available                              |
| Order request        | Wrong expected price                        | Reject with price-changed error        |
| Order request        | Collection unavailable                      | Reject                                 |
| Order request        | Invalid options                             | Reject                                 |
| Order request        | Valid order                                 | Create successfully                    |
| Persistence          | V19 snapshot fields                         | Persisted correctly                    |
| Persistence          | Option snapshots                            | Persisted correctly                    |
| Historical integrity | Rename/archive collection later             | Old order snapshot unchanged           |
| Idempotency          | Replay same request                         | Return stored result                   |
| Idempotency          | Replay after menu becomes unavailable       | Still return stored result             |
| Security             | Customer endpoint without staff auth        | Works where intended                   |
| Security             | Staff endpoint without auth                 | Reject                                 |
| Error handling       | Bad UUID / malformed payload                | Clean 4xx, not 500                     |

That is your **behavioral contract**.

Then I would divide those tests by layer:

```text
JUnit / backend integration
- pricing
- options
- schedules
- category fallback
- idempotency
- persistence

curl / API black-box
- endpoint responses
- validation errors
- status codes
- JSON shape
- successful order

Playwright E2E
- customer completes an order
- customer sees correct price/options
- staff changes a menu
- customer sees the staff change
```

The most important beginner rule is: **test boundaries and edge cases, not only happy paths**.

For example, if a time window is `17:00–01:00`, do not only test `18:00`. Test:

```text
16:59
17:00
00:59
01:00
```

If option quantity must be exactly `1`, test:

```text
0
1
2
```

If price override can be null or zero, test both:

```text
null
0
positive value
```

That is where bugs hide.

For your project, I would start with about **15–20 backend acceptance cases**, then add only **3–5 E2E tests**. You do not need hundreds immediately.

Your highest-value first 10 would be:

```text
1. normal base price
2. collection override
3. zero override
4. non-default variation ignores override
5. required SINGLE option missing
6. duplicate option rejected
7. overnight schedule boundaries
8. same variation in different collections
9. V19 snapshot persistence
10. idempotent replay
```

If those are correct, you have already covered most of the dangerous logic in this release.






