# Application Trace Map

This guide is a code-navigation aid for the application that currently exists. It was built by following the wiring from `../../frontend/src/main.jsx` and `../../frontend/src/app/AppRouter.jsx`, then matching each frontend request to Spring mappings, security rules, persistence code, entities, and Flyway DDL. A filename is not treated as implemented behavior.

## How a request moves through this application

```text
User action in a hash route (`#/...`)
        ↓
React page/component event handler or effect
        ↓
React hook/context/local state
        ↓
Domain API module (browser `fetch`; there is no active shared HTTP client)
        ↓
HTTP `/api/...` (Vite proxies it to Spring Boot in development)
        ↓
Spring SecurityFilterChain → CSRF and, for staff routes, JwtAuthenticationFilter
        ↓
@RestController mapping
        ↓
validated request record / query parameters
        ↓
@Service use-case/handler, or transactional controller for a few staff operations
        ↓
domain rules and price/status/availability checks
        ↓
Spring Data repository, repository adapter, or EntityManager
        ↓
@Entity persistence model → PostgreSQL `public` tables
        ↓
response record/map/entity → ResponseEntity/HTTP status
        ↓
API decoder → React state/context update → render
```

Application composition is `../../frontend/src/main.jsx` → `App` in `../../frontend/src/app/App.jsx` → `AuthProvider` → `CartProvider` → `AppRouter` plus the global `CartDock`. Routing is a `hashchange` listener in `AppRouter`; React Router is not installed or used. In development, `../../frontend/vite.config.js` proxies `/api` and `/media` to port 8080.

## Security and cross-cutting checkpoints

- `../../backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java`, `securityFilterChain(...)` (`@Configuration`, `@Bean`): permits the explicitly listed public menu, ordering, payment, verification, function, reservation, and availability operations; applies role checks to staff paths; denies everything else. Spring CSRF remains enabled. This is the authoritative authorization layer; `ProtectedRoute` is only a UI guard.
- `../../backend/src/main/java/au/com/nakornthai/shared/security/JwtAuthenticationFilter.java`, `doFilterInternal(...)`: for a Bearer request, verifies the JWT, reads `staff_session` through `SpringDataStaffSessionRepository.findById`, follows its eager `user` relationship to `staff_user`, checks session expiry/revocation and user enablement, and installs `ROLE_ADMIN`, `ROLE_FOH`, or `ROLE_BOH` in the security context. It returns 401 before a controller on failure.
- Frontend staff wrappers call `fetchWithIdentity(...)` in `../../frontend/src/domains/identity/api/identityApi.js`. It refreshes an expiring access token and retries once after 401. Access tokens live in module/React memory; the rotating refresh token is an HttpOnly cookie.
- Browser writes first obtain a CSRF token. Identity uses `/api/identity/csrf`; menu uses `/api/staff/menu/csrf`; orders and payments use `/api/orders/csrf`; reservations and functions use their own `/csrf` endpoints; restaurant administration uses `/api/staff/restaurant/csrf`.
- `../../backend/src/main/java/au/com/nakornthai/shared/observability/CorrelationIdFilter.java` and `LoggingAspect.java` wrap requests/service calls for correlation and logging but do not change feature data flow.

## Tracing index

| Feature | Frontend entry | API endpoint | Controller | Service / use case | Repository / persistence | DB table(s) |
|---|---|---|---|---|---|---|
| Public menu discovery/browse | `MenuPage`, `useMenu` | `GET /api/menu/collections`; `GET /api/menu/collections/{slug}/items` | `ListMenuController` | `ListMenuHandler` | `MenuItemRepository` → `JpaMenuItemRepository` → Spring Data menu repositories | `menu_collection`, `menu_collection_schedule`, `menu_collection_category`, `menu_collection_item`, `menu_category`, `menu_item`, `menu_item_variation`, image/food/option tables |
| Cart add/update/remove | `MenuItemCard`, `Cart`, global `CartDock` | None | None | `createCartLine`, `cartReducer` | Browser `sessionStorage` only | None |
| Place pickup order | `CheckoutPage.place` | `POST /api/orders` | `CreateOrderController` | `CreateOrderHandler` | `EntityManager`, `OrderMapper` | `restaurant_order`, `restaurant_order_item`, `restaurant_order_item_option`, `restaurant_order_event`; reads menu and restaurant schedule tables |
| Customer order status | `OrderConfirmationPage.poll` | `GET /api/orders/{id}` | `GetOrderController` | `GetOrderHandler`, `OrderAccessService` | `SpringDataOrderRepository`, native tracking-grant query | order tables, `order_tracking_grant` |
| Table request | `ReservationPage.submit` | `POST /api/reservations` | `CreateReservationController` | `CreateReservationHandler` | `SpringDataReservationRepository`, advisory lock, restaurant repository | `reservation`; reads restaurant schedule tables |
| Staff reservation queue/update | `ReservationAdminPage` | `GET /api/staff/reservations`; `PATCH /api/staff/reservations/{id}` | `ListReservationsController` | Controller-owned transaction for update | Spring Data read; `EntityManager` locked write | `reservation` |
| Staff authentication/session | `LoginForm.submit`, `AuthProvider` | `POST /api/identity/login`; `/refresh`; `/logout` | `LoginController`, `RefreshTokenController`, `LogoutController` | `LoginHandler`, `JpaUserRepository` | Spring Data plus `EntityManager` | `staff_user`, `staff_session` |
| Staff order queues | `StaffOrdersPage.poll` | `GET /api/staff/foh/orders`; `GET /api/staff/kitchen/orders` | `ListOrdersController` | `ListOrdersHandler` | `SpringDataOrderRepository` | order/item/option tables |
| Staff order transition | `StaffOrdersPage.apply` | `PATCH /api/staff/orders/{id}/status` | `ChangeOrderStatusController` | `ChangeOrderStatusHandler` | `EntityManager` pessimistic lock | `restaurant_order`, `restaurant_order_event` |
| Menu item administration | `StaffMenuPage` | `GET/POST /api/staff/menu/items`; `PUT/DELETE /api/staff/menu/items/{id}` | get/create/update/delete menu controllers | corresponding handlers → `MenuAdminService` | menu Spring Data repositories and `EntityManager` | core menu, variation, and collection-membership tables |
| Menu image administration | `MenuImageEditor.save` | `POST /api/staff/menu/items/{id}/image`; `GET /media/menu/{name}` | `MenuImageController` | `MenuImageService` | `EntityManager` plus media filesystem | `menu_item_image`, `menu_item`; JPEG under configured media directory |
| Collection management | `MenuAdminPage`, collection detail views | `GET/POST /api/staff/menu/collections`; `PUT/DELETE .../collections/{id}`; category/schedule/membership writes | `MenuConfigurationController` | `MenuConfigurationHandler` | `EntityManager` | `menu_collection` and related configuration tables |
| Function enquiry + staff workflow | `FunctionsPage`; `FunctionEnquiriesPage` | `POST /api/functions`; `GET/PATCH /api/staff/functions...` | `CreateFunctionEnquiryController`; `FunctionEnquiriesController` | create handler; transactional controller update | Spring Data and `EntityManager` | `function_enquiry` |
| Staff account administration | `UsersPage` | `GET/POST /api/identity/users`; `PUT .../{id}` | `StaffUsersController` | transactional controller methods | staff/session Spring Data repositories, `EntityManager` | `staff_user`, `staff_session` |
| Payments | `PaymentForm`; `PaymentStatus` | `/api/payments/...`; `/api/staff/payments/...` | `CreatePaymentController` | `CreatePaymentHandler`, `PayPalPaymentProvider` | `EntityManager`, `OrderAccessService` | `restaurant_order`, `order_payment` |
| Tracking recovery | `OrderTrackingPage.submit` | `/api/order-verification/options|start|check` | `OrderVerificationController` | `OrderVerificationHandler`, `TwilioVerifyClient` | `EntityManager`, native rate-limit query | `order_verification`, `order_tracking_grant`, `restaurant_order` |
| Restaurant availability/schedule | `ReservationPage`; `RestaurantSchedulePage` | `GET /api/restaurant/availability`; CRUD `/api/staff/restaurant/...` | availability/opening-hours controllers | `RestaurantAvailabilityService`, `OpeningHoursHandler` | `RestaurantRepository` → `JpaRestaurantRepository`, `EntityManager` | `restaurant_settings`, `restaurant_opening_hours`, `restaurant_closed_date` |

## 1. Customer menu browsing

Route: `#/menu`.

