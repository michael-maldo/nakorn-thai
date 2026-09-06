# Nakorn Thai Testing — Beginner Getting Started Guide

## Goal

You are starting from zero testing knowledge.

Do **not** try to learn every testing tool at once.

For the Nakorn Thai application, learn testing in this order:

```text
1. JUnit
2. Mockito
3. Spring Boot integration tests with PostgreSQL
4. curl API smoke tests
5. Playwright end-to-end tests
```

The application flow is:

```text
React frontend
    ↓
Spring Boot backend
    ↓
PostgreSQL
```

Each testing tool checks a different part of that flow.

---

# 1. What Testing Actually Means

A test is simply:

```text
Given some starting conditions
When something happens
Then verify the result
```

Example:

```text
Given:
Pad Thai base price = $20.90
Prawns option = +$6.00

When:
the customer selects Prawns

Then:
the backend price must be $26.90
```

In cents:

```text
2090 + 600 = 2690
```

The test exists so that if somebody changes the code later and accidentally produces `2590`, the test fails immediately.

---

# 2. The Four Testing Levels You Need

## Level 1 — Unit Test

Tests one small piece of Java logic.

Typical tool:

```text
JUnit
```

Sometimes:

```text
JUnit + Mockito
```

Example:

```text
Does the pricing rule calculate 2690?
```

A unit test normally does **not** use PostgreSQL.

---

## Level 2 — Backend Integration Test

Tests multiple real backend parts together.

Example:

```text
Spring Boot
    ↓
CreateOrderHandler
    ↓
JPA / EntityManager
    ↓
PostgreSQL
```

This proves that:

- Java code works
- JPA mappings work
- SQL works
- Flyway migrations work
- transactions work
- data is persisted correctly

For the new menu model, these tests are extremely important.

---

## Level 3 — API Test

Tests the running backend from outside.

Typical tool:

```text
curl
```

Example:

```bash
curl http://127.0.0.1:8082/api/menu/collections
```

This checks:

```text
HTTP
→ Spring controller
→ backend logic
→ database
→ JSON response
```

---

## Level 4 — End-to-End Test

Tests the real user journey through the browser.

Typical tool:

```text
Playwright
```

Example:

```text
open menu
→ choose Pad Thai
→ choose Prawns
→ add to cart
→ checkout
→ place order
→ see confirmation
```

This tests:

```text
Browser
→ React
→ HTTP
→ Spring Boot
→ PostgreSQL
```

---

# 3. Your Testing Pyramid

Think of your tests like this:

```text
                 Playwright
              a few E2E tests

                  curl
             API smoke tests

        Spring integration tests
      backend + real PostgreSQL

             JUnit / Mockito
          many fast small tests
```

Do **not** test every possible rule with Playwright.

Use small backend tests for detailed rules.

Use Playwright for important user journeys.

---

# 4. Start With JUnit

JUnit is the Java testing framework.

A simple test looks like:

```java
@Test
void addsTwoNumbers() {
    int result = 2 + 3;

    assertEquals(5, result);
}
```

The important idea is:

```text
run code
↓
compare actual result with expected result
```

For your application, a pricing test may eventually look conceptually like:

```java
@Test
void calculatesPriceWithPrawnOption() {
    long basePrice = 2090;
    long prawnOption = 600;

    long result = basePrice + prawnOption;

    assertEquals(2690, result);
}
```

Do not worry yet about the exact production class.

First understand how a test works.

---

# 5. The JUnit Assertions to Learn First

Start with only these:

```java
assertEquals(expected, actual);
```

Example:

```java
assertEquals(2690, result);
```

---

```java
assertTrue(condition);
```

Example:

```java
assertTrue(collection.isAvailable());
```

---

```java
assertFalse(condition);
```

Example:

```java
assertFalse(collection.isAvailable());
```

---

```java
assertNull(value);
```

---

```java
assertNotNull(value);
```

---

```java
assertThrows(Exception.class, () -> {
    // code expected to fail
});
```

Example:

```java
assertThrows(InvalidOptionException.class, () -> {
    selectTwoOptionsForSingleChoiceGroup();
});
```

These are enough to get started.

---

# 6. What Mockito Is

Mockito is **not** a replacement for JUnit.

JUnit runs the test.

Mockito creates fake dependencies.

Suppose:

```text
CreateOrderHandler
    ↓
MenuRepository
```

Normally `MenuRepository` talks to the database.

In a unit test, Mockito can pretend to be that repository.

Example conceptually:

```java
@Mock
MenuRepository menuRepository;
```

Then:

```java
when(menuRepository.findVariation(id))
    .thenReturn(testVariation);
```

This lets you test only the handler logic.

Use Mockito when you want:

```text
fast isolated Java test
```

Do not use Mockito when you specifically want to prove that PostgreSQL or JPA works.

---

# 7. Integration Tests Are Different

An integration test uses real infrastructure.

For example:

```text
JUnit
↓
Spring Boot
↓
real repository
↓
real PostgreSQL test database
```

