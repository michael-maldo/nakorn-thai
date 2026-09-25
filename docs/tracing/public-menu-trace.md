# Public menu browsing: an editor tracing guide

Verified against the current source on 2026-09-25. Start at `#/menu`; finish at the rendered dish cards. This is a source-level trace, not a claim that a local database or running server was inspected. All data examples below use field names/types rather than invented restaurant data.

Read the numbered steps in order. The first HTTP trip discovers collections; only after it returns can the second trip retrieve the selected collection. Each trip has its own request and transaction. A “Calls next” entry can mean a synchronous call, an asynchronous continuation, or a later React render; the text distinguishes them.

For the broader flow, see `application-trace-map.md`, section 1. This guide follows the current source, including V23 item-specific option prices. Three qualifications matter: its mention of `MenuPricing` should not imply that browsing calls it (this read path calculates offer fields in `MenuItemMapper`); V19's order provenance is not a public-menu retrieval dependency; and the table index omits the conditional reads of the three restaurant schedule tables. “Carry cookies” means eligible existing cookies may be sent, not that a cookie must exist.

## Before following the calls

A fresh load of `http://localhost:5173/#/menu` loads the HTML and modules. The fragment `#/menu` is not sent to the HTTP server. Navigating to that hash from an already mounted page instead triggers `hashchange`; it does not rerun `main.jsx` or reload the HTML.

Spring Boot must already be running against a configured development PostgreSQL database. `backend/src/main/java/au/com/nakornthai/NakornThaiApplication.java` bootstraps the application; Spring registers controllers, services, adapters and Spring Data proxies before these requests arrive. `backend/src/main/resources/application.yml` configures the datasource, Flyway and Hibernate (`ddl-auto: validate`, `open-in-view: false`). Migrations run at startup, not on each menu GET.

## Browser startup and React execution

### Step 1 — Load the page entry module

**File**
`frontend/index.html`; `frontend/src/main.jsx`

**Symbol**
HTML module script; module-level `createRoot(...).render(...)`

**Called by**
Browser loads the document on a fresh navigation.

**Input**
DOM element `#root`; URL containing `#/menu`.

**What happens**
The HTML loads `/src/main.jsx`. Its imports load React, App and the three CSS files. `createRoot(document.getElementById('root')).render(<StrictMode><App /></StrictMode>)` gives React ownership of the root.

**Calls next**
React schedules `App()` rendering.

**Output**
A React root, followed by a committed component tree.

**What I should learn here**
Module execution starts the application; JSX describes elements. React calls component functions. StrictMode in development can call render functions extra times and replay effect setup/cleanup; do not count every breakpoint hit as a separate customer action.

**Things to inspect in the debugger**
- `window.location.hash`
- `document.getElementById("root")`
- Network document/module requests

### Step 2 — Compose providers and router

**File**
`frontend/src/app/App.jsx`

**Symbol**
`App()`

**Called by**
React rendering `<App />`.

**Input**
No props.

**What happens**
Returns `<AuthProvider><CartProvider><AppRouter /><CartDock /></CartProvider></AuthProvider>`. The providers pass children through. Menu browsing is not gated on authentication. AuthProvider can make independent restoration requests; these are not menu-fetch prerequisites. Cart context supplies values read by cards, but its implementation is outside this trace.

**Calls next**
React renders the providers, then `AppRouter()`.

**Output**
The component tree containing the router.

**What I should learn here**
Returning `<AppRouter />` describes a child; it is not an ordinary direct call to `AppRouter()`. Context makes values available further down the tree.

**Things to inspect in the debugger**
- React DevTools component tree
- Provider children; no public-route loading guard

### Step 3 — Select the hash route

**File**
`frontend/src/app/AppRouter.jsx`

**Symbol**
`AppRouter()`; effect-local `navigate()`

**Called by**
Initial React render, or the registered `hashchange` listener calling `setHash(...)`.

**Input**
`window.location.hash.split('?')[0]`; persisted `hash` state.

**What happens**
On mount, the effect installs click/hashchange/beforeunload listeners and tracks history position. For normal public browsing the edit guards allow navigation. `navigate()` updates history and calls `setHash`. Another effect scrolls on hash changes. During rendering, `if (hash === '#/menu') return <MenuPage />;`. No React Router or ProtectedRoute participates here. The small `allowMenuNavigation`/`hasMenuEdits` helpers in `frontend/src/domains/menu/model/menuAdminNavigation.js` only affect whether navigation is allowed; they do not load dishes.

**Calls next**
React mounts `MenuPage()`, or reuses it if its type remains the same.

**Output**
A `MenuPage` element.

**What I should learn here**
Effects install listeners after commit. The browser event happens later; setting state schedules another render, which chooses the page.

**Things to inspect in the debugger**
- `hash`, `window.location.hash`
- In `navigate`: `currentUrl`, `targetPosition`
- Route return expression

### Step 4 — Render the initial menu page

**File**
`frontend/src/domains/menu/pages/MenuPage.jsx`

**Symbol**
`MenuPage()`

**Called by**
React mounts the page selected by AppRouter.

**Input**
Initial `selectedId = null`, `search = ""`, `enabled = false`, `orderingAttempt = 0`, `added = ""`.

**What happens**
Calls `useMenu(selectedId)` during every render. Reads cart context through `useCart()`. Initially the hook supplies no menu and `loading: true`, so the page renders its header and loading status. Independently its effect calls `getOrderingOptions()` from `frontend/src/domains/ordering/api/orderApi.js` (`GET /api/orders/options`) and updates `enabled`; failure is ignored. This only controls card actions and the ordering notice, not whether menu data loads.

**Calls next**
`useMenu(selectedId)` now; after commit, the menu-loading effect and the independent options effect.

**Output**
Initial JSX with “Loading our menu…”.

**What I should learn here**
Component bodies run to calculate UI. Fetching in an effect happens after commit. Independent effects need not finish in registration order.

**Things to inspect in the debugger**
- `selectedId`, `search`, hook return values
- `enabled`
- React DevTools hooks

### Step 5 — Initialize the custom hook and schedule loading

**File**
`frontend/src/domains/menu/hooks/useMenu.js`

**Symbol**
`useMenu(selectedId = null)`; `useEffect` callback; nested `load()`

**Called by**
MenuPage calls the hook during render; React later runs the effect.

**Input**
State `{collections: [], menu: null, loading: true, error: "", selectedId}`; `attempt = 0`.

**What happens**
The effect depends on `[attempt, selectedId]`. On mount it creates an AbortController, clears menu/error and sets loading with a functional state update, then calls async `load()`. The function executes synchronously until its first `await`. Cleanup aborts the old request on unmount or dependency change. The hook also compares `state.selectedId === selectedId` so an old collection is hidden immediately while a new effect is pending.

**Calls next**
`load()` → `getMenuCollections(controller.signal)`.

**Output**
Immediately: hook state for rendering. Later: promises that will update state.

**What I should learn here**
A custom hook is ordinary JavaScript using React hooks; it is not a separate component. State survives renders. A new render alone does not rerun an effect whose dependencies did not change.

**Things to inspect in the debugger**
- `state`, `attempt`, `selectedId`, `current`
- In effect: `controller.signal.aborted`
- Break at first line of `load()`

### Learning checkpoint — browser to effect

- How does a fresh load differ from changing only the hash?
- What caused MenuPage and useMenu to execute?
- Why does the loading effect execute on mount but not on every search keystroke?
- Why might development show an aborted request and another request?
- Which state belongs to MenuPage, and which belongs to useMenu?

### Step 6 — Ask for collection discovery

**File**
`frontend/src/domains/menu/api/menuApi.js`

**Symbol**
`getMenuCollections(signal)`

**Called by**
`useMenu` → `load()` at its first await.

**Input**
AbortSignal from the current effect.

**What happens**
Calls `menuRequest('/menu/collections', { signal })` and awaits its decoded result. Array/entry validation happens on the return journey, not before the request.

**Calls next**
`menuRequest(path, options)`.

**Output**
A pending Promise, eventually a validated collection summary array.

**What I should learn here**
An async function always returns a Promise. Await suspends this function, not the browser UI thread.

**Things to inspect in the debugger**
- `signal`
- `collections` after the await

### Step 7 — Construct a public request

**File**
`frontend/src/domains/menu/api/menuApi.js`

**Symbol**
`menuRequest(path, { authorization, csrf, ...options } = {})`

**Called by**
`getMenuCollections`; later `getMenuCollection` reuses this exact wrapper.

**Input**
Path `/menu/collections`; `{signal}`; no authorization, CSRF or body.

**What happens**
Prefixes `/api`. The write-only CSRF branch is skipped. Builds `credentials: 'same-origin'` and `headers: {}` for this GET. With no `method`, browser fetch defaults to GET. The spread retains the AbortSignal.

**Calls next**
`fetchWithIdentity('/api/menu/collections', options)`.

**Output**
A pending Promise for decoded data; it first awaits a Response.

**What I should learn here**
This wrapper owns menu HTTP/error conventions. No active shared HTTP client is used. Headers used for writes are not automatically added to reads.

**Things to inspect in the debugger**
- `path`, `options.signal`
- Outgoing `credentials`, `headers`, absence of body

### Step 8 — Pass directly to browser fetch

**File**
`frontend/src/domains/identity/api/identityApi.js`

**Symbol**
`fetchWithIdentity(url, options = {})`

**Called by**
menuRequest.

**Input**
Public `/api/...` URL and headers without Authorization.