1. `../../frontend/src/app/AppRouter.jsx`, `AppRouter()`, renders `MenuPage` when the hash is `#/menu`. It is called by `App`; its next step is mounting the page.
2. `../../frontend/src/domains/menu/pages/MenuPage.jsx`, `MenuPage()`, calls `useMenu(selectedId)`. Collection buttons/select call local `select(id)`; search input changes `search`; `menuSections(menu, search)` prepares render sections. It also calls `getOrderingOptions()` separately to decide whether Add buttons are enabled.
3. `../../frontend/src/domains/menu/hooks/useMenu.js`, `useMenu(...)` and effect-local `load()`, first call `getMenuCollections(signal)`, choose a collection with `selectCollection(...)`, then call `getMenuCollection(selected.slug, signal)`. The hook owns `{collections, menu, loading, error}` and calls `setState`, causing `MenuPage` to render loading, error, empty, or menu UI.
4. `../../frontend/src/domains/menu/api/menuApi.js`, `getMenuCollections`, `getMenuCollection`, and `menuRequest`, use browser `fetch` through `fetchWithIdentity`. These public requests carry cookies but no Bearer token. They issue `GET /api/menu/collections` and `GET /api/menu/collections/{encodedSlug}/items`, validate basic response shape, and return JSON.
5. `SecurityConfig.securityFilterChain` permits both GET patterns. No staff authentication is required; CSRF does not affect GET.
6. `../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java` (`@RestController`, `@RequestMapping("/api/menu/collections")`): `discover()` (`@GetMapping`) calls `ListMenuHandler.discover()`; `list(slug)` (`@GetMapping("/{slug}/items")`) creates `ListMenuQuery` and calls `handle`.
7. `../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuQuery.java` is the collection-slug input record. The handler explicitly validates its nonblank/length/slug-pattern constraints.
8. `../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java` (`@Service`): `discover()` and `handle(...)` are `@Transactional` because schedule evaluation takes a database consistency lock. They call the `MenuItemRepository` domain interface.
9. `../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java` (`@Repository`):
   - `findPublishedCollections()` calls `SpringDataMenuCollectionRepository.findByStatusOrderByDisplayOrderAscIdAsc`, optionally gets the restaurant schedule, and applies `MenuCatalogRules.availability`.
   - `findVisibleCollection(slug)` calls `findVisibleBySlug`, `SpringDataMenuCollectionItemRepository.findPublishedMemberships`, filters inactive/invalid placement, invokes `MenuItemMapper.map`, and assembles ordered categories.
