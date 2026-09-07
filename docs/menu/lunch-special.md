# Lunch Special — Phase 5

V22 adds the published, active `lunch-special` collection, a Lunch Special category
and collection placement, ten Lunch-specific canonical items L1–L10, ten memberships
and ten Standard/default AUD 1490 variations. Existing Main Menu items, placements,
prices and option attachments are not modified. UUID literals are the deterministic
version-5 IDs in the supplied V22; memberships and attachments use existing composite
keys. Migration guards verify the expected seed counts and relationships.

## Time and restaurant composition

`menu_collection.daily_cutoff_time` is an optional menu-specific cutoff value.
For a collection with a cutoff, effective availability requires:

1. Existing collection lifecycle, active flag and collection schedules permit ordering.
2. The Phase 4 restaurant schedule is open at the operation instant.
3. The local time in that restaurant schedule's timezone is strictly before the cutoff.

Lunch is seeded with `14:30:00`: 14:29 may be available, while 14:30 and 14:31 are
unavailable even if the restaurant remains open. `CollectionAvailability.withRestaurantCutoff`
composes the existing menu result with `RestaurantSchedule.isOpen(Instant)`.
The restaurant timezone is authoritative, even when `menu_collection.timezone`
differs. Existing weekly/specific-date collection schedules still use the collection
timezone. No restaurant hours, weekdays, closed dates or overnight calculations are
duplicated in the Lunch model, and no artificial midnight-to-14:30 schedule is seeded.

The extra nullable TIME column is necessary because existing collection schedule
rows require paired start/end times. It accepts local times below 24:00; staff writes
accept whole seconds. NULL leaves existing collection availability behavior unchanged.
The seed retains the requested `Australia/Melbourne` collection timezone, but that
column does not govern the daily cutoff. The supplied V22 comments were corrected
to reflect this distinction.

Public collection discovery and menu reads use an injected Clock and a consistent
restaurant schedule snapshot when a cutoff is configured. Their transactions allow
the existing Phase 4 consistency lock; they do not write schedule data. Published
unavailable collections and their dishes remain browsable. `AFTER_CUTOFF` and
`RESTAURANT_CLOSED` availability reasons are rendered by generic customer messages.
No customer code branches on the Lunch slug or calculates the cutoff in the browser.

## Printed data and options

Lunch-specific items isolate their printed names, descriptions, rice inclusions and
item-global options from Main Menu. L1's description intentionally ends at “crushed”;
no missing ingredient is invented. L4–L7 link to the existing GF tag. No allergens,
new dietary claims or changes to existing VG/V interpretation are introduced.
GF mappings retain the existing unverified item-level metadata convention; this phase
does not change the public food-profile policy or infer variation-level claims.

Nine dishes use the Lunch-owned required SINGLE protein group:

| Protein | Delta | Unit price |
| --- | ---: | ---: |
| Beef | 0 | 1490 |
| Chicken | 0 | 1490 |
| Veg & Tofu | 0 | 1490 |
| Prawns | 600 | 2090 |
| Seafood | 800 | 2290 |
| Crispy Pork | 500 | 1990 |
| Fish | 600 | 2090 |

L4 uses a separate required SINGLE group containing only Chicken and Beef. This
follows the individual printed label and is corroborated by the historical V14
import; the broader header does not add other L4 choices. Both groups require
exactly one selection with quantity 1 per dish unit. Existing V17 validation rejects
missing, multiple, duplicated or unrelated options. Two Prawns dishes cost 4180.
No collection price override is needed: each Lunch variation's base is 1490.

## Checkout, cart and administration

`CreateOrderHandler` resolves successful idempotent retries first. For a new order,
it captures one Clock instant and one restaurant schedule snapshot, then reuses
both for restaurant and menu availability. Membership, variation, required options,
current authoritative pricing and stale client price checks remain enforced.
Loading the menu before cutoff does not authorize an order submitted after cutoff.

Successful replays return stored results after cutoff, closure or price/configuration
changes. Cart identity remains collection + variation + normalized options, so Chicken
and Prawns stay separate. Existing snapshots retain collection ID/name/slug, base price,
null Lunch override, selected options/deltas and authoritative unit price.