**What happens**
The first branch is the entire relevant path: `if (!options.headers?.Authorization?.startsWith('Bearer ')) return fetch(url, options);`. No token refresh or authenticated retry is invoked for this request.

**Calls next**
Browser-native `fetch(url, options)`.

**Output**
Promise resolving to a browser `Response`, or rejecting on network failure/abort.

**What I should learn here**
An API wrapper named for identity can pass public traffic through unchanged. The next operation crosses a process/network boundary.

**Things to inspect in the debugger**
- `url`, `options`
- The first return branch
- DevTools Network request headers

## Stop here: the HTTP boundary

The browser now sends a request. Frontend JavaScript does **not** call a Java method. Its async continuation waits while a separate backend request runs; other browser JavaScript and rendering remain free to execute.

```http
GET /api/menu/collections
```

After discovery and selection, the same transport sends:

```http
GET /api/menu/collections/{encodeURIComponent(selected.slug)}/items
```

Both requests have no body, no query parameters, no application-supplied Authorization, Content-Type or CSRF header. `credentials: 'same-origin'` permits cookies that already exist and match the browser URL's domain/path/security rules; none are required for this public read. The browser adds its normal transport headers.

With Vite's default development port, the browser requests `http://localhost:5173/api/menu/...` if the page was opened on localhost. `frontend/vite.config.js` proxies `/api` unchanged to `http://127.0.0.1:8080`; `/media` is also proxied there. The browser sees a same-origin URL. The hash is never part of the backend route. If Vite chooses another port, the browser origin changes accordingly.

Success is HTTP 200, `Cache-Control: no-store`, with a JSON body. The collection-list body is an array of `MenuItem.CollectionSummary`; the item body is `MenuResponse`. Here is a **type-shaped example**, not a captured payload or invented database row:

```text
Discovery: [
  { id: UUID-string, slug: string, name: string, description: string|null,
    timezone: string, displayOrder: integer,
    availability: { available: boolean, reason: string, evaluatedAt: ISO-instant-string } }
]

Items: {
  id: UUID-string, slug: string, name: string, description: string|null,
  timezone: string,
  availability: { available: boolean, reason: string, evaluatedAt: ISO-instant-string },
  categories: [{ id: UUID-string, slug: string, name: string, displayOrder: integer }],
  items: [{
    id: UUID-string, slug: string, name: string, description: string,
    available: boolean,
    image: null | { url: string, alt: string, focusX: integer, focusY: integer, zoom: number },
    profileScope: "ITEM" | "VARIATION_REQUIRED",
    profile: FoodProfile|null,
    variations: [{ id: UUID-string, name: string, priceMinor: integer, currency: "AUD",
      available: boolean, defaultVariation: boolean, profile: FoodProfile,
      variationBasePriceMinor: integer }],
    category: { id: UUID-string, slug: string, name: string, displayOrder: integer },
    collectionCategoryId: UUID-string|null, displayOrder: integer,
    priceOverrideMinor: integer|null,
    optionGroups: [{ id: UUID-string, code: string, name: string,
      selectionType: "SINGLE"|"MULTIPLE", active: boolean,
      minSelections: integer, maxSelections: integer, displayOrder: integer,
      options: [{ id: UUID-string, code: string, name: string,
        priceDeltaMinor: integer, currency: "AUD", available: boolean, displayOrder: integer }] }]
  }]
}
FoodProfile = {
  allergenReviewStatus: string, allergenReviewedAt: ISO-instant-string|null,
  dietaryTags: [{ code: string, name: string, notes: string|null, verifiedAt: ISO-instant-string }],
  allergens: [{ code: string, name: string, declaration: string,
    notes: string|null, verifiedAt: ISO-instant-string|null }]
}
```

### Learning checkpoint — HTTP

- Where does the direct JavaScript call chain end?
- Why does discovery have to complete before the item request starts?
- Which origin does the browser contact, and where does Vite forward it?
- Does this public request need a token or a body?

### Step 9 — Allow the public GET through security

**File**
`backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java`

**Symbol**
`SecurityConfig.securityFilterChain(HttpSecurity, JwtAuthenticationFilter)`

**Called by**
Spring calls this @Bean method at startup; the resulting filter chain handles each HTTP request.

**Input**
GET `/api/menu/collections` or `/api/menu/collections/<slug>/items`.

**What happens**
`@Configuration` declares configuration; `@Bean` registers the built SecurityFilterChain. Its GET matchers for `/api/menu/collections` and `/api/menu/collections/*/items` use `permitAll()`. CSRF remains enabled but does not require a token for GET. The installed JwtAuthenticationFilter sees no Bearer header and continues without a session lookup.

**Calls next**
Filter chain proceeds to Spring MVC dispatch and `ListMenuController.discover()` (or `list(slug)` for the second trip).

**Output**
An allowed request; no menu data yet.

**What I should learn here**
Security authorization precedes controller dispatch. This configuration method is not called once per request. URL matching in SecurityConfig and MVC routing are distinct decisions.

**Things to inspect in the debugger**
- Inspect the GET matchers in source
- In controller inspect request URL via debugger/Network; no need to step through security internals

### Step 10 — Dispatch discovery to the controller

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java`

**Symbol**
`ListMenuController.discover()`

**Called by**
Spring MVC matches GET plus class-level `/api/menu/collections` and method-level `@GetMapping`.

**Input**
No arguments or body.

**What happens**
`@RestController` makes returned data an HTTP response body. `@RequestMapping` supplies the shared path. Lombok `@RequiredArgsConstructor` creates a constructor for final `handler`; Spring injects the service. The body expression invokes `handler.discover()` before ResponseEntity is completed.

**Calls next**
`ListMenuHandler.discover()`.

**Output**
Eventually `ResponseEntity<List<MenuItem.CollectionSummary>>`, 200 and no-store.

**What I should learn here**
Spring chooses methods from annotations and constructs dependencies at startup. The controller translates HTTP to a use-case call.

**Things to inspect in the debugger**
- `handler` (possibly a Spring proxy)
- Returned ResponseEntity body and headers

### Step 11 — Start discovery transaction and use the domain contract

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/MenuItemRepository.java`

**Symbol**
`ListMenuHandler.discover()` → `MenuItemRepository.findPublishedCollections()`

**Called by**
Controller calls the injected handler proxy.

**Input**
No arguments.

**What happens**
`@Service` registers the use-case component. `@Transactional` starts/joins a transaction around the call, keeping JPA relationships usable. The comment explains why this is not readOnly: conditional restaurant schedule reads take a consistency lock. The domain interface declares the operation; its empty default implementation is overridden by the injected adapter.

**Calls next**
`JpaMenuItemRepository.findPublishedCollections()`.

**Output**
Eventually a List of immutable CollectionSummary records.

**What I should learn here**
An interface is a contract, not a second database operation. Spring injects its concrete implementation; transaction advice wraps the method.

**Things to inspect in the debugger**
- Runtime class of `repository`
- Call stack from controller to handler to adapter

### Step 12 — Load published collection entities

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java`

**Symbol**
`findPublishedCollections()` → `findByStatusOrderByDisplayOrderAscIdAsc("PUBLISHED")`

**Called by**
Domain-repository call from handler.

**Input**
Injected Clock, Spring Data collection repository and restaurant service.

**What happens**
`@Repository` registers the persistence adapter and enables persistence exception translation. Its @Transactional joins the existing transaction. Captures one `now = clock.instant()`, then calls the Spring Data derived query. `SpringDataMenuCollectionRepository extends JpaRepository<MenuCollectionJpaEntity, UUID>`; Spring generates its implementation and derives the status predicate and ordering from the method name. Hibernate executes SQL against menu_collection and materializes entities. Clock comes from `backend/src/main/java/au/com/nakornthai/shared/config/TimeConfig.java`, normally Clock.systemUTC().

**Calls next**
If any published collection has a cutoff: `RestaurantAvailabilityService.schedule()`; then `MenuCatalogRules.availability(c, now, schedule)` for each collection.

**Output**
Ordered `List<MenuCollectionJpaEntity>` in `published`.

**What I should learn here**
Spring Data enters here. SQL filters PUBLISHED, not availability; unavailable published collections still appear. One captured instant makes this response internally consistent.

**Things to inspect in the debugger**
- `now`, `published`
- Each collection status, active flag, cutoff, displayOrder
- Spring Data call return, using step-over

### Step 13 — Conditionally load the restaurant snapshot

**File**
`backend/src/main/java/au/com/nakornthai/restaurant/availability/RestaurantAvailabilityService.java`; `backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantRepository.java`; `backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/JpaRestaurantRepository.java`

**Symbol**
`RestaurantAvailabilityService.schedule()` → `RestaurantRepository.schedule()` → `JpaRestaurantRepository.schedule()`; `settings`, `hours`, `closedDates`

**Called by**
Discovery if any published collection has dailyCutoffTime; second trip if the selected collection has one.

**Input**
Existing transaction; no HTTP subrequest.

**What happens**
Service delegates through the restaurant interface to its adapter. Adapter calls `settings(LockModeType.PESSIMISTIC_READ)` → `em.find(RestaurantSettingsJpaEntity.class, (short) 1, lock)`, then `hours()` → JPQL query/getResultList and `closedDates()` → JPQL query/getResultList. Constructs RestaurantSchedule from ZoneId, mapped OpeningHours and a set of dates. Missing settings throws 503. This narrow dependency is necessary to explain menu availability.

**Calls next**
Return RestaurantSchedule to the menu adapter; then `MenuCatalogRules.availability(...)`.

**Output**
Snapshot from restaurant_settings, restaurant_opening_hours and restaurant_closed_date.

**What I should learn here**
This branch uses EntityManager, not Spring Data. Pessimistic read locking explains the writable transaction even though this flow does not change rows. These tables have no FK to a menu collection; composition happens in Java.

**Things to inspect in the debugger**
- `lock`, settings timezone, hours and closed dates
- `RestaurantSchedule` return
- Whether cutoff branch is entered at all

### Step 14 — Calculate collection availability

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/CollectionAvailability.java`; `backend/src/main/java/au/com/nakornthai/restaurant/domain/RestaurantSchedule.java`; `backend/src/main/java/au/com/nakornthai/restaurant/domain/OpeningHours.java`