10. Domain rules are in `menu/domain/CollectionAvailability.java` (`evaluate`, `withRestaurantCutoff`), `menu/infrastructure/MenuCatalogRules.java`, and `menu/domain/MenuPricing.java`. `MenuItemMapper` maps initialized JPA relationships to immutable records in `menu/domain/MenuItem.java`, including variations, images, food declarations, collection placement, and option groups.
11. Principal entities are `MenuCollectionJpaEntity`, `MenuCollectionScheduleJpaEntity`, `MenuCollectionCategoryJpaEntity`, `MenuCollectionItemJpaEntity`, `MenuCategoryJpaEntity`, `MenuItemJpaEntity`, `MenuItemVariationJpaEntity`, `MenuItemImageJpaEntity`, `MenuOptionGroupJpaEntity`, `MenuOptionJpaEntity`, and `MenuItemOptionGroupJpaEntity`; each implemented persistence class is `@Entity`. Food-profile association entities cover allergens and dietary tags.
12. PostgreSQL tables are in the common `public` schema. Core DDL is `V2__create_menu_schema.sql`; image focus is V9; the option/schedule/collection-category model is V17; order collection provenance is V19; later menu content/schema adjustments are V18, V20, and V22. Relationships include collection-item → collection/item, variation/image → item, collection-category → collection/category, item-option-group → item/group, and option → group.
13. `MenuResponse.from(...)` returns collection metadata, availability, categories, and items. `ListMenuController` sends 200 with `no-store`.
14. `menuRequest` decodes JSON; `useMenu.load` shape-checks it and calls `setState`; `MenuPage` re-renders collection navigation and `MenuItemCard` rows. `presentDish`, `menuSections`, and `collectionAvailability` are frontend presentation/filtering only.

## 2. Adding and updating cart items (client-only path)

There is deliberately no backend or database step until checkout.

1. In `MenuItemCard` (`../../frontend/src/domains/menu/components/MenuItemCard.jsx`), variation `<select>` updates local `variationId`; `MenuItemOptions` updates local `selections`. `evaluateOptions(...)` in `../../frontend/src/domains/menu/model/menuOptions.js` validates group cardinality and computes option deltas.
2. Clicking **Add to order** calls the card's `onAdd(line)`. `createCartLine(...)` in `../../frontend/src/domains/ordering/model/cartModel.js` creates a display/offer snapshot containing collection, dish, variation, selected options, unit price, and a stable `configurationKey`.
3. `MenuPage` supplies `onAdd`, which dispatches `{type: 'add', line}` through `useCart()` and updates its `added` live-region message.
4. `CartProvider` in `../../frontend/src/domains/ordering/model/CartContext.jsx` owns `useReducer(cartReducer, ...)`. `cartReducer` in `cartReducer.js` merges an identical configuration, caps quantity at 20, and caps distinct configurations at 30.
5. `Cart` in `../../frontend/src/domains/ordering/components/Cart.jsx` supplies quantity `commit`, increment/decrement, and remove handlers. They dispatch `quantity` or `remove`; `cartTotal` derives the total. `CartDock` consumes the same context and renders globally outside the router.
6. Every cart update triggers the `CartProvider` effect, which calls `serializeCart(cart)` and writes `sessionStorage['nakorn-pickup-cart']`. Initial state calls `restoreCart`. The reducer state update re-renders all `useCart` consumers.
7. These prices are not trusted by the server. `CheckoutPage` refreshes them against the menu, and `CreateOrderHandler` recalculates authoritative prices.

## 3. Customer places a pickup order

Route: `#/checkout`. A detailed editor-following trace appears later; this section names the layers.

- UI/event/state: `CheckoutPage.place(event)` reads controlled name/phone/email/payment/notes fields and `cart` from `CartContext`. `prepareCheckout(cart)` refreshes every referenced collection, rebuilds lines with `createCartLine`, rejects changed display terms, and converts lines through `orderLines` to `{collectionId, variationId, quantity, expectedUnitPriceMinor, selectedOptions}`.
- Retry/idempotency: `CheckoutPage` creates `requestId` and a 64-hex-character `trackingToken`, saves the entire payload under `nakorn-pending-pickup` before network submission, and reuses it. `resumeOrder` first tries a lookup before replaying. This is active behavior, not just a backend facility.
- API: `submitOrder` in `../../frontend/src/domains/ordering/api/orderApi.js` gets `/api/orders/csrf`, then posts JSON to `POST /api/orders`. `request` decodes errors and responses.
- Security: both the CSRF GET and order POST are public in `SecurityConfig`, but the POST must pass Spring CSRF. No JWT is required.
- Controller/input: `CreateOrderController` (`@RestController`, `@RequestMapping("/api/orders")`) has `create(...)` (`@PostMapping`) with `@Valid CreateOrderRequest`; it returns 201. The request record validates identity/contact, 1–30 lines, quantities 1–20, expected nonnegative prices, selected option IDs/quantities, and payment method.
- Use case: `CreateOrderHandler` (`@Service`), `handle(...)` (`@Transactional`), takes a PostgreSQL advisory lock derived from `requestId`, fingerprints the request, safely returns an identical existing order, and rejects a conflicting replay. It checks feature flags and `RestaurantAvailabilityService.schedule()`.
- Domain checks: it takes `MenuCatalogLock.read`, loads variation and collection membership with `EntityManager.find`, checks collection schedule, publication/category/item/variation availability, and calls `MenuPricing.calculate`. The expected price must exactly match. This is the authoritative validation boundary.
- Persistence: the handler builds `OrderJpaEntity` (`@Entity`, `restaurant_order`), `OrderItemJpaEntity` (`restaurant_order_item`) snapshots, nested `OrderItemOptionJpaEntity` (`restaurant_order_item_option`), persists/flushed the aggregate, then persists a `NEW` `OrderEventJpaEntity` (`restaurant_order_event`). It also reads menu tables and restaurant schedule tables. V11 creates base ordering tables, V17 adds option snapshots, and V19 adds collection provenance.
- Response: `OrderMapper.map(order, false)` produces `CreateOrderResponse`; contact values are deliberately null in a customer response. `CheckoutPage.complete(payload)` stores only `{requestId, trackingToken}` in `nakorn-pickup-receipt`, removes pending payload, clears cart context, and navigates to `#/order-confirmation`.
- Resulting UI: `OrderConfirmationPage.poll()` calls `getOrder`, then renders status, snapshots, total, pickup estimate, and `PaymentForm`; it polls every five seconds until completed/cancelled.