This is where you discover bugs such as:

```text
JPA field name wrong

foreign key wrong

query wrong

Flyway migration incompatible

transaction does not save correctly
```

Unit tests cannot catch many of these problems.

---

# 8. Never Test Against Production

Create a separate test database.

Recommended:

```text
nakorn_thai_test
```

Do not point tests at:

```text
nakorn_thai
```

if that is your real production database.

A safe setup is:

```text
Production:
old/current production backend
→ production DB

Testing:
new backend
→ nakorn_thai_test
```

---

# 9. Test Database Environment Variables

Your repository already expects test database settings.

Set:

```bash
export DB_TEST_URL='jdbc:postgresql://127.0.0.1:5432/nakorn_thai_test'
export DB_TEST_USERNAME='nakorn_test'
export DB_TEST_PASSWORD='your-test-password'
```

Then from:

```text
backend/
```

run:

```bash
mvn test
```

and later:

```bash
mvn verify
```

Important:

If tests say:

```text
SKIPPED
```

because `DB_TEST_URL` is missing, then the PostgreSQL integration tests did **not** actually run.

---

# 10. Your First Useful Menu Tests

After Codex finishes the backend, these are good tests to understand.

## Test 1 — Normal price

```text
variation price = 2090

expected:
unit price = 2090
```

---

## Test 2 — Collection override

```text
variation price = 2090
collection override = 1790

expected:
unit price = 1790
```

---

## Test 3 — Option price

```text
variation price = 2090
Prawns = +600

expected:
unit price = 2690
```

---

## Test 4 — Override + option

```text
variation price = 2090
collection override = 1790
Prawns = +600

expected:
1790 + 600 = 2390
```

---

## Test 5 — Required option missing

```text
Pad Thai requires protein

selected options = none

expected:
order rejected
```

---

## Test 6 — Invalid SINGLE option quantity

```text
Protein group = SINGLE

Prawns quantity = 2

expected:
order rejected
```

---

# 11. Test the Database Result, Not Just the HTTP Response

Suppose the API returns success.

That is useful, but you also want to know:

```text
What did PostgreSQL actually store?
```

For V19, inspect:

```sql
SELECT
    snapshot_version,
    collection_id,
    collection_name,
    collection_slug,
    variation_base_price_minor,
    collection_price_override_minor,
    unit_price_minor,
    quantity
FROM restaurant_order_item;
```

Also inspect options:

```sql
SELECT
    order_item_id,
    option_group_name,
    option_name,
    price_delta_minor,
    quantity
FROM restaurant_order_item_option;
```

This confirms that the backend did not merely return the right number—it persisted the correct historical snapshot.

---

# 12. curl: Your First API Testing Tool

Use curl to test the running backend.

Start with something simple:

```bash
curl -i   http://127.0.0.1:8082/api/menu/collections
```

The `-i` option shows:

```text
HTTP status
headers
body
```

For pretty JSON:

```bash
curl -s   http://127.0.0.1:8082/api/menu/collections   | jq
```

On Fedora, install `jq` if needed:

```bash
sudo dnf install jq
```

---

# 13. Turn curl Into a Test Script

Create:

```text
scripts/test-menu-v2-api.sh
```

Start with:

```bash
#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8082}"

echo "Testing menu collections..."

response="$(curl -fsS "$BASE_URL/api/menu/collections")"

echo "$response" | jq .

echo
echo "PASS: collections endpoint responded"
```

Make it executable:

```bash
chmod +x scripts/test-menu-v2-api.sh
```

Run it:

```bash
./scripts/test-menu-v2-api.sh
```

Or:

```bash
BASE_URL=http://127.0.0.1:8082 ./scripts/test-menu-v2-api.sh
```

---

# 14. Add Assertions to the curl Script

A script becomes a real test when it can fail.

Example:

```bash
response="$(curl -fsS   "$BASE_URL/api/menu/collections/main-menu/items")"

count="$(echo "$response" | jq '.items | length')"

if [ "$count" -ne 40 ]; then
    echo "FAIL: expected 40 menu items, got $count"
    exit 1
fi

echo "PASS: Main Menu contains 40 items"
```

Now:

```text
correct result
→ exit 0

wrong result
→ exit 1
```

This makes the script useful for automated release checks.

---

# 15. Postman or curl?

Both can call APIs.

Use Postman when you want:

```text
manual experimentation
change request
click Send
inspect result
```

Use curl when you want:

```text
repeatable test
stored in Git
run from shell
run from VPS
run from CI later
```

For your project, learn curl first.

Postman is optional.

---

# 16. What Playwright Does

Playwright controls a real browser.

It can:

```text
open page
click buttons
fill fields
select options
submit forms
check visible text
```

Example:

```javascript
import { test, expect } from '@playwright/test';

test('Main Menu opens', async ({ page }) => {
  await page.goto('http://localhost:5173/#/menu');

  await expect(
    page.getByText('Main Menu')
  ).toBeVisible();
});
```

This actually launches a browser and runs your React application.