**Symbol**
`MenuCatalogRules.availability(c, now, restaurant)` → `CollectionAvailability.evaluate(...)` → `withRestaurantCutoff(...)`

**Called by**
Adapter maps each discovered collection; reused for selected collection.

**Input**
Status, active, startsAt/endsAt, timezone, schedule entities, captured instant and optional restaurant snapshot.

**What happens**
Maps c.getSchedules() to Rule records (lazy access can cause SQL). evaluate checks publication, active flag, instant bounds, timezone and schedule windows. It invokes private `matches` and `dayMatches`: active weekly/date rules are additive; start is inclusive, end exclusive; overnight windows include the previous date. With no rules, there is no window restriction. withRestaurantCutoff returns early if no cutoff or already unavailable; otherwise calls RestaurantSchedule.isOpen, including closed dates, active hours and OpeningHours.overnight, then compares local restaurant time to cutoff.

**Calls next**
`new CollectionAvailability.Result(available, reason, now)` returns; discovery builds `new MenuItem.CollectionSummary(...)`.

**Output**
Availability result, including reason and evaluatedAt; collections are not removed because available is false.

**What I should learn here**
Availability is calculated server-side, not inferred in React. Database publication determines visibility; clock/schedule rules determine availability to order.

**Things to inspect in the debugger**
- `c.getSchedules()`, `now`, `result`
- `reason`, `rules`, local time
- cutoff and restaurant timezone

### Step 15 — Return and decode discovery

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`; `backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java`; `backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java`; `frontend/src/domains/menu/api/menuApi.js`

**Symbol**
`findPublishedCollections()` return → `discover()` returns → `menuRequest()` continuation → `getMenuCollections()` continuation

**Called by**
Completion of the first database/Java call chain, then receipt of the HTTP response.

**Input**
List of CollectionSummary records; then browser Response.

**What happens**
Adapter returns its stream.toList; handler returns and transaction completes; controller builds 200/no-store ResponseEntity. Spring MVC JSON serialization converts records/UUIDs/Instants to JSON. Browser fetch resolves; menuRequest checks response.ok, then awaits response.json(). getMenuCollections checks Array.isArray and each entry's id, slug, name strings and boolean availability.available.

**Calls next**
Return validated array to suspended `useMenu.load()`.

**Output**
Plain JavaScript objects; no Java class identity remains.

**What I should learn here**
Discovery returns domain summary records directly, with no extra DTO mapper. Response.json asynchronously consumes/parses the body; HTTP errors do not automatically reject fetch.

**Things to inspect in the debugger**
- Network response body/status/cache header
- `response.ok` and `collections` after decoding
- `Array.isArray(collections)`

### Step 16 — Choose the collection

**File**
`frontend/src/domains/menu/hooks/useMenu.js`; `frontend/src/domains/menu/model/menuCollections.js`

**Symbol**
`load()` → `selectCollection(collections, selectedId)`

**Called by**
First await resumes with validated summaries.

**Input**
Summaries sorted by backend; selectedId initially null or an explicit choice.

**What happens**
selectCollection tries matching ID, then first available collection, then collections[0], then null. Selected IDs identify dropdown entries; the next URL uses the selected slug. No setSelectedId occurs for the initial automatic choice. Empty list means no second request, menu stays null and loading eventually becomes false.

**Calls next**
If selected: `getMenuCollection(selected.slug, controller.signal)`; otherwise final hook state update.

**Output**
Chosen summary or null.

**What I should learn here**
Fallbacks use nullish coalescing. Default selection can change with availability; do not assume a fixed main-menu slug.

**Things to inspect in the debugger**
- `collections`, `selectedId`, `selected`
- Why each fallback did/did not match

### Step 17 — Request the selected collection items

**File**
`frontend/src/domains/menu/api/menuApi.js`

**Symbol**
`getMenuCollection(slug, signal)`

**Called by**
Second await in useMenu.load.

**Input**
Selected slug, same effect AbortSignal.

**What happens**
Calls menuRequest with the template path `/menu/collections/${encodeURIComponent(slug)}/items` and `{ signal }`. Repeats the exact menuRequest → fetchWithIdentity → fetch path traced above. The first request has already finished; this is a new HTTP request and backend transaction.

**Calls next**
Browser GET → permitted security chain → `ListMenuController.list(slug)`.

**Output**
Pending Promise for the collection object.

**What I should learn here**
encodeURIComponent protects one URL path segment; selection by UUID and lookup by slug serve different purposes.

**Things to inspect in the debugger**
- `slug`, encoded path and signal
- Network shows discovery finishing before item request begins

### Learning checkpoint — discovery and selection

- Why can an unavailable collection still be selected and browsed?
- Where does a Java List become a JavaScript array?
- What selects the first collection when selectedId is null?
- When does the menu request read restaurant tables?
- Why are discovery and items not one transaction?

### Step 18 — Bind the slug and construct a query record

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java`; `backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuQuery.java`

**Symbol**
`ListMenuController.list(@PathVariable String slug)`; `new ListMenuQuery(slug)`

**Called by**
MVC matches GET `/api/menu/collections/{slug}/items`.

**Input**
Path variable string; no request body.

**What happens**
@GetMapping combines with the class mapping; @PathVariable binds the path segment. Controller constructs the Java record `ListMenuQuery(String collectionSlug)`. Its constraints are @NotBlank, @Size(max = 180), @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*"). Construction alone does not run validation.

**Calls next**
`ListMenuHandler.handle(new ListMenuQuery(slug))`.

**Output**
A query object passed into the handler; later a ResponseEntity<MenuResponse>.

**What I should learn here**
A request query record can carry validated input without being JSON-deserialized. Path binding, record construction and validation are separate steps.

**Things to inspect in the debugger**
- `slug`
- Query `collectionSlug()`
- No @RequestBody here

### Step 19 — Validate the query and request the collection

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/MenuItemRepository.java`

**Symbol**
`ListMenuHandler.handle(ListMenuQuery query)`; `MenuItemRepository.findVisibleCollection(String slug)`

**Called by**
Controller calls the transactional service proxy.

**Input**
Query record; injected Jakarta Validator and repository.

**What happens**
Explicitly tests query null or nonempty validator.validate(query); invalid input throws ResponseStatusException(NOT_FOUND). Otherwise calls repository.findVisibleCollection(query.collectionSlug()). Its return Optional will later map through MenuResponse::from or throw 404.

**Calls next**
`JpaMenuItemRepository.findVisibleCollection(slug)`.

**Output**
Eventually MenuResponse, or 404.

**What I should learn here**
@Transactional covers entity access and DTO mapping. @Service does not validate automatically: the explicit validator call does. Optional expresses that a published collection might not exist.

**Things to inspect in the debugger**
- `query`, constraint violations
- `repository` runtime type
- Optional result on return

### Step 20 — Find the collection with explicit JPQL

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java`

**Symbol**
`JpaMenuItemRepository.findVisibleCollection(slug)` → `SpringDataMenuCollectionRepository.findVisibleBySlug(slug)`

**Called by**
Handler via the domain interface.

**Input**
Slug; newly captured `now`.

**What happens**
Spring Data executes the @Query: `select c from MenuCollectionJpaEntity c where c.slug = :slug and c.status = 'PUBLISHED'`. This is JPQL naming Java entities/properties, not native SQL naming tables. Hibernate translates it to PostgreSQL SQL and binds the slug. Optional.empty skips the adapter lambda.

**Calls next**
For a found collection: optional `restaurant.schedule()` and `MenuCatalogRules.availability(...)`, already traced; then memberships query.

**Output**
Optional<MenuCollectionJpaEntity>; one entity `c` inside the mapping lambda.

**What I should learn here**
An explicit repository query differs from the derived discovery query. Neither filters collection active/schedule at SQL level.

**Things to inspect in the debugger**
- `slug`, `now`, Optional presence
- `c.id`, status, schedules, cutoff
- Compare this evaluatedAt with discovery: it can differ

### Step 21 — Load published membership rows and joined categories

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionItemRepository.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`

**Symbol**
`findPublishedMemberships(UUID collectionId)`

**Called by**
Adapter after calculating selected collection availability.

**Input**
`c.getId()`.

**What happens**
Executes explicit JPQL with `join fetch m.menuItem i join fetch i.category`, `left join fetch m.collectionCategory cc left join fetch cc.category`, `where m.collection.id = :collectionId and i.status = 'PUBLISHED' order by m.displayOrder, i.id`. Hibernate returns memberships with their joined item and category objects. Other associations are still lazy.

**Calls next**
Adapter stream filters, including `MenuCollectionItemJpaEntity.effectiveCategory()`.

**Output**
List<MenuCollectionItemJpaEntity>.

**What I should learn here**
Membership gives collection-specific placement/price/order. Fetch joins load chosen associations together; this is not a call to SpringDataMenuItemRepository.

**Things to inspect in the debugger**
- collectionId and membership list
- `m.menuItem`, canonical category, optional collectionCategory
- Query text and parameters

### Step 22 — Resolve placement and filter memberships

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionItemJpaEntity.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`