## 4. Customer order lookup and tracking recovery

### Original receipt lookup

`OrderConfirmationPage.poll` → `orderApi.getOrder(receipt)` → `GET /api/orders/{id}` with `X-Order-Token` → public security rule → `GetOrderController.get` (`@RestController`, `@GetMapping`) → `GetOrderHandler.handle` (`@Service`, `@Transactional(readOnly=true)`) → `SpringDataOrderRepository.findById` → `OrderAccessService.require` compares the SHA-256 token or queries active `order_tracking_grant` → `OrderMapper` → `CreateOrderResponse` → `setOrder(data)` → status UI re-render.

### Recovering a receipt

`OrderTrackingPage.submit` (`#/track-order`) calls `paymentRequest` for `POST /api/order-verification/start`, then `/check`. `OrderVerificationController` (`@RestController`, request mapping) validates nested `Start`/`Check` records. `OrderVerificationHandler` (`@Service`, transactional methods) reads `restaurant_order`, rate-limits with native queries on `order_verification`, calls `TwilioVerifyClient`, consumes a successful challenge, and persists an expiring `OrderTrackingGrantJpaEntity` in `order_tracking_grant`. The response `{requestId, trackingToken, expiresAt}` is saved as the receipt and navigation returns to confirmation. V16 creates both verification tables. SMS/email availability is configuration-dependent; the UI reports when neither is enabled.

## 5. Reservations

### Customer table request

1. `AppRouter` maps `#/reservations` to `ReservationPage`.
2. `ReservationPage` first calls `getRestaurantAvailability()` to display the configured timezone. Form `submit(e)` constructs `{customerName, phone, partySize, requestedAt, notes}`, keeps a stable `crypto.randomUUID()` for identical retries, and calls `reservationRequest('', {method:'POST', body})`.
3. `reservationApi.js`, `reservationRequest`, gets `/api/reservations/csrf`, then posts to `/api/reservations` using browser `fetchWithIdentity` without a Bearer header.
4. `SecurityConfig` permits the CSRF GET and POST. `CreateReservationController` (`@RestController`, `@RequestMapping`, `@PostMapping`) validates `CreateReservationRequest` and calls the handler.
5. `CreateReservationHandler` (`@Service`, `@Transactional`) takes an advisory idempotency lock, compares a retry with an existing row, loads `RestaurantAvailabilityService.schedule`, validates a future whole-minute local time within 90 days, resolves DST through `RestaurantSchedule.requestedInstant`, and requires the restaurant to be open.
6. It constructs `ReservationJpaEntity` (`@Entity`, `@Table("reservation")`) and calls `SpringDataReservationRepository.saveAndFlush`. V13 creates the table. `requested_at` is a local timestamp; audit times are instants.
7. A 201 map `{reference, message}` returns through `decode`; `setReceipt` replaces the form with the “request received” UI. This is a request, not confirmation.

### Staff reservation queue and transitions

`#/staff/reservations` is UI-guarded for ADMIN/FOH. `ReservationAdminPage` loads `GET /api/staff/reservations?date=...` and updates with `PATCH /api/staff/reservations/{id}` through `reservationRequest` and Bearer auth. `SecurityConfig` enforces ADMIN/FOH; `JwtAuthenticationFilter` validates the live session. `ListReservationsController` (`@RestController`, `@RequestMapping`) reads with the derived Spring Data date-range query. Its `update(...)` is itself `@Transactional`: it obtains a pessimistic `EntityManager` lock, checks `@Version`, enforces REQUESTED/CONFIRMED transition sets, and updates status/note/actor/time. The PATCH is 204; the page immediately reloads and `setRows(...)` re-renders. `ListReservationsHandler.java` and query files are empty and not in this active path.

## 6. Staff authentication

1. Any protected staff route reaches `ProtectedRoute` in `../../frontend/src/domains/identity/components/ProtectedRoute.jsx`. It reads `AuthContext`; while loading it shows a check message, without a user it renders `LoginPage`/`LoginForm`, and it applies a convenience role check before rendering children.
2. `AuthProvider` mounts globally and calls `identity.refreshAccess()` once. A valid HttpOnly refresh cookie restores an in-memory access token; a timer refreshes shortly before expiry.
3. `LoginForm.submit` reads FormData, calls context `login`, and updates busy/error state. `identityApi.login(username,password)` gets CSRF, posts `{username,password}` to `/api/identity/login`, and `publish(data)` notifies the context subscriber.
4. `SecurityConfig` permits login/refresh/logout. `LoginController` (`@RestController`, `@RequestMapping("/api/identity")`, `@PostMapping("/login")`) validates `LoginRequest`, calls `LoginHandler`, sets the refresh cookie, and returns `LoginResponse`.
5. `LoginHandler` (`@Service`) supplies an in-memory, per-process attempt limit and calls `JpaUserRepository.login`.
6. `JpaUserRepository` is an active `@Service` despite its repository name. Its `@Transactional login` calls `SpringDataUserRepository.findByUsername`, performs BCrypt comparison, creates `StaffSessionJpaEntity`, rotates/hash-stores the refresh secret, persists it with `EntityManager`, and issues a JWT with `JwtService`.
7. `UserJpaEntity` and `StaffSessionJpaEntity` are `@Entity` mappings for `staff_user` and `staff_session`; session has an eager many-to-one `user_id` relationship. V12 creates both.
8. Response data `{accessToken, expiresAt, user}` goes to module state and then `AuthContext.setSession`; `ProtectedRoute` re-renders the requested dashboard. Refresh uses `RefreshTokenController` → `JpaUserRepository.refresh` with pessimistic session locking and token rotation. Logout uses `LogoutController` → `JpaUserRepository.logout`, revokes the row, clears the cookie, and clears frontend identity state.