---

# 17. Your First Real End-to-End Test

Eventually, test:

```text
customer opens menu

→ chooses Main Menu

→ chooses Pad Thai

→ chooses Prawns

→ adds to cart

→ opens checkout

→ enters customer details

→ places order

→ sees confirmation
```

That single test exercises:

```text
Playwright
↓
React
↓
Spring Boot
↓
PostgreSQL
```

That is true end-to-end testing.

---

# 18. Do Not Start With Playwright Yet

Since you are new to testing, I recommend:

```text
first:
JUnit

then:
backend integration tests

then:
curl

then:
Playwright
```

Otherwise you will be debugging:

```text
browser selectors
frontend
HTTP
backend
database
test framework
```

all at the same time.

That is unnecessarily difficult.

---

# 19. How to Read a Test

When you see a test written by Codex, read it in three parts.

## Arrange

Prepare the world.

Example:

```text
create Lunch Menu
create Pad Thai
set price override to 1790
```

Often called:

```text
Given
```

---

## Act

Perform the action.

Example:

```text
create order
```

Often called:

```text
When
```

---

## Assert

Check the result.

Example:

```text
unit price must be 1790
```

Often called:

```text
Then
```

This pattern is commonly called:

```text
Arrange
Act
Assert
```

or:

```text
Given
When
Then
```

---

# 20. A Test Is Executable Documentation

This design statement:

```text
A collection override replaces the default variation price.
```

is useful documentation.

But this test:

```java
assertEquals(
    1790,
    calculatedPrice
);
```

makes the rule executable.

If someone breaks the rule later:

```text
test fails
```

That is why tests are valuable.

---

# 21. Your Menu V2 Acceptance Matrix

After the backend implementation, make a table like this:

| Design rule | Test proving it |
|---|---|
| Backend owns price | Create order integration test |
| Zero override works | Pricing test |
| Override affects only default variation | Pricing test |
| Required SINGLE options are enforced | Option validation test |
| Same variation can have different collection prices | Integration test |
| Category fallback works | Menu API test |
| Overnight schedule works | Availability test |
| V19 snapshot is persisted | PostgreSQL integration test |
| Selected option snapshots persist | Integration test |
| Idempotent replay returns stored result | Order integration test |

This table lets you answer:

```text
How do I know the implementation follows the design?
```

with:

```text
Because every important design invariant has an executable test.
```

---

# 22. Your Immediate Learning Exercise

Before writing application tests, create one tiny JUnit test.

Find an existing backend test directory:

```text
backend/src/test/java/au/com/nakornthai/
```

Study one existing non-empty test.

Look for:

```java
@Test
```

and:

```java
assertEquals(...)
```

Then run:

```bash
cd backend
mvn test
```

Your first goal is simply to understand:

```text
where tests live
how Maven discovers them
how a passing test looks
how a failing test looks
```

Do not worry about Mockito or Playwright yet.

---

# 23. Recommended Learning Plan

## Session 1

Learn:

```text
@Test
assertEquals
assertTrue
assertFalse
```

Run existing tests.

Write one simple test.

---

## Session 2

Read one existing Nakorn Thai service/handler test.

Learn:

```text
Arrange
Act
Assert
```

---

## Session 3

Learn basic Mockito:

```text
@Mock
when(...)
verify(...)
```

---

## Session 4

Run a PostgreSQL integration test.

Understand the difference between:

```text
mock repository
```

and:

```text
real PostgreSQL repository
```

---

## Session 5

Write the first curl smoke test.

---

## Session 6

After backend + frontend are stable, install and learn Playwright.

---

# 24. Commands Cheat Sheet

Backend unit/integration tests:

```bash
cd backend
mvn test
```

Stronger Maven verification:

```bash
mvn verify
```

Frontend tests:

```bash
cd frontend
npm test
```

Frontend production build:

```bash
npm run build
```

API call:

```bash
curl -i http://127.0.0.1:8082/api/menu/collections
```

Pretty JSON:

```bash
curl -s http://127.0.0.1:8082/api/menu/collections | jq
```

Later, Playwright:

```bash
npx playwright test
```

Visible browser:

```bash
npx playwright test --headed
```

---

# 25. One Rule to Remember

Do not ask:

```text
Do I have tests?
```

Ask:

```text
What important behavior does this test prove?
```

A large number of weak tests can give false confidence.

A smaller set of tests tied directly to your design decisions is much more valuable.

---

# 26. Where You Should Start Today

For now:

```text
1. Let Codex finish the backend work.

2. Do not start Playwright yet.

3. Inspect the tests Codex creates.

4. Pick one simple JUnit test and understand every line.

5. Run:
   mvn test

6. Deliberately change one expected value so the test fails.

7. Restore it and make the test pass again.
```

That deliberately failing test is an excellent first exercise.

It teaches you the most important testing concept:

```text
a test is useful only if it can detect something wrong.
```

Once this feels comfortable, move to PostgreSQL integration tests, then curl, then browser E2E testing.