**Symbol**
`MenuCollectionItemJpaEntity.effectiveCategory()`; stream predicates in `findVisibleCollection`

**Called by**
Adapter streams fetched memberships.

**Input**
Membership, canonical item category, optional collection category.

**What happens**
effectiveCategory returns collectionCategory.getCategory() when present, else menuItem.getCategory(). Adapter removes rows whose effective category is inactive and rows whose explicit placement belongs to another collection. It does not remove unavailable dishes. Membership uses @EmbeddedId MenuAssociationId (collection_id/menu_item_id); @MapsId connects each key part to its @ManyToOne relation.

**Calls next**
`MenuItemMapper.map(m, availability.available())` for each retained membership.

**Output**
Filtered membership stream.

**What I should learn here**
SQL publication filtering and Java category filtering happen at different boundaries. Composite identity models a many-to-many association with its own fields.

**Things to inspect in the debugger**
- `m.id`, effective category active flag
- collection IDs on both sides
- `priceOverrideMinor`, membership displayOrder

### Step 23 — Materialize mapped entity relationships

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemJpaEntity.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemVariationJpaEntity.java`; `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java`

**Symbol**
JPA entity getters used by `MenuItemMapper` and `MenuCatalogRules`

**Called by**
Hibernate materializes query results; subsequent getters/stream traversal initialize needed lazy relations.

**Input**
Database rows and association foreign keys.

**What happens**
@Entity marks managed persistence types; @Table maps SQL names; @Column maps fields. @ManyToOne(fetch = LAZY) and default-lazy @OneToMany map links. @BatchSize(size = 64) can batch related reads. MenuUuidJpaEntity supplies @Id UUID; MenuAuditJpaEntity supplies version/audit fields via @MappedSuperclass. These base classes are not independent tables. Traversing variations, images, profiles, option groups and assignment option-price maps may execute additional SQL during mapping, within the transaction. `MenuItemOptionGroupJpaEntity.optionPrices` is a lazy `@ElementCollection` map (`Map<UUID, Long>`) with `@BatchSize(size = 64)`, stored in `menu_item_option_price`; it has no separate entity or repository. The map joins on the assignment’s item/group key and uses `option_id` as its map key.

**Calls next**
Mapper getters → Hibernate/JDBC → PostgreSQL as needed → populated relationships → mapper resumes.

**Output**
Managed entities/lists/proxies, then initialized data.

**What I should learn here**
There is no single universal “database step”: the main query and lazy initialization can interleave with mapping. Exact statement count depends on loaded state and batching. open-in-view=false makes in-transaction mapping significant.

**Things to inspect in the debugger**
- Relationship initialization state before/after traversal
- Entity ID vs domain record ID
- Avoid expanding every lazy collection in debugger: expansion itself may trigger SQL

### Learning checkpoint — backend to persistence

- How does Spring choose list rather than discover?
- Where is ListMenuQuery actually validated?
- How do MenuItemRepository, JpaMenuItemRepository and SpringDataMenuCollectionRepository differ?
- Which classes are JPA entities and which are records?
- At what calls can PostgreSQL be queried beyond the initial repository query?
- Why is the mapping inside a transaction?

### Step 24 — Map the base dish and active variations

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/MenuItem.java`

**Symbol**
`MenuItemMapper.map(MenuCollectionItemJpaEntity, boolean)` → `map(MenuItemJpaEntity)`

**Called by**
One retained membership from adapter stream.

**Input**
membership.getMenuItem(); calculated collection availability.

**What happens**
@Component makes the mapper injectable. Its constructor normalizes the configured media-base URL with a trailing slash. The membership overload calls map(item). That overload filters inactive variations, sorts displayOrder then UUID string, builds Variation records (available = item.available && variation.available), and calls variationProfile(v). It selects the primary image and combines mediaBaseUrl + storageKey. Returns a base MenuItem; if no active variations, uses scope ITEM and itemProfile; otherwise VARIATION_REQUIRED and null item-level profile.

**Calls next**
`variationProfile(v)` or `itemProfile(item)` as needed, then returns base to membership overload.

**Output**
Immutable base MenuItem with variations and primary image, before collection-specific enrichment.

**What I should learn here**
Mapping is explicit representation conversion. Entity audit/status/SKU fields are not copied into the public record. A mapper overload can call another overload to reuse base conversion.

**Things to inspect in the debugger**
- `item`, `variations`, `image`, `base`
- Active vs available variation flags
- `mediaBaseUrl`, storageKey, constructed URL

### Step 25 — Map food profiles without inheriting claims

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/MenuItem.java`

**Symbol**
`variationProfile(MenuItemVariationJpaEntity)`; conditional `itemProfile(MenuItemJpaEntity)`

**Called by**
Base mapper constructs each active variation, or a no-active-variation item profile.

**Input**
Dietary/allergen associations and their reference entities.

**What happens**
Dietary badges require verifiedAt != null and active dietary tag; sort by tag order then UUID string. Allergens sort by reference order/UUID and copy declaration, notes and verification time, without that dietary filter. Profiles copy review status/time. A variation profile comes from that variation, never inherited item claims. Traversing associations can load food tables.

**Calls next**
Construct `MenuItem.FoodProfile`, `Badge`, `Allergen`; return to base mapper.

**Output**
Records included in response, though current public card does not render food-profile badges.

**What I should learn here**
API data need not all be displayed. Filtering verification claims is different from hiding an entire dish.

**Things to inspect in the debugger**
- Tag verifiedAt/active
- Allergen declaration and review status
- Variation profile vs item profile

### Step 26 — Enrich the dish with collection offers and options

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java`

**Symbol**
`map(MenuCollectionItemJpaEntity membership, boolean collectionAvailable)` after `base = map(item)`

**Called by**
Return from base mapping.

**Input**
Base MenuItem, effectiveCategory, item option-group assignments, override price.

**What happens**
Sorts assignments by displayOrder/group ID. Builds groups with assignment min/max/order and group identity/type/active; sorts options by displayOrder/ID. Includes inactive and unpriced options but marks an option available only if the group and option are active **and** this assignment’s `optionPrices` contains its option ID. `priceDeltaMinor` comes from `a.getOptionPrices().getOrDefault(o.getId(), 0L)`, not from the reusable option entity. A missing price produces zero in JSON with `available: false`; an explicit zero price can be available. Prices belong to the item/group assignment, so two dishes sharing a group can have different deltas. Computes configurable from required-group feasibility (SINGLE requires a possible one-choice selection; MULTIPLE counts available options × 20). Item available combines collection, base item, category and configurable. Rebuilds variations: default variation alone gets a nonnull membership price override; variationBasePriceMinor preserves original price; availability combines enriched item and base variation. Category order comes from placement if present; dish displayOrder comes from membership.

**Calls next**
`new MenuItem(...)` returns to adapter stream.

**Output**
Final public MenuItem with category, placement ID, variations, options and offer fields.

**What I should learn here**
A row flag is not necessarily the final API availability. Calculation and renaming occur here; MenuPricing.calculate is not called on this path.

**Things to inspect in the debugger**
- `a.getOptionPrices()`, option ID and `containsKey` versus an explicit zero value
- `groups`, `configurable`, `available`
- Base and overridden variation prices
- Membership vs canonical displayOrder
- Returned record

### Step 27 — Sort dishes and construct the collection read model

**File**
`backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`; `backend/src/main/java/au/com/nakornthai/menu/domain/MenuItem.java`

**Symbol**
`findVisibleCollection` mapping lambda; `new MenuItem.Collection(...)`

**Called by**
All membership mappings completed.

**Input**
Mapped dishes; collection categories and metadata.

**What happens**
Sorts dishes by category displayOrder, category UUID, dish displayOrder, dish UUID. Builds LinkedHashMap keyed by category UUID from active collection category placements; then dishes add missing categories with putIfAbsent. Sorts category values by order/UUID. Explicit categories can therefore exist with no dishes. Constructs MenuItem.Collection containing metadata, items, timezone, availability and categories, wrapped in Optional.

**Calls next**
Return to `ListMenuHandler.handle`; Optional.map invokes `MenuResponse.from`.

**Output**
Optional<MenuItem.Collection>, fully detached from needing JPA getters for JSON.

**What I should learn here**
Grouping metadata and item order are assembled in Java. Domain read records are immutable containers, not JPA-managed entities.

**Things to inspect in the debugger**
- `dishes`, category map before/after putIfAbsent
- Collection record and final ordering