`CurrentUserController.GET /api/identity/me` exists and is secured, but the current frontend does not call it; session restoration uses refresh. The empty `RefreshTokenHandler`, `LogoutHandler`, `CurrentUserHandler`, command, and query files are not active layers.

## 7. Staff viewing orders

Routes: `#/staff/foh` for ADMIN/FOH and `#/staff/kitchen` for ADMIN/BOH (`KitchenDashboardPage` simply renders `<StaffOrdersPage kitchen />`).

1. `StaffOrdersPage` gets `authorization` from `useAuth`. Its effect-local `poll()` calls `getStaffOrders(auth, kitchen, history)` immediately and every five seconds.
2. `orderApi.getStaffOrders` serializes staff reads/writes through `staffQueue`, then calls either `GET /api/staff/foh/orders?history=...` or `GET /api/staff/kitchen/orders` with Bearer auth.
3. Security validates the live JWT session and route role. `ListOrdersController` (`@RestController`) maps `front(...)` and `kitchen()` with `@GetMapping` and calls `ListOrdersHandler.handle(kitchen, history)`.
4. `ListOrdersHandler` (`@Service`, `@Transactional(readOnly=true)`) calls `SpringDataOrderRepository` derived queries with a 200-row page: active FOH statuses, kitchen statuses, or completed/cancelled rows from the last 24 hours.
5. JPA loads `OrderJpaEntity` and its item/option relationships; `OrderMapper.map(o, !kitchen)` includes contact data only for FOH. It returns `List<CreateOrderResponse>`.
6. `StaffOrdersPage.poll` calls `setOrders`, `setUpdated`, and clears stale/error flags. React renders queue cards; FOH also mounts `PaymentStatus` and sees contact/payment data, while kitchen sees preparation data only.

## 8. Staff order workflow/status changes

1. Buttons in `StaffOrdersPage` call `choose(order, status)` and render a confirmation form. `apply(event)` builds `{version, status, pickupMinutes, paymentCollected, reason}` and calls `changeOrderStatus`.
2. `orderApi.changeOrderStatus` serially gets `/api/staff/orders/csrf`, then sends `PATCH /api/staff/orders/{id}/status` with Bearer, CSRF, and JSON.
3. `SecurityConfig` allows ADMIN/FOH/BOH at the coarse route. `ChangeOrderStatusController` (`@RestController`, `@PatchMapping`) validates `ChangeOrderStatusCommand`, passes Spring `Authentication`, and returns 204.
4. `ChangeOrderStatusHandler` (`@Service`, `@Transactional`) additionally enforces action-level roles: BOH/Admin for PREPARING/READY, FOH/Admin otherwise. It locks `OrderJpaEntity` pessimistically, checks optimistic `version`, then enforces `NEW → ACCEPTED → PREPARING → READY → COMPLETED`, with cancellation from active states.
5. Domain checks require pickup minutes on acceptance, verified online payment before acceptance/handover, a cancellation reason, and explicit payment-collected confirmation at completion. It updates the order and persists `OrderEventJpaEntity` with actor/status/time.
6. Although the handler returns `CreateOrderResponse`, the controller discards it and sends 204. `StaffOrdersPage.apply` therefore explicitly reloads via `getStaffOrders`, then updates state and re-renders. V11 owns the order/event tables; status history is retained in `restaurant_order_event`.

## 9. Menu administration

All `#/staff/menu` routes remain ADMIN-only through `ProtectedRoute`; staff menu APIs remain ADMIN-only in `SecurityConfig`.

### Navigation and reads

`AppRouter` uses the existing hash router. `StaffMenuPage` delegates to the menu-owned `MenuAdminPage`, which renders `MenuAdminLayout` and a dedicated list or detail view:

- `#/staff/menu` and `#/staff/menu/items`: `MenuItemList`.
- `#/staff/menu/items/new`: create item.
- `#/staff/menu/items/{id}` or `/{id}/overview|pricing|collections|images`: `MenuItemEditor`.
- `#/staff/menu/collections`: `MenuCollectionList`.
- `#/staff/menu/collections/new`: create collection.
- `#/staff/menu/collections/{id}` or `/{id}/overview|items|categories|availability`: `MenuCollectionDetail`, delegating to one focused section component.

Lists link to resources; local section navigation uses real links with `aria-current`. Refresh preserves the resource/section URL, and browser Back follows hash history. Unknown menu routes show an explicit not-found state. The layout provides compact Items/Collections navigation above the content, breadcrumbs and a main-content skip link, without a sidebar. Tables become labelled rows on narrow screens.

`useMenuAdminData` loads `getStaffMenu` and `getCollectionConfiguration` through the existing menu API wrapper. Returning to a view reloads its data/version. Item reads continue through `GetMenuItemController` → `GetMenuItemHandler` → `MenuAdminService.list` → existing Spring Data repositories. Collection reads continue through `MenuConfigurationController` → `MenuConfigurationHandler.collections`.

