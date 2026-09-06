# Menu backend contracts after V17–V19

The backend requires Flyway through V19. Existing menu item/image administration,
order tracking and staff queue/status endpoints remain in place. The customer and
staff frontends have not yet been updated for these contracts.

## Public menus

`GET /api/menu/collections` returns an array, ordered by collection display order
and ID, of `{id, slug, name, description, timezone, displayOrder, availability}`.
Only published collections are returned, including those currently unavailable.

`GET /api/menu/collections/{slug}/items` keeps the existing collection and `items`
fields and adds `timezone`, `availability` and ordered `categories`. Unknown,
draft and archived collections return 404. Unavailable published collections
return 200 with availability false.

Availability is `{available, reason, evaluatedAt}`. Reasons are `AVAILABLE`,
`NOT_PUBLISHED`, `INACTIVE`, `NOT_STARTED`, `ENDED`, `OUTSIDE_SCHEDULE` and
`INVALID_TIMEZONE`. All menu responses use `Cache-Control: no-store`.

Each item additionally contains:

- `category: {id, slug, name, displayOrder}`: collection placement's category,
  falling back to the canonical item category when placement is null.
- `collectionCategoryId`: nullable placement ID.
- `displayOrder`: membership order; `priceOverrideMinor`: nullable membership price.
- `optionGroups`: ordered `{id, code, name, selectionType, active, minSelections,
  maxSelections, displayOrder, options}` records. Options are ordered
  `{id, code, name, priceDeltaMinor, currency, available, displayOrder}` records.

Variation `priceMinor` is now the effective base price in this collection, before
options. `variationBasePriceMinor` exposes the original variation price. Overrides
apply only to default variations, and zero is a valid override. Item/variation
availability also accounts for the collection and whether required groups can be
satisfied. Inactive options remain visible with `available: false`.

Dietary/allergen profiles keep their existing scope and verification rules; option
selection does not imply new dietary claims.

## Order creation and snapshots

`POST /api/orders` retains the existing request-level fields. Each new line is:

```json
{
  "collectionId": "collection UUID",
  "variationId": "variation UUID",
  "quantity": 2,
  "expectedUnitPriceMinor": 3200,
  "selectedOptions": [{"optionId": "option UUID", "quantity": 2}]
}
```

`selectedOptions` may be omitted or null for an empty selection. A collection is
required for every new line; it cannot be inferred from another membership.
Limits remain 30 lines and 1–20 dish units per line. Selections are limited to 100
option IDs per line, each with quantity 1–20 per dish unit. Duplicate option IDs
are rejected. SINGLE requires at most one option of quantity one, plus assignment
min/max requirements; MULTIPLE counts summed quantities toward assignment limits.

The server computes the selected variation base, applies a membership override
only to the default variation, then adds selected option deltas multiplied by
per-unit option quantities. That final unit price is multiplied by dish quantity.
Arithmetic is checked for overflow. Expected price only detects stale prices.

Configuration identity includes collection, variation and option ID/quantity
pairs sorted by option ID. Exact duplicate configurations are rejected; different
configurations of the same variation are accepted. Dish quantities and expected
prices participate in the request fingerprint but not configuration identity.

Every new line stores V19 snapshot version 1, collection ID/name/slug, original
variation price, applied nullable override, dish/variation names and final unit
price. V17 option rows store option ID, group/option names, delta and quantity.
Collection IDs are historical identifiers without a live collection relationship.

Order response lines keep their existing fields and additionally return `id`,
`snapshotVersion`, `collectionId`, `collectionName`, `collectionSlug`,
`variationBasePriceMinor`, `collectionPriceOverrideMinor`, and `selectedOptions`.
Each selected option is `{optionId, optionGroupName, optionName, priceDeltaMinor,
quantity}`. These are snapshots, including in staff queues and tracking responses.
Legacy version-0 lines expose null provenance.

An exact idempotent replay returns the stored order before consulting current
menu availability or the global ordering switch. Legacy payloads without collection
IDs preserve the old fingerprint representation for existing-order replay only;
new orders with that shape return 400. New fingerprints normalize line/option
ordering and include collection IDs.