### Step 28 — Create the HTTP response DTO

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java`; `backend/src/main/java/au/com/nakornthai/menu/listmenu/MenuResponse.java`

**Symbol**
`MenuResponse.from(MenuItem.Collection collection)`

**Called by**
Handler Optional.map(MenuResponse::from).

**Input**
Domain Collection record.

**What happens**
Copies id, slug, name, description, items, timezone, availability and categories into MenuResponse. There is no second per-item DTO conversion: MenuResponse directly contains List<MenuItem>. An empty Optional instead throws 404. Returning from handler completes its transaction before controller response serialization.

**Calls next**
Return DTO to `ListMenuController.list`.

**Output**
MenuResponse record.

**What I should learn here**
Domain-to-DTO conversion is shallow here. DTO construction is not JSON serialization; it still produces a Java object.

**Things to inspect in the debugger**
- `collection` and resulting response
- `items` retained as domain records
- Empty Optional branch if slug is not found

### Step 29 — Serialize the response and cross back to the browser

**File**
`backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java`

**Symbol**
`list(...)` return: `ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(...)`

**Called by**
Handler returns MenuResponse.

**Input**
Java DTO graph with no entity references.

**What happens**
Controller returns ResponseEntity; Spring MVC uses its JSON message converter to serialize the record graph and write HTTP 200/no-store. UUIDs become strings; Instants become timestamp strings; booleans/numbers/lists become JSON values. No hand-written serialization method is invoked in application code.

**Calls next**
Browser fetch resolves its Response; `fetchWithIdentity` resolves; `menuRequest` resumes after await.

**Output**
HTTP JSON bytes, then a browser Response object (body not yet parsed).

**What I should learn here**
Returning a Java object, serializing it, transporting bytes and parsing JSON are separate operations. Do not try to step directly from a Java return into a browser stack.

**Things to inspect in the debugger**
- DTO immediately before controller return
- DevTools Network response body/headers
- Compare Java types with JSON types

### Step 30 — Decode HTTP success or failure

**File**
`frontend/src/domains/menu/api/menuApi.js`

**Symbol**
`menuRequest` after `await fetchWithIdentity(...)`

**Called by**
Item response arrives in the browser.

**Input**
Response with status, headers, unread body.

**What happens**
If !response.ok, attempts response.json().message, otherwise chooses its status message/fallback; throws Error with status. Network errors already reject the await. A 204 returns null (not the normal menu success). A successful body is parsed with `await response.json()`; parse failure throws an invalid-response Error.

**Calls next**
Return through `getMenuCollection` to suspended `useMenu.load()`.

**Output**
Plain JavaScript collection object, or rejected Promise.

**What I should learn here**
Fetch resolves for HTTP 404/500, so explicit response.ok matters. JSON parsing does not instantiate Java classes or validate the full schema.

**Things to inspect in the debugger**
- `response.status`, `response.ok`
- Decoded JSON; do not consume response.json twice
- Error message/status on failure

### Step 31 — Validate and publish the loaded menu to React state

**File**
`frontend/src/domains/menu/hooks/useMenu.js`

**Symbol**
`load()` after its second await; `setState(...)`; load().catch callback

**Called by**
getMenuCollection resolves or rejects.

**Input**
Chosen summary and parsed menu.

**What happens**
If menu is truthy, checks ID matches selection, items/categories are arrays and availability.available is boolean. It does not deeply validate every nested item. If not aborted, updates collections (replacing selected summary availability with fresher detail availability), menu, loading:false, error:"", selectedId. On rejection while not aborted, clears collections/menu and sets error.message. Aborted loads do not publish stale results.

**Calls next**
React schedules another `MenuPage()` render; useMenu returns updated state.

**Output**
New state snapshot with completed data or error.

**What I should learn here**
A setState call requests a render; it does not rewrite the currently executing function's state variable. Multiple updates may be batched, so do not assume an exact render count.

**Things to inspect in the debugger**
- `menu.id` vs `selected.id`
- `controller.signal.aborted`
- State payload and updated summary availability
- Error handler when offline or response invalid

### Learning checkpoint — return journey

- Where does a JPA entity become a MenuItem record?
- Where does a Collection become MenuResponse, and which nested types are reused?
- Where does Java become JSON, and JSON become a JavaScript object?
- Which checks occur after parsing?
- What prevents an old request from overwriting a new selection?
- What actually causes React to render the dishes?

### Step 32 — Render loaded state and derive sections

**File**
`frontend/src/domains/menu/pages/MenuPage.jsx`; `frontend/src/domains/menu/model/menuCollections.js`

**Symbol**
`MenuPage()` → `menuSections(menu, search)`; conditional `collectionAvailability(menu)`

**Called by**
React rerender after hook state update.

**Input**
Loaded menu, summaries, search string, independently loaded enabled state.

**What happens**
useMenu returns persisted state; unchanged dependencies mean no new request. menuSections trims/lowercases search, maps categories, filters items by category ID and name/description substring, then sorts by displayOrder and ID localeCompare. Page reduces item counts, hides empty sections, displays collection metadata/navigation, and calls collectionAvailability for a label if unavailable. That helper maps backend reason codes to text; it does not reevaluate schedules.

**Calls next**
Nested `sections...map` creates `<MenuItemCard item={item} collection={menu} enabled={enabled} cart={cart} onAdd={...} />`.

**Output**
JSX for navigation, status, categories and dish cards.

**What I should learn here**
Filtering/grouping/search here is client presentation logic. Changing search changes local state and UI without HTTP. Server rules have already determined the offered data/availability.

**Things to inspect in the debugger**
- `menu`, `search`, `sections`, `count`
- Item category ID matches section ID
- Conditional loading/error/empty/unavailable UI

### Step 33 — Pass item props and initialize each card

**File**
`frontend/src/domains/menu/pages/MenuPage.jsx`; `frontend/src/domains/menu/components/MenuItemCard.jsx`

**Symbol**
`MenuItemCard({ item, collection, enabled, cart, onAdd })`

**Called by**
React renders elements produced by MenuPage item mapping.

**Input**
One JavaScript item object, full collection, enabled boolean and existing cart context.

**What happens**
Key is `${menu.id}:${item.id}:${menu.availability.evaluatedAt}`. Calls presentDish(item). useState lazy initializer picks default variation ID, otherwise first variation ID, otherwise empty string; selections starts []. Finds the selected variation, evaluates options and derives orderable from enabled plus collection/item/variation availability. A refreshed evaluatedAt changes the key and can remount the card, resetting local choices.

**Calls next**
`presentDish(item)` and `evaluateOptions(item.optionGroups, selections)`, then card display calculations.

**Output**
Card state and derived display values.

**What I should learn here**
Props flow downward; each card owns its selection state. Keys determine component identity, and a useState initializer runs on mount rather than every update.

**Things to inspect in the debugger**
- `item`, `collection`, `variationId`, `variation`, `selections`
- `orderable` and `enabled`
- Key before/after refresh

### Step 34 — Choose presentation image metadata

**File**
`frontend/src/domains/menu/model/menuModel.js`

**Symbol**
`presentDish(item)`

**Called by**
MenuItemCard render.

**Input**
API item, optional API image object; local photo map keyed by specific item UUIDs.

**What happens**
Copies item fields and presentation fallback metadata. Prefers item.image.url over local bundled photo, alt over item name; if API image exists, computes focus percentages, transform origin and scale from its metadata. Names, descriptions and visibility remain from API.

**Calls next**
Returns `dish` to MenuItemCard.

**Output**
Presentation object where `dish.image` is a URL string, whereas original item.image was an object/null.

**What I should learn here**
Object spread and derived properties can change a field's representation without mutating the input. This is presentation, not menu persistence.

**Things to inspect in the debugger**
- Original `item.image` vs `dish.image`
- `imageAlt`, `imagePosition`, `imageOrigin`, `imageScale`
- Whether a fallback photo exists

### Step 35 — Compute the visible option and price state

**File**
`frontend/src/domains/menu/model/menuOptions.js`; `frontend/src/domains/menu/components/MenuItemCard.jsx`; `frontend/src/domains/ordering/model/cartModel.js`

**Symbol**
`evaluateOptions` → `normalizeSelectedOptions`; card-local `createCartLine(...)` call when valid

**Called by**
Card rendering, even before any Add click.

**Input**
Option groups, initially empty selections, chosen variation.

**What happens**
evaluateOptions normalizes selections, checks group limits/availability and accumulates deltaMinor; required groups can make empty selection invalid. If variation exists and evaluation.valid, card calls createCartLine, which reevaluates options, validates price integers and uses configurationKey → normalizeSelectedOptions to build a prospective line. Card catches errors as display messages and looks for an existing matching configuration to derive limit text. This pure calculation is mentioned only because rendering calls it; no cart mutation or order submission happens here.

**Calls next**
`money(...)` formatting and `<MenuItemOptions ... />` rendering.

**Output**
`evaluation`, optional `line`, `problem`, `limit` and displayed price.

**What I should learn here**
A render can call ordinary pure business helpers. Computation now is different from event-handler execution later; the Add handler is only registered.

**Things to inspect in the debugger**
- `evaluation.valid`, message and deltaMinor
- `line?.unitPriceMinor`, `problem`
- Initially required options may prevent line construction

### Step 36 — Render option controls and commit dish UI

**File**
`frontend/src/domains/menu/components/MenuItemOptions.jsx`; `frontend/src/domains/menu/components/MenuItemCard.jsx`; `frontend/src/domains/ordering/model/cartReducer.js`

**Symbol**
`MenuItemOptions(...)`; `money(minor)`; `MenuItemCard` JSX return

**Called by**
React renders card and nested option component.

**Input**
dish presentation fields, variation, groups, selections, disabled state, computed price.

**What happens**
money divides integer minor units by 100 and formats en-AU AUD. MenuItemOptions maps groups into SINGLE selects or MULTIPLE quantity inputs, marks required/optional, calculates selected totals and disables unavailable controls. Unpriced options remain visible as zero-price, unavailable choices; the client uses the server’s `available` flag rather than treating zero as permission to select. Card renders article, optional lazy image, h3 name, description, unavailable label, variation prices, option UI and base+options total. With no variation it displays “Ask us for pricing.” React reconciles/commits DOM; browser paints. Image URLs may cause separate lazy image GETs; image bytes are not inside menu JSON. Header/Footer are public-site chrome, not menu-data transformation.

**Calls next**
Browser paints DOM; later user events can update local state. The requested initial browsing trace ends here.

**Output**
Visible menu item cards.

**What I should learn here**
JSX expressions calculate text/attributes; JSX event callbacks wait for events. HTML/CSS and image loading are presentation. Current card does not display every profile field returned by backend.

**Things to inspect in the debugger**
- Elements panel article/h3/select/price text
- Actual img src/style and image network request
- React DevTools props/state vs DOM text

### Step 37 — Understand later collection selection, retry and search

**File**
`frontend/src/domains/menu/pages/MenuPage.jsx`; `frontend/src/domains/menu/hooks/useMenu.js`

**Symbol**
`select(id)`; `reload()`; returned `retry()`; search onChange callback

**Called by**
User chooses collection, clicks refresh/reload, or types after initial rendering.

**Input**
Chosen ID, click or input value.

**What happens**
select sets selectedId and clears search/added; changed selectedId triggers effect cleanup then a fresh discovery and items sequence. reload resets enabled, increments orderingAttempt, and retry increments hook attempt, refreshing independent options and menu requests. Search only setSearch: menuSections recomputes using loaded items. There is no menu polling timer; availability is a snapshot until a new request.

**Calls next**
Selection/retry returns to loading effect; search returns to MenuPage derivation only.

**Output**
Updated menu or filtered UI.

**What I should learn here**
Dependency-driven effects explain WHEN fetching recurs. A state change can rerender without any network request.

**Things to inspect in the debugger**
- selectedId vs state.selectedId during transition
- attempt / orderingAttempt
- Network remains unchanged while typing search

### Learning checkpoint — final rendering

- How does the item object reach MenuItemCard?
- Which image representation does presentDish change?
- Why can an item show a price while its Add action is disabled?
- What state change fetches a different collection, and what state change only filters locally?
- What happens to card state when evaluatedAt changes its React key?
- Which returned fields are not currently displayed?

## Database access map

Arrows below denote parent-to-child rows unless labeled as references. These are the actual table names from the DDL and entity mappings, in the common PostgreSQL schema (`public` in the migrations that qualify it). They are not separate schemas per domain.

```text
menu_collection
+-- menu_collection_schedule                 (collection_id)
+-- menu_collection_category                 (collection_id; category_id -> menu_category)
+-- menu_collection_item                     (collection_id, menu_item_id composite PK)
    |   collection_category_id -> menu_collection_category (optional)
    +-- references menu_item                 (menu_item_id)
        |   category_id -> menu_category     (canonical category)
        +-- menu_item_variation              (menu_item_id)
        |   +-- menu_item_variation_dietary_tag -> dietary_tag
        |   +-- menu_item_variation_allergen    -> allergen
        +-- menu_item_image                  (menu_item_id)
        +-- menu_item_dietary_tag             -> dietary_tag
        +-- menu_item_allergen                -> allergen
        +-- menu_item_option_group           (menu_item_id, option_group_id composite PK)
            +-- menu_item_option_price       (menu_item_id, option_group_id, option_id PK)
            |   (option_id, option_group_id) -> menu_option
            +-- references menu_option_group
                +-- menu_option              (option_group_id; reusable choice, no price)