`useMenuAdminForm` holds only the current form draft. `menuAdminNavigation` registers active form guards with the existing router: links and browser history ask before discarding unsaved edits; in-flight saves block navigation; browser refresh/close uses `beforeunload`. Successful create navigation is deferred until the completed save has rendered so a stale busy guard cannot block it. Form errors receive focus, and local editors focus their legend.

### Item writes and images

`MenuItemEditor` groups existing capabilities into Overview, Pricing / variations, Collections and Images. It does not add option or food-declaration editors. Name/description edits still trigger the backend dietary-review invalidation behavior.

- POST `/api/staff/menu/items` → `CreateMenuItemController` → `CreateMenuItemHandler` → `MenuAdminService.create`.
- PUT `/api/staff/menu/items/{id}` → `UpdateMenuItemController` → `UpdateMenuItemHandler` → `MenuAdminService.update`.
- DELETE `/api/staff/menu/items/{id}?version=...` → delete controller/handler → `MenuAdminService.archive`, which sets ARCHIVED rather than deleting the row. The detail view confirms archiving explicitly.

The service retains catalog locking, version checks, variation prices, collection membership synchronization and food-review invalidation. Item saves still send the complete existing item DTO; section views preserve fields belonging to other sections.

`MenuImageEditor` retains JPEG/PNG upload, alt text, focus and zoom via POST `/api/staff/menu/items/{id}/image` → `MenuImageController` → `MenuImageService`. Image edits have independent save/cancel and navigation guards. A successful image write cannot be repeated if reloading its preview fails. The server still stores image metadata in `menu_item_image` and normalized JPEG bytes in the configured media directory.

### Collection management

`MenuCollectionOverview` uses the existing POST/PUT collection endpoints for metadata, publication, active state and display order. ARCHIVED status and deactivation remain non-destructive. Availability fields are preserved when saving overview metadata.

`MenuCollectionItems` uses existing PUT/DELETE membership endpoints. Memberships retain canonical item identity, nullable category placement with canonical fallback, nullable price overrides including zero, and collection-specific ordering. Overrides affect only the default/base variation. Confirmation explicitly distinguishes removing a membership from deleting the canonical menu item.

`MenuCollectionCategories` uses existing POST/PUT/DELETE collection category endpoints. In-use placements cannot be removed from the UI; the database foreign key remains authoritative. The UI reuses canonical categories and does not create new ones.

`MenuCollectionAvailability` separates server-evaluated availability from configuration, broad instant bounds via explicitly labelled UTC date/time pickers, daily cutoff and the collection schedule timezone. It links to restaurant scheduling for restaurant hours/closed dates. `MenuScheduleEditor` uses existing schedule POST/PUT/DELETE endpoints and retains weekly/specific-date rules, all-day null time pairs, inclusive start/exclusive end, overnight starting-day semantics, and active state. No rules means unrestricted; all inactive rules means unavailable.

`MenuConfigurationHandler.collections` reads the restaurant-owned schedule before the catalog lock and returns catalog availability, combined collection/restaurant availability, restaurant timezone and open state. Public Main Menu/Lunch Special semantics are unchanged. Combined availability describes collection/restaurant rules, not global ordering flags or individual item eligibility.

All writes refresh CSRF and submit the resource version. A committed write followed by a failed read disables further editing until reload. Conflicts also require reload; validation errors retain the draft. Options, option groups and item assignments retain their existing backend APIs without a staff editor.

### Frontend validation

`npm test` includes menu API contracts and `menuAdminNavigation.test.js` for route identity, creation routes, invalid paths, availability presentation and navigation guards. `npm run test:menu-browser` runs `tests/e2e/menuAdmin.browser.mjs`, a dependency-free Chromium DevTools workflow check with mocked menu/identity APIs. Start Vite on port 5174 and Chromium with `--remote-debugging-port=9223`, or provide `MENU_ADMIN_TEST_URL` and `CHROME_DEBUG_URL`. It exercises real React navigation/forms, save/error handling, membership isolation and responsive layouts without writing to an application database.

## 10. Other implemented end-to-end flows

### Function/venue enquiries

- Public `#/functions`: `FunctionsPage.submit` builds contact/event data plus a stable request UUID → `functionRequest` obtains `/api/functions/csrf` and posts to `/api/functions` → permitted security rules → `CreateFunctionEnquiryController` (`@RestController`, `@RequestMapping`, `@PostMapping`) with `CreateFunctionEnquiryRequest` → `CreateFunctionEnquiryHandler` (`@Service`, `@Transactional`) with advisory idempotency lock/date checks → `SpringDataFunctionEnquiryRepository.saveAndFlush` → `FunctionEnquiryJpaEntity` (`@Entity`) → `function_enquiry` (V15) → receipt map → `setReceipt` → thank-you UI.
- Staff `#/staff/functions`: `FunctionEnquiriesPage` calls GET/PATCH `/api/staff/functions`; ADMIN/FOH security applies. `FunctionEnquiriesController` (`@RestController`) uses paged Spring Data reads and performs its PATCH in a controller-level `@Transactional` method with pessimistic lock/version/transition validation. The page increments `reload`, effect-refetches, and re-renders. No automatic customer notification is sent.

### Staff account administration

`#/staff/users` → `UsersPage` → `usersRequest` → ADMIN-only `/api/identity/users` → `StaffUsersController` (`@RestController`). GET returns sorted `LoginResponse.User` views. POST (`@Transactional`) validates nested `Create`, BCrypt-hashes, and saves `UserJpaEntity`; PUT (`@Transactional`) advisory/pessimistic-locks, validates nested `Update`, protects the last enabled admin, changes role/status/password, and revokes active `StaffSessionJpaEntity` rows. The frontend reloads `users` and re-renders. There is no separate handler in this path.

### Payment initiation/checking