Malformed selections or missing collection IDs return 400; changed price,
unavailable collection/item or absent membership return 409. Existing status/message
error handling remains. CSRF and tracking-token protections are unchanged.

## Staff configuration

All routes below are under `/api/staff/menu`, require ADMIN, and retain CSRF on
writes. JSON writes return `{id, version, data}`, where `data` has the corresponding
request fields and current version. POST returns 201, PUT 200, DELETE 204.

| Resource | Read | Create/update | Delete behavior |
| --- | --- | --- | --- |
| Collections | GET `/collections` | POST `/collections`, PUT `/collections/{id}` | DELETE `/collections/{id}?version=N` archives |
| Schedules | Nested in collection read | POST `/collections/{collectionId}/schedules`, PUT same path plus `/{id}` | DELETE same path plus `/{id}?version=N` removes |
| Collection categories | Nested in collection read | POST `/collections/{collectionId}/categories`, PUT same path plus `/{id}` | DELETE same path plus `/{id}?version=N` removes; referenced placements conflict |
| Memberships | Nested in collection read | PUT `/collections/{collectionId}/items/{itemId}` | DELETE same path plus `?version=N` removes membership |
| Option groups | GET `/option-groups` | POST `/option-groups`, PUT `/option-groups/{id}` | DELETE `/option-groups/{id}?version=N` deactivates |
| Options | Nested in group read | POST `/option-groups/{groupId}/options`, PUT same path plus `/{id}` | DELETE same path plus `/{id}?version=N` deactivates |
| Item assignments | GET `/items/{itemId}/option-groups` | PUT `/items/{itemId}/option-groups/{groupId}` | DELETE same path plus `?version=N` removes assignment |

Collection reads return `{collection, schedules, categories, memberships}`;
option-group reads return `{group, options}`. Each nested entry uses the resource
wrapper. Membership resource IDs are item IDs; assignment resource IDs are group
IDs. PUT creates a missing membership/assignment only when version is null.

Request fields (all writes include nullable-on-create `version`):

| Resource | Fields |
| --- | --- |
| Collection | `name`, `slug`, `description`, `status`, `active`, `timezone`, `startsAt`, `endsAt`, `displayOrder` |
| Schedule | `ruleType`, `dayOfWeek` (ISO 1–7), `specificDate`, `startTime`, `endTime`, `active`, `displayOrder` |
| Collection category | `categoryId` (existing canonical category), `displayOrder` |
| Membership | nullable `collectionCategoryId`, nullable `priceOverrideMinor`, `displayOrder` |
| Option group | `code`, `name`, `selectionType`, `active` |
| Option | `code`, `name`, `priceDeltaMinor` (AUD), `active`, `displayOrder` |
| Assignment | `minSelections`, `maxSelections`, `displayOrder` |

Updates/deletes require the resource's current version; stale versions return 409.
Parent ownership is checked. SINGLE assignments require maxSelections=1 and
minSelections in 0–1. Group conversion to SINGLE rejects incompatible assignments.
Existing item CRUD preserves retained memberships' category, override and display
order. Membership changes through the new endpoints also advance the item version
so an old dashboard cannot silently overwrite the membership set.

## Scheduling and transaction consistency

Broad instant ranges are start-inclusive and end-exclusive. Weekly and specific-
date schedule rows are additive local-time windows in the collection timezone.
Overnight windows belong to their starting day. Both times null means all day;
equal times or a partial pair are rejected on staff writes. No rows means
unrestricted; rows with none active mean unavailable. Repeated local times at DST
overlap can match both occurrences; nonexistent local times do not occur.

Checkout uses one server instant after acquiring a shared PostgreSQL transaction
advisory catalog lock. Menu pricing/configuration writes acquire its exclusive
counterpart before loading data; checkouts may run concurrently. This convention
protects application writes, not out-of-band SQL changes. Photo-only writes retain
their existing item lock and version behavior.

## Remaining frontend work