Conditional availability dependency (Java composition, no menu FK):
restaurant_settings       singleton id = 1, timezone, consistency lock
restaurant_opening_hours  weekday/time windows
restaurant_closed_date    excluded local dates
```

The entity classes in the following table are each located at the exact path formed by `backend/src/main/java/au/com/nakornthai/menu/infrastructure/` + class name + `.java`. The loading paths are important: the existence of a Spring Data interface for a table does not mean this request calls it.

| Table | Meaning and relevant keys | JPA entity class | Actual loading path in this slice |
| --- | --- | --- | --- |
| `menu_collection` | Named public menu; UUID `id` PK, unique `slug` | `MenuCollectionJpaEntity` | `SpringDataMenuCollectionRepository.findByStatusOrderByDisplayOrderAscIdAsc` / `findVisibleBySlug` |
| `menu_collection_schedule` | Weekly/date windows; UUID `id` PK, `collection_id` FK | `MenuCollectionScheduleJpaEntity` | `c.getSchedules()` traversal in `MenuCatalogRules.availability` |
| `menu_collection_category` | Category placement/order for a collection; UUID `id` PK; `collection_id`, `category_id` FKs; unique pair | `MenuCollectionCategoryJpaEntity` | Fetch-joined membership placement; also `c.getCategories()` for response categories |
| `menu_collection_item` | Membership and contextual price/order; PK `(collection_id, menu_item_id)`, both FKs; optional `collection_category_id` FK | `MenuCollectionItemJpaEntity` | `SpringDataMenuCollectionItemRepository.findPublishedMemberships` |
| `menu_category` | Reusable category; UUID `id` PK, unique slug | `MenuCategoryJpaEntity` | Fetch joins for canonical/effective category; collection-category traversal |
| `menu_item` | Dish name/description/publication and base flags; UUID `id` PK, `category_id` FK | `MenuItemJpaEntity` | Membership query fetch-joins `m.menuItem`; no separate SpringDataMenuItemRepository call |
| `menu_item_variation` | Dish variation and base AUD price; UUID `id` PK, `menu_item_id` FK | `MenuItemVariationJpaEntity` | `item.getVariations()` in base mapper |
| `menu_item_image` | Stored image metadata; UUID `id` PK, `menu_item_id` FK; at most one primary per item via partial unique index | `MenuItemImageJpaEntity` | `item.getImages()`; mapper selects primary only |
| `menu_item_option_group` | Assignment and selection limits/order; composite PK/FKs `(menu_item_id, option_group_id)` | `MenuItemOptionGroupJpaEntity` | `item.getOptionGroups()` |
| `menu_item_option_price` | Item-specific delta; PK `(menu_item_id, option_group_id, option_id)`; composite FKs to assignment and option/group | `MenuItemOptionGroupJpaEntity.optionPrices` (`@ElementCollection`, no separate entity) | `assignment.getOptionPrices()` during option mapping; lazy map with batch size 64 |
| `menu_option_group` | Named SINGLE/MULTIPLE group; UUID `id` PK, unique code | `MenuOptionGroupJpaEntity` | `assignment.getOptionGroup()` |
| `menu_option` | Reusable option name/currency/active flag (no price column); UUID `id` PK, `option_group_id` FK | `MenuOptionJpaEntity` | `group.getOptions()` |
| `menu_item_dietary_tag` | Verified item tag claim; composite PK/FKs `(menu_item_id, dietary_tag_id)` | `MenuItemDietaryTagJpaEntity` | `itemProfile` → `item.getDietaryTags()` when no active variations |
| `menu_item_allergen` | Item declaration and verification; composite PK/FKs `(menu_item_id, allergen_id)` | `MenuItemAllergenJpaEntity` | `itemProfile` → `item.getAllergens()` when no active variations |
| `menu_item_variation_dietary_tag` | Variation-specific tag claim; composite PK/FKs `(variation_id, dietary_tag_id)` | `MenuItemVariationDietaryTagJpaEntity` | `variationProfile` → `variation.getDietaryTags()` |
| `menu_item_variation_allergen` | Variation-specific declaration; composite PK/FKs `(variation_id, allergen_id)` | `MenuItemVariationAllergenJpaEntity` | `variationProfile` → `variation.getAllergens()` |
| `dietary_tag` | Reference tag label/code/active/order; UUID `id` PK, unique code | `DietaryTagJpaEntity` | Association `getDietaryTag()` during profile mapping |
| `allergen` | Reference allergen label/code/order; UUID `id` PK, unique code | `AllergenJpaEntity` | Association `getAllergen()` during profile mapping |

The three conditional entity files use the directory `backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/`:

| Table | Meaning and keys | Entity file | Actual loading call |
| --- | --- | --- | --- |
| `restaurant_settings` | Business timezone, SMALLINT PK `id` constrained to 1; no FK | `RestaurantSettingsJpaEntity.java` | `JpaRestaurantRepository.settings` → `EntityManager.find(..., PESSIMISTIC_READ)` |
| `restaurant_opening_hours` | Active local weekly windows, UUID `id` PK; no FK | `OpeningHoursJpaEntity.java` | `JpaRestaurantRepository.hours` → explicit JPQL `from OpeningHoursJpaEntity order by dayOfWeek, displayOrder, opensAt, id` |
| `restaurant_closed_date` | Closed local dates, UUID `id` PK, unique `closed_date`; no FK | `ClosedDateJpaEntity.java` | `JpaRestaurantRepository.closedDates` → explicit JPQL `from ClosedDateJpaEntity order by closedDate` |

Relevant migrations, all under `backend/src/main/resources/db/migration/`:

- `V2__create_menu_schema.sql`: core categories, items, variations, images, collections, membership and food-profile tables/constraints.
- `V9__menu_image_focus.sql`: focus/zoom metadata consumed by mapper and presentation helper.
- `V17__add_menu_option_model.sql`: collection active/timezone/schedules, collection category placement, membership price override and option model. Its unrelated order snapshot table is not read here.
- `V21__add_restaurant_scheduling.sql`: the conditional restaurant snapshot tables.
- `V22__add_lunch_special_menu.sql`: daily cutoff extension plus lunch content. V18/V20 and other seed/import migrations affect which rows exist; they are not runtime calls or proof of current database contents.
- `V23__price_options_per_menu_item.sql`: creates `menu_item_option_price`, copies previous global option prices into each existing assignment, then drops `menu_option.price_delta_minor`. Composite foreign keys keep prices tied to both the assigned group and its choices. Subsequent missing prices mean unavailable choices, not inherited global prices.

`@Query` in the two menu Spring Data interfaces is **explicit JPQL**, not literal PostgreSQL SQL. Discovery's method name is a **derived query**. Restaurant hours/dates use **explicit JPQL via EntityManager**. Hibernate generates SQL for all these queries, lazy association loads and the settings lock. This slice has no application-written native SQL read query. PostgreSQL executes generated statements and returns rows; Hibernate reconstructs entities and relationships. Flyway DDL is explicit SQL, executed separately at startup.

No fixed query count is promised. For example, profile mapping is conditional on active variations, and `@BatchSize` can combine lazy loads. A breakpoint/debugger expansion can initialize a relation earlier than normal. Capture actual SQL in a local debug session if statement order/count is the question.

### Learning checkpoint — tables and mappings

- Why is menu_collection_item more than a bare join table?
- Which category is canonical, and which can override placement?
- Which foreign keys connect an item to its option groups and item-specific prices?
- Why is a missing option price different from an explicit zero price?
- Why are some food-profile tables only conditionally traversed?
- Which queries are derived, which are JPQL, and who generates SQL?

## One-item data transformation map

Choose **one retained published membership with an active variation** in your development response. Call its IDs `collectionId`, `itemId`, `variationId`; these are symbols, not fabricated UUIDs. If the database is empty, this example describes the shape only. Use the no-variation branch in the mapper steps if that is what your actual data contains.

| Boundary | Representation of this same item | Transformation to inspect |
| --- | --- | --- |
| PostgreSQL | `menu_item.id UUID`, `name VARCHAR`, `description TEXT`, `is_available BOOLEAN`, `status`; related `menu_item_variation.price_minor BIGINT`; membership `display_order INTEGER`, `price_override_minor BIGINT NULL` | Separate normalized rows joined by IDs; canonical category and collection placement are distinct |
| JPA | `MenuCollectionItemJpaEntity` → `MenuItemJpaEntity`; variation `Long priceMinor`, UUID IDs, entity association lists | Column names map to Java fields/getters; lazy objects are initialized when needed; published-item query and effective-category predicates filter rows |
| Base domain mapping | `MenuItem` and `MenuItem.Variation` records, `long priceMinor`; `Image` record | Inactive variations discarded; active variations sorted; primary image selected; storage key becomes URL; entity status/audit/version/SKU discarded |
| Collection mapping | Final `MenuItem` with `Category`, `collectionCategoryId`, membership `displayOrder`, `optionGroups` | Effective placement replaces canonical category when supplied; default variation price overridden if applicable; original price retained as `variationBasePriceMinor`; availability calculated from several inputs; profiles filtered; option deltas read from assignment price maps; inactive or unpriced options retained with available=false |
| Collection model | `MenuItem.Collection.items(): List<MenuItem>` | Item sorted with siblings; categories deduplicated by UUID and ordered |
| Response DTO | `MenuResponse.items(): List<MenuItem>` | Collection envelope copied; same nested domain records reused |
| HTTP JSON | `items[index].id` string, `variations[index].priceMinor` number, nested object/array fields | Serialization removes Java type identity; timestamps become strings; JSON property names stay camelCase |
| Browser object | `menu.items[index]` plain JS object after `response.json()` | No automatic Date conversion; minor units remain numbers; hook validates only parts of response shape |
| React state and sections | `state.menu` → `menuSections(menu, search)` → `section.items[index]` | Categories group items; search may filter this item from visible UI; per-section order recalculated |
| React props | `<MenuItemCard item={item} collection={menu} ... />` | Object passed as prop; selected variation ID held separately in card state |
| Presentation object | `dish = presentDish(item)` | `image` object becomes URL string; fallback photo/focus/alt/scale chosen; item name/description preserved |
| DOM | `<h3>{dish.name}</h3>`, description, variation `<option>`, formatted price, option fieldsets | `money(priceMinor)` formats integer AUD minor units; undisplayed API fields remain data, not DOM |

For the chosen active variation, the price transformation is exactly:

```text
original = MenuItemVariationJpaEntity.getPriceMinor()
effective = defaultVariation && membership.priceOverrideMinor != null
              ? membership.priceOverrideMinor : original