`OrderConfirmationPage` renders `PaymentForm`; `run(start|check)` calls `paymentRequest`, which gets order CSRF and sends `X-Order-Token`. `CreatePaymentController` (`@RestController`) maps options/start/check plus staff check/PayID confirmation. `CreatePaymentHandler` (`@Service`, `@Transactional` operations) locks `restaurant_order`, uses `OrderAccessService`, creates/updates `OrderPaymentJpaEntity`, calls `PayPalPaymentProvider` when enabled, or returns configured PayID details. V16 creates `order_payment` with order ID as FK/PK. `setPayment(result)` re-renders approval/details/paid state. `PaymentStatus` supplies staff reconciliation actions. PayPal and PayID are feature/configuration dependent; refund/webhook-named scaffolds are not evidence of active flows.

### Restaurant schedule

- Public availability: `getRestaurantAvailability` → `GET /api/restaurant/availability` → `RestaurantAvailabilityController.availability` (`@RestController`, `@GetMapping`) → `RestaurantAvailabilityService.schedule` (`@Service`, `@Transactional`) → domain `RestaurantRepository` → `JpaRestaurantRepository` (`@Repository`) → settings/hours/closed-date entities and tables → `{timezone,evaluatedAt,open}`. Reservation UI consumes the timezone; ordering/menu availability reuse the same service server-side.
- ADMIN `#/staff/restaurant`: `RestaurantSchedulePage` handlers call `restaurantRequest` for schedule/settings/hours/closures CRUD. `OpeningHoursController` (`@RestController`, `@RequestMapping`) maps GET/PUT/POST/DELETE; `OpeningHoursHandler` (`@Service`, transactional methods) validates IANA timezone, whole-second windows, versions, duplicates, and uses `JpaRestaurantRepository` plus `EntityManager`. Returned entity JSON is assigned to `schedule` via reload. V21 creates `restaurant_settings`, `restaurant_opening_hours`, and `restaurant_closed_date`.

## Detailed editor trace: customer places an order

Follow these in order with “go to file”/symbol search:

1. Open `../../frontend/src/app/AppRouter.jsx`, `AppRouter()`. Confirm `#/checkout` returns `<CheckoutPage />` and `#/order-confirmation` returns `<OrderConfirmationPage />`.
2. Open `../../frontend/src/domains/ordering/pages/CheckoutPage.jsx`, `CheckoutPage()`. `useCart()` supplies current lines and `dispatch`; local state owns customer/payment fields. The user submits the `<form onSubmit={place}>`.
3. Stay in `CheckoutPage.place(event)`. For a new attempt it calls `prepareCheckout(cart)` before constructing the payload. Note that it persists `PENDING_ORDER` before sending: this makes a lost HTTP response safely recoverable.
4. Open `../../frontend/src/domains/ordering/api/checkoutApi.js`, `prepareCheckout(cart)`. It first calls `orderLines(cart)` for structural validation, then `refreshCartPrices(cart)`.
5. In `refreshCartPrices`, follow `getMenuCollections` and `getMenuCollection` to `../../frontend/src/domains/menu/api/menuApi.js`. These invoke the same public menu endpoints as browsing, so checkout uses a fresh server offer rather than only the stored browser snapshot.
6. Return to `refreshCartPrices`. It matches collection, dish, variation, and selections, checks collection/item availability, and rebuilds each line with `createCartLine`. Back in `prepareCheckout`, `displayedTerms` detects changed price/names/options and forces the customer to review rather than silently accept a change.
7. Open `../../frontend/src/domains/ordering/model/cartModel.js`, `orderLines(cart)`. This is the exact wire conversion: display fields disappear; the server receives `collectionId`, `variationId`, `quantity`, `expectedUnitPriceMinor`, and normalized `{optionId, quantity}` selections.
8. Return to `CheckoutPage.place`. Inspect the final payload: stable `requestId`, random 64-hex `trackingToken`, contact fields, optional email, payment method, notes, and reviewed items. Then follow `submitOrder(payload)`.
9. Open `../../frontend/src/domains/ordering/api/orderApi.js`, `submitOrder`. It GETs `/api/orders/csrf`, then `request('/orders', ...)` sends `POST /api/orders` with JSON, cookies, and the CSRF header.
10. Open `../../backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java`, `securityFilterChain`. Locate the public GET `/api/orders/csrf` and POST `/api/orders` matchers. Authentication is not required, but Spring's enabled CSRF filter validates the token before controller invocation.
11. Open `../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderController.java`, `create(...)`. The `@RestController`/`@RequestMapping("/api/orders")` plus `@PostMapping` form the endpoint. `@Valid @RequestBody` triggers DTO validation; successful output is HTTP 201.
12. Open `CreateOrderRequest.java`. Compare every JSON field with the frontend payload and note nested validation bounds. `Line.configurationKey()` canonicalizes selection order for duplicate/idempotency logic.
13. Open `CreateOrderHandler.java`, `handle(...)` (`@Service`, `@Transactional`). First follow the advisory lock and fingerprint. An already-committed identical `requestId` maps the existing order; a mismatched replay returns conflict.
14. Continue through feature/payment flags and `RestaurantAvailabilityService.schedule()`. Open `restaurant/availability/RestaurantAvailabilityService.java`, then `restaurant/infrastructure/JpaRestaurantRepository.java`, to see the read of `restaurant_settings`, `restaurant_opening_hours`, and `restaurant_closed_date` and the domain `RestaurantSchedule.isOpen` decision.
15. Return to `CreateOrderHandler`. Follow `MenuCatalogLock.read(em)`, then each line's `EntityManager.find(MenuItemVariationJpaEntity...)` and `find(MenuCollectionItemJpaEntity...)`. Navigate into those entity classes to see variation → item and membership → collection/item/category relationships.
16. Open `../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java` and `menu/domain/MenuPricing.java`. Availability is evaluated at one checkout instant; `MenuPricing.calculate` validates option ownership/cardinality/availability, applies a collection override only to the default variation, and uses checked arithmetic.
17. Return to the handler and find `expectedUnitPriceMinor != price.unitPrice()`. This is the server-side anti-stale-price check. Then inspect construction of `OrderJpaEntity`, `OrderItemJpaEntity`, and `OrderItemOptionJpaEntity`: human-readable names and prices are immutable order snapshots, not later menu joins.
18. Open those three classes under `ordering/infrastructure`, plus `OrderEventJpaEntity`. Their `@Entity`/`@Table` annotations map to `restaurant_order`, `restaurant_order_item`, `restaurant_order_item_option`, and `restaurant_order_event`. Parent-to-items and item-to-options use cascade persist; the handler explicitly persists the NEW event.
19. Open `../../backend/src/main/resources/db/migration/V11__create_pickup_ordering.sql`, then V17 and V19. Verify base order/item/event constraints, option snapshots, and collection provenance. Menu tables read during validation originate in V2/V17; schedule tables originate in V21.
20. Back in `CreateOrderHandler`, `em.persist(order); em.flush()` writes the aggregate, then the event is persisted. `OrderMapper.map(order, false)` creates `CreateOrderResponse`; open both `OrderMapper.java` and `CreateOrderResponse.java` to inspect the customer-visible JSON.
21. Return through `CreateOrderController` to `orderApi.request`, which JSON-decodes the 201 response. `CheckoutPage` does not use returned fields directly because its locally held receipt key is the durable capability; it calls `complete(payload)`.
22. In `complete`, observe the state effects: store `{requestId, trackingToken}` in `RECEIPT`, remove `PENDING_ORDER`, dispatch cart `clear`, clear local pending state, and set `window.location.hash = '/order-confirmation'`. `CartProvider` persists the empty cart and subscribed components re-render.
23. Open `OrderConfirmationPage.jsx`. Its effect reads the receipt and calls `getOrder(receipt)` every five seconds. Follow that to `GET /api/orders/{id}` and `GetOrderController` → `GetOrderHandler` → `SpringDataOrderRepository.findById` → `OrderAccessService.require` → `OrderMapper`.
24. The final `setOrder(data)` renders the status label, item snapshots, total, payment UI, and pickup information. Later staff status writes alter `restaurant_order`; the polling lookup is what propagates those changes to the customer's React UI.