Existing order error contracts are preserved: `RESTAURANT_CLOSED` retains the Phase 4
409/code response; collection-unavailable and stale-price errors retain their existing
409/message responses, and invalid options retain 400/message responses. This phase
does not introduce competing `INVALID_OPTIONS` or `PRICE_CHANGED` code contracts.

Staff manage Lunch items using the existing menu dashboard. A generic **Collection
availability** editor uses the existing ADMIN-only `/api/staff/menu/collections`
GET/PUT contracts to edit lifecycle, active flag and `dailyCutoffTime`. Writes acquire
fresh CSRF tokens, retain unrelated collection fields and use the current version.
Existing collection, membership and option management APIs continue to apply; there
is no Lunch-specific admin subsystem. Staff must configure real restaurant hours
in Phase 4 and enable existing online-ordering settings before ordering can succeed.

## Validation

Executable tests cover restaurant/collection timezone disagreement, cutoff boundaries,
closed dates, lifecycle and existing schedules, unchanged no-cutoff behavior, public
repository discovery, all protein deltas, required SINGLE validation, L4 restrictions,
checkout after cutoff, historical replay and snapshots, cart identity and admin CSRF/
version contracts. PostgreSQL tests additionally cover V21/V22 history, actual seeded
relationships, Main Menu isolation, prices/options, public API composition, persistence
and admin version checks.

`mvn test` passed. Final `mvn --batch-mode --no-transfer-progress verify` passed and
built the executable JAR. `npm test` passed with 67 tests; `npm run build` passed.
PostgreSQL tests are gated by `DB_TEST_URL`; local `DB_TEST_URL`, `DB_TEST_USERNAME`
and `DB_TEST_PASSWORD` are absent. Skipped tests do not establish V22 PostgreSQL
compatibility. No migration was applied or deployed by this task. AGENTS.md and
V1–V21 were preserved.

## Changed files

- [backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandler.java)
- [backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationRequest.java](../../backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationRequest.java)
- [backend/src/main/java/au/com/nakornthai/menu/domain/CollectionAvailability.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/CollectionAvailability.java)
- [backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java)
- [backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java)
- [backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java)
- [backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java)
- [backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java)
- [backend/src/main/resources/db/migration/V22__add_lunch_special_menu.sql](../../backend/src/main/resources/db/migration/V22__add_lunch_special_menu.sql)
- [backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationApiTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandlerTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationIntegrationTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/domain/LunchSpecialAvailabilityTest.java](../../backend/src/test/java/au/com/nakornthai/menu/domain/LunchSpecialAvailabilityTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/domain/LunchSpecialPricingTest.java](../../backend/src/test/java/au/com/nakornthai/menu/domain/LunchSpecialPricingTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/listmenu/LunchSpecialIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/LunchSpecialIntegrationTest.java)
- [backend/src/test/java/au/com/nakornthai/menu/listmenu/LunchSpecialRepositoryTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/LunchSpecialRepositoryTest.java)
- [backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java)
- [backend/src/test/java/au/com/nakornthai/ordering/createorder/LunchSpecialOrderTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/LunchSpecialOrderTest.java)
- [docs/menu/lunch-special.md](../../docs/menu/lunch-special.md)
- [docs/restaurant/scheduling.md](../../docs/restaurant/scheduling.md)
- [frontend/src/domains/menu/api/menuApi.js](../../frontend/src/domains/menu/api/menuApi.js)
- [frontend/src/domains/menu/api/menuApi.test.js](../../frontend/src/domains/menu/api/menuApi.test.js)
- [frontend/src/domains/menu/components/MenuCollectionAvailabilityEditor.jsx](../../frontend/src/domains/menu/components/MenuCollectionAvailabilityEditor.jsx)
- [frontend/src/domains/menu/model/menuCollections.js](../../frontend/src/domains/menu/model/menuCollections.js)
- [frontend/src/domains/ordering/api/customerPresentation.test.js](../../frontend/src/domains/ordering/api/customerPresentation.test.js)
- [frontend/src/domains/ordering/model/cartModel.test.js](../../frontend/src/domains/ordering/model/cartModel.test.js)
- [frontend/src/domains/staff/pages/StaffMenuPage.jsx](../../frontend/src/domains/staff/pages/StaffMenuPage.jsx)