response variation.priceMinor = effective
response variation.variationBasePriceMinor = original
visible base price = money(effective)
visible configured total = effective + evaluation.deltaMinor (formatted with money)
```

For an option in an assigned group, the corresponding transformation is:

```text
assignment.optionPrices[optionId] = menu_item_option_price.price_delta_minor
response option.priceDeltaMinor = assignment.optionPrices.getOrDefault(optionId, 0)
response option.available = group.active && option.active
                            && assignment.optionPrices.containsKey(optionId)
selected option contribution = option.priceDeltaMinor * selected quantity
```

`evaluateOptions` rejects unavailable selections and accumulates valid contributions into `deltaMinor`. If a required group has no feasible priced choices, the backend also marks the dish and its variations unavailable. No global option price fallback remains after V23.

This follows an offered price through browsing. It does not claim the browser can authorize a payment or validate a future purchase. The current card does not render `profile`, `profileScope`, `variationBasePriceMinor` or every placement field, even though they traveled through JSON.

## Execution sequence diagram

```text
Customer             Browser / React                     Spring Boot / Java                  PostgreSQL
   |                         |                                   |                               |
   | fresh /#/menu --------->| index.html -> main.jsx             |                               |
   |                         | createRoot.render -> App           |                               |
   | hash navigation ------>| AppRouter.navigate -> setHash      |                               |
   |                         | AppRouter -> MenuPage              |                               |
   |                         | useMenu(selectedId), initial JSX   |                               |
   |                         | commit -> effect -> load()         |                               |
   |                         | getMenuCollections(signal)         |                               |
   |                         | menuRequest -> fetchWithIdentity   |                               |
   |                         | browser fetch                      |                               |
   |                         |-- GET /api/menu/collections ------>| SecurityFilterChain permits    |
   |                         |      (Vite proxy in dev)           | ListMenuController.discover    |
   |                         |                                   | ListMenuHandler.discover [tx]  |
   |                         |                                   | MenuItemRepository contract    |
   |                         |                                   | JpaMenuItemRepository          |
   |                         |                                   | .findPublishedCollections      |
   |                         |                                   | SpringDataMenuCollectionRepo*  |
   |                         |                                   | .findByStatusOrderBy... ------>| collections
   |                         |                                   |<------------------------------| entities
   |                         |                                   | optional restaurant.schedule ->| settings lock, hours, dates
   |                         |                                   | MenuCatalogRules.availability ->| lazy schedules as needed
   |                         |                                   | CollectionAvailability rules   |
   |                         |                                   | CollectionSummary list         |
   |                         |<-- 200/no-store JSON --------------| handler -> controller -> JSON  |
   |                         | menuRequest: response.json()       |                               |
   |                         | getMenuCollections validates       |                               |
   |                         | load -> selectCollection           |                               |
   |                         | getMenuCollection(selected.slug)   |                               |
   |                         | menuRequest -> fetchWithIdentity   |                               |
   |                         | GET /api/menu/collections/         |                               |
   |                         |     {slug}/items ---------------->|                               |
   |                         |                                   | SecurityFilterChain permits    |
   |                         |                                   | ListMenuController.list(slug)  |
   |                         |                                   | new ListMenuQuery(slug)        |
   |                         |                                   | ListMenuHandler.handle [tx]    |
   |                         |                                   | Validator.validate             |
   |                         |                                   | MenuItemRepository contract    |
   |                         |                                   | JpaMenuItemRepository          |
   |                         |                                   | .findVisibleCollection         |
   |                         |                                   | SpringDataMenuCollectionRepo*  |
   |                         |                                   | .findVisibleBySlug ----------->| collection
   |                         |                                   |<------------------------------| entity
   |                         |                                   | optional restaurant.schedule   |
   |                         |                                   | MenuCatalogRules.availability  |
   |                         |                                   | SpringDataMenuCollectionItemRepository
   |                         |                                   | .findPublishedMemberships ---->| joins
   |                         |                                   |<------------------------------| entities
   |                         |                                   | effectiveCategory / filters    |
   |                         |                                   | MenuItemMapper.map(m, bool)    |
   |                         |                                   |   -> map(item) --------------->| lazy relations as needed
   |                         |                                   |<------------------------------| variations/images/profiles
   |                         |                                   |   -> profile helpers          |
   |                         |                                   |   -> option groups/prices ---->| lazy assignment price maps
   |                         |                                   |<------------------------------| option data and item deltas
   |                         |                                   |   -> enriched MenuItem records|
   |                         |                                   | sort/categories -> Collection |
   |                         |                                   | handler -> MenuResponse.from  |
   |                         |                                   | transaction completes         |
   |                         |<-- 200/no-store JSON --------------| controller -> MVC serializer   |
   |                         | fetch Response -> menuRequest      |                               |
   |                         | response.json -> getMenuCollection |                               |
   |                         | useMenu.load validates -> setState |                               |
   |                         | React rerender MenuPage            |                               |
   |                         | menuSections -> MenuItemCard       |                               |
   |                         | presentDish / evaluateOptions      |                               |
   |                         | price helpers / MenuItemOptions    |                               |
   |<-- visible dish cards --| React commit -> browser paint      |                               |