## Active versus scaffolded, duplicate, or limited paths

- Active HTTP clients are domain modules using `fetch`. `../../frontend/src/shared/api/httpClient.js`, `app/Providers.jsx`, and `app/routes.js` are empty and unused.
- Empty command/query/handler/response files in several backend slices are scaffolds. The trace above names the actual invoked class even where this skips an intended layer.
- Reservations and function staff updates, plus staff-user CRUD, deliberately contain transactional business logic in controllers. There is no hidden active handler behind their empty handler files.
- `JpaUserRepository` is a `@Service`, not a Spring Data interface; the actual Spring Data interfaces are `SpringDataUserRepository` and `SpringDataStaffSessionRepository`.
- Ordering creation uses `EntityManager` directly; reads use `SpringDataOrderRepository`. Menu public reads use a domain repository adapter; menu administration uses both Spring Data and `EntityManager`. These are active local patterns, not competing duplicate implementations.
- `CreateOrderHandler.fingerprintLines` retains a legacy replay fingerprint for old lines without collection/options. Current frontend `orderLines` always sends collection identity. New orders explicitly reject missing collection IDs, so the legacy branch only assists replay compatibility.
- `CurrentUserController /api/identity/me` is implemented but not called by the frontend. The active restoration path is refresh-token rotation.
- Collection metadata, schedule, category placement and membership CRUD are wired into `StaffMenuPage`. Option-group/option/assignment endpoints remain implemented and secured without a dashboard editor.
- Confirmation-sending files (`SendOrderConfirmationHandler` and command) are empty. Reservation/function pages explicitly state that no automatic notification is sent. Twilio Verify is active only for tracking-access verification when configured.
- Payment provider behavior is gated by configuration. PayPal create/details/capture and staff PayID confirmation are implemented; similarly named webhook/refund or alternative-provider placeholders do not form an active end-to-end path.
- Public media bytes live on the filesystem while metadata lives in PostgreSQL. This is the one mapped feature whose persistence is intentionally split.

## Database migration quick reference

| Migration | Trace relevance |
|---|---|
| `V2__create_menu_schema.sql` | Core menu/category/item/variation/image/collection and food-profile tables |
| `V9__menu_image_focus.sql` | Image focus and zoom metadata |
| `V11__create_pickup_ordering.sql` | Order, order-item snapshot, and order-event tables |
| `V12__create_staff_identity.sql` | Staff users and refresh sessions |
| `V13__create_reservations.sql` | Table requests |
| `V15__create_function_enquiries.sql` | Venue/function enquiries |
| `V16__order_payments_and_tracking_verification.sql` | Payment, verification, tracking grants, order email/payment extensions |
| `V17__add_menu_option_model.sql` | Collection scheduling/categories, option groups/options, selected option snapshots |
| `V19__add_order_item_collection_provenance.sql` | Collection and base/override price snapshots on order items |
| `V21__add_restaurant_scheduling.sql` | Restaurant timezone, opening windows, and closed dates |
| `V22__add_lunch_special_menu.sql` | Current collection/menu data and collection cutoff extension |

All of these are unqualified or explicitly `public` PostgreSQL tables in one common schema. Hibernate validates rather than creates this schema.