Discover collections dynamically; render categories, effective prices and option
selectors; retain collection/configuration identity in carts and persisted pending
checkouts; refresh the selected collection's offer; submit and display options.
Build staff configuration editors against the new routes and resource versions.
The old customer checkout cannot create orders because it omits collection IDs.

## Implementation file inventory

### Backend source

- [au/com/nakornthai/menu/configuremenu/MenuConfigurationController.java](../../backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationController.java)
- [au/com/nakornthai/menu/configuremenu/MenuConfigurationHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandler.java)
- [au/com/nakornthai/menu/configuremenu/MenuConfigurationRequest.java](../../backend/src/main/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationRequest.java)
- [au/com/nakornthai/menu/domain/CollectionAvailability.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/CollectionAvailability.java)
- [au/com/nakornthai/menu/domain/MenuItem.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/MenuItem.java)
- [au/com/nakornthai/menu/domain/MenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/MenuItemRepository.java)
- [au/com/nakornthai/menu/domain/MenuPricing.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/MenuPricing.java)
- [au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java)
- [au/com/nakornthai/menu/infrastructure/MenuAdminService.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuAdminService.java)
- [au/com/nakornthai/menu/infrastructure/MenuCatalogLock.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogLock.java)
- [au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java)
- [au/com/nakornthai/menu/infrastructure/MenuCollectionCategoryJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionCategoryJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuCollectionItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionItemJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuCollectionScheduleJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionScheduleJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuItemMapper.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java)
- [au/com/nakornthai/menu/infrastructure/MenuItemOptionGroupJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemOptionGroupJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuOptionGroupJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuOptionGroupJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuOptionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuOptionJpaEntity.java)
- [au/com/nakornthai/menu/infrastructure/MenuWriteExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuWriteExceptionHandler.java)
- [au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionItemRepository.java)
- [au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java)
- [au/com/nakornthai/menu/infrastructure/SpringDataMenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuItemRepository.java)
- [au/com/nakornthai/menu/listmenu/ListMenuController.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java)
- [au/com/nakornthai/menu/listmenu/ListMenuHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java)
- [au/com/nakornthai/menu/listmenu/MenuResponse.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/MenuResponse.java)
- [au/com/nakornthai/ordering/createorder/CreateOrderHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java)
- [au/com/nakornthai/ordering/createorder/CreateOrderRequest.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderRequest.java)
- [au/com/nakornthai/ordering/createorder/CreateOrderResponse.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderResponse.java)
- [au/com/nakornthai/ordering/infrastructure/OrderItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderItemJpaEntity.java)
- [au/com/nakornthai/ordering/infrastructure/OrderItemOptionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderItemOptionJpaEntity.java)
- [au/com/nakornthai/ordering/infrastructure/OrderMapper.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderMapper.java)
- [au/com/nakornthai/shared/security/SecurityConfig.java](../../backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java)

### Backend tests

- [au/com/nakornthai/menu/configuremenu/MenuConfigurationApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationApiTest.java)
- [au/com/nakornthai/menu/configuremenu/MenuConfigurationHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationHandlerTest.java)
- [au/com/nakornthai/menu/configuremenu/MenuConfigurationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/configuremenu/MenuConfigurationIntegrationTest.java)
- [au/com/nakornthai/menu/createitem/CreateMenuItemHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/menu/createitem/CreateMenuItemHandlerTest.java)
- [au/com/nakornthai/menu/domain/CollectionAvailabilityTest.java](../../backend/src/test/java/au/com/nakornthai/menu/domain/CollectionAvailabilityTest.java)
- [au/com/nakornthai/menu/domain/MenuPricingTest.java](../../backend/src/test/java/au/com/nakornthai/menu/domain/MenuPricingTest.java)
- [au/com/nakornthai/menu/listmenu/ListMenuApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/ListMenuApiTest.java)
- [au/com/nakornthai/menu/listmenu/MenuSchemaIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/MenuSchemaIntegrationTest.java)
- [au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderHandlerTest.java)
- [au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java)

### Documentation

- [menu/backend-menu-v2-api.md](../../docs/menu/backend-menu-v2-api.md)