```

`SpringDataMenuCollectionRepo*` abbreviates **SpringDataMenuCollectionRepository** only to fit the diagram. The item HTTP request terminates at Spring Boot; only generated SQL travels from Hibernate/JDBC to PostgreSQL. Discovery and item retrieval are sequential; the independent ordering-options effect is deliberately outside this diagram because it does not load the menu. Within a mapping call, lazy-load SQL may interleave with Java calculations.

# Manual Debugging Exercise

Trace one **item GET** end to end, with discovery as its prerequisite. This exercise does not require stepping into React, Spring, Hibernate or JDBC implementation code. No runtime debugger session was performed to produce this guide.

Use a development database with the migrations applied and some published menu content. Start the Java application in your IDE's Debug mode with Java 21, its configured development profile and trusted DB environment variables. The application does not automatically load `.env.dev`. Start Vite from `frontend/` with `npm run dev` after installing the locked dependencies if needed. Open the actual Vite URL at `#/menu`. Use browser DevTools Sources and Network, plus React DevTools if available. Vite development sources make the original JSX/modules practical breakpoint targets.

Set the following breakpoints before reload. For long Java streams, put the breakpoint on the enclosing expression or lambda line and step over library methods. If discovery is paused at a shared mapping/rules breakpoint, continue until `ListMenuController.list` is reached to focus on the one item request.

1. **Browser entry.** File: `frontend/src/main.jsx`. Symbol: module-level `createRoot(...).render(...)`. Break on that expression and reload the document. Inspect root DOM node and `window.location.hash`. Continue: React starts rendering App; this breakpoint will not fire for a later hash-only navigation.

2. **Route decision.** File: `frontend/src/app/AppRouter.jsx`. Symbol: `AppRouter()`. Break on `if (hash === '#/menu') return <MenuPage />;`. Inspect hash. Continue: React renders MenuPage. Optionally break in effect-local `navigate` on setHash and navigate away/back to distinguish browser event from rendering.

3. **Initial page/hook.** File: `frontend/src/domains/menu/pages/MenuPage.jsx`. Symbol: `MenuPage()`. Break on `const { collections, menu, loading, error, retry } = useMenu(selectedId);`. Inspect selectedId, then step over and inspect returned values. Continue: initial loading JSX commits before requests complete.

4. **Effect begins.** File: `frontend/src/domains/menu/hooks/useMenu.js`. Symbol: useEffect callback / `load()`. Break on `const collections = await getMenuCollections(controller.signal);`. Inspect selectedId, attempt, signal. Continue: discovery starts; the browser is not blocked by await. StrictMode may abort/restart an initial effect in development.

5. **Discovery returned and selection chosen.** File: `frontend/src/domains/menu/hooks/useMenu.js`. Symbol: `load()`. Break on `const menu = selected ? await getMenuCollection(...) : null;`. Inspect collections and selected (id, slug, availability). Continue: this is the one item request you will follow. If selected is null, there is no item request; supply published development data rather than inventing a slug.

6. **URL construction.** File: `frontend/src/domains/menu/api/menuApi.js`. Symbol: `getMenuCollection`. Break on its menuRequest call. Inspect slug and signal. Continue into menuRequest; inspect path and generated options at `const response = await fetchWithIdentity(...)`.

7. **Browser network boundary.** File: `frontend/src/domains/identity/api/identityApi.js`. Symbol: `fetchWithIdentity`. Break on its first conditional return; restrict to URLs ending `/items` if convenient. Inspect url and options.headers/credentials/signal. Continue: browser sends the GET. Network should show no body or application Authorization header. Switch to the Java debugger; this is not a shared call stack.

8. **Controller ingress.** File: `backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java`. Symbol: `list(String slug)`. Break on the return expression calling handler.handle. Inspect slug and injected handler. Continue/step into handler: ListMenuQuery is constructed and Spring's service proxy enters a transaction. SecurityConfig's matching permitAll rule can be read in source; its bean method is a startup breakpoint, not a per-request breakpoint.

9. **Query validation.** File: `backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java`. Symbol: `handle(ListMenuQuery query)`. Break at the validation `if`, then the repository call. Inspect query.collectionSlug and repository runtime class. Continue: valid input reaches JpaMenuItemRepository. Do not step through the Jakarta validator internals.

10. **Collection query.** File: `backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`. Symbol: `findVisibleCollection`. Break at `collections.findVisibleBySlug(slug)` and inside its `map(c -> ...)` lambda. Inspect slug, now and c fields. Step over the Spring Data call. Continue: collection availability is evaluated. Read the exact JPQL in `backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java`; that interface has no hand-written method body to step through.

11. **Conditional restaurant read.** File: `backend/src/main/java/au/com/nakornthai/restaurant/infrastructure/JpaRestaurantRepository.java`. Symbol: `schedule()`. Break at `settings(LockModeType.PESSIMISTIC_READ)`. This is hit only when the selected collection has a cutoff (and may have been hit by discovery). Inspect settings timezone and returned RestaurantSchedule after stepping over settings/hours/closedDates. Continue: return to menu availability rules. A missing hit is expected for a no-cutoff collection.

12. **Availability calculation.** File: `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCatalogRules.java`. Symbol: three-argument `availability`. Break at `CollectionAvailability.evaluate(...)`, inspect c and now, then step into `backend/src/main/java/au/com/nakornthai/menu/domain/CollectionAvailability.java`, `evaluate` / `withRestaurantCutoff`. Inspect reason, result and cutoff. Continue: result is used to map item availability; false does not remove all dishes.

13. **Membership query and filter.** File: `backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`. Symbol: `findVisibleCollection` lambda. Break at `memberships.findPublishedMemberships(c.getId())` and a filter/map lambda. Inspect membership/effective category. Read query in `backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionItemRepository.java`, step over its proxy call. Continue: each retained membership reaches MenuItemMapper.

14. **Entity-to-record conversion.** File: `backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java`. Symbol: `map(MenuCollectionItemJpaEntity, boolean)`, then `map(MenuItemJpaEntity)`. Break on `var base = map(item)` and final return. Choose one real item ID and optionally condition the breakpoint on it. Inspect entity variation prices/flags, image, profile result, then base, assignment optionPrices, groups, configurable, available and final variations. Compare a missing price entry with an explicit zero: both serialize a zero delta, but only the explicit entry can enable an otherwise active option. Continue: the adapter receives a MenuItem record. Expanding lazy associations can cause SQL; inspect intentionally.

15. **Collection assembly.** File: `backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java`. Symbol: `findVisibleCollection` lambda. Break at `return new MenuItem.Collection(...)`. Inspect dishes and category map; find your chosen item. Continue: handler maps the Optional value to MenuResponse.

16. **DTO conversion and HTTP return.** File: `backend/src/main/java/au/com/nakornthai/menu/listmenu/MenuResponse.java`. Symbol: `from(MenuItem.Collection)`. Break on new MenuResponse. Inspect collection.items and the chosen item. Continue through handler/controller: transaction completes, Spring serializes response. Return to browser; Network now shows status/JSON. No breakpoint inside Jackson is necessary.

17. **JSON decoding.** File: `frontend/src/domains/menu/api/menuApi.js`. Symbol: `menuRequest`. Break at `if (!response.ok)` or `try { return await response.json(); }` for the items response. Inspect status/ok. Continue: parsed data returns to hook. Do not manually call response.json in the console and then expect the application to read the consumed body again.

18. **State update.** File: `frontend/src/domains/menu/hooks/useMenu.js`. Symbol: `load()`. Break on the menu shape-validation `if` and success `setState`. Inspect selected.id, menu.id, menu.items, menu.availability and signal.aborted. Continue: React schedules MenuPage rendering; it does not rerun main.jsx. If signal was aborted, focus on the succeeding active load.

19. **Grouping and props.** File: `frontend/src/domains/menu/model/menuCollections.js`. Symbol: `menuSections`. Break on query/categories calculation. Inspect menu/search, then returned sections. Continue to `frontend/src/domains/menu/components/MenuItemCard.jsx`, `MenuItemCard`, at `const dish = presentDish(item)`. Inspect your chosen item and collection props, variationId, evaluation and dish. Continue: card JSX renders, including option controls and price formatting.

20. **Visible result.** File: `frontend/src/domains/menu/components/MenuItemCard.jsx`. Symbol: JSX return in `MenuItemCard`. Break at `return <article ...>`. Inspect rendered source expressions, then continue and locate the article in Elements. Compare its name/description/price with the chosen Network JSON item. Type a search and observe a render without a menu GET; choose another collection and observe discovery followed by a new items GET. Stop before interacting with Add: the browsing slice is complete.

### Final self-check

- Can I identify my item at every representation boundary without relying on its displayed name alone?
- Can I explain a disabled/unavailable dish using the values I inspected?
- Can I name the exact method where persistence data became the public read model?
- Can I explain which calls happen during render, after commit, on HTTP completion and on a later user event?

### Verification limits

The call graph, branches, field shapes, mappings and migration references above were checked against source. No live database values, actual SQL statement log, timing, rendered screenshot or active deployment configuration was captured. Exact default collection, returned items, availability, media URLs and lazy-query count depend on runtime data/configuration. The application-level path is traceable confidently; those runtime outcomes must be observed with the exercise.
