# Workflow vertical slices

This guide follows each implemented workflow from user action to frontend code,
HTTP request, backend processing and persistence, then back to the screen. Use it
as a reading/debugging order rather than as a list of every scaffold file.

The paths below describe the implementation through V16. Arrows mean execution or
data flow; grouped supporting files are not necessarily called one after another.
The backend is one Spring Boot application. Not every slice has a separate handler:
some transactional staff operations currently live directly in controllers.

See the [domain map](domain-map.md) for ownership and the [API reference](api.md)
for payload constraints, response shapes and permissions.

## Shared entry and security path

Frontend entry: [App.jsx](../../frontend/src/app/App.jsx) → [AppRouter.jsx](../../frontend/src/app/AppRouter.jsx). App mounts the shared
AuthProvider and CartProvider; AppRouter selects hash routes. Public header/footer
links are in [Header.jsx](../../frontend/src/website/components/Header.jsx) → [Footer.jsx](../../frontend/src/website/components/Footer.jsx).

Protected staff route: [ProtectedRoute.jsx](../../frontend/src/domains/identity/components/ProtectedRoute.jsx) → [LoginPage.jsx](../../frontend/src/domains/identity/pages/LoginPage.jsx) → [LoginForm.jsx](../../frontend/src/domains/identity/components/LoginForm.jsx).
The route guard reads [AuthContext.jsx](../../frontend/src/domains/identity/model/AuthContext.jsx); the backend,
not the guard, ultimately enforces permissions.

Authenticated API calls use [identityApi.js](../../frontend/src/domains/identity/api/identityApi.js), whose
`fetchWithIdentity` obtains/refreshes bearer tokens and retries once after a 401.
Guest calls do not require JWTs. Write clients first acquire CSRF and keep cookies.

Backend gate: [SecurityConfig.java](../../backend/src/main/java/au/com/nakornthai/shared/security/SecurityConfig.java) → [JwtAuthenticationFilter.java](../../backend/src/main/java/au/com/nakornthai/shared/security/JwtAuthenticationFilter.java) → [JwtService.java](../../backend/src/main/java/au/com/nakornthai/shared/security/JwtService.java).
Spring Security applies CSRF and role checks; the JWT filter verifies the token and
loads the live session/account before allowing protected controller work. Each
staff slice below includes this gate even where it is not repeated.

```mermaid
sequenceDiagram
    participant U as User
    participant P as React page
    participant A as API client
    participant S as Spring Security
    participant C as Controller / handler
    participant D as PostgreSQL
    U->>P: Open view or submit action
    P->>A: Load or save
    opt Mutation
        A->>S: Get CSRF token with session cookie
        S-->>A: Token and cookie
    end
    A->>S: HTTP request (JWT for staff; CSRF for writes)
    S->>C: Authorized request
    C->>D: Read or transactional update
    D-->>C: Data / version
    C-->>A: JSON or 204
    A-->>P: Render result, refresh view or show error
```

## 1. Identity: sign in, restore session and sign out

**Trigger:** open a protected staff page, submit credentials, reload the browser,
or sign out.

1. [LoginForm.jsx](../../frontend/src/domains/identity/components/LoginForm.jsx) → [AuthContext.jsx](../../frontend/src/domains/identity/model/AuthContext.jsx) → [identityApi.js](../../frontend/src/domains/identity/api/identityApi.js)
   sends login credentials after fetching `/api/identity/csrf`. AuthContext also
   attempts refresh on startup and refreshes access before expiry.
2. Login: [LoginController.java](../../backend/src/main/java/au/com/nakornthai/identity/login/LoginController.java) → [LoginRequest.java](../../backend/src/main/java/au/com/nakornthai/identity/login/LoginRequest.java) → [LoginHandler.java](../../backend/src/main/java/au/com/nakornthai/identity/login/LoginHandler.java) → [JpaUserRepository.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/JpaUserRepository.java).
   LoginHandler throttles attempts; JpaUserRepository checks bcrypt and creates a session.
3. Restore/rotate: [RefreshTokenController.java](../../backend/src/main/java/au/com/nakornthai/identity/refresh/RefreshTokenController.java) calls the same
   JpaUserRepository refresh operation. Logout follows
   [LogoutController.java](../../backend/src/main/java/au/com/nakornthai/identity/logout/LogoutController.java) to revoke the matching session.
4. [IdentityCookies.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/IdentityCookies.java) → [LoginResponse.java](../../backend/src/main/java/au/com/nakornthai/identity/login/LoginResponse.java)
   supplies the HttpOnly refresh cookie and access-token/user response. Persistence:
   [UserJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/UserJpaEntity.java) → [StaffSessionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/StaffSessionJpaEntity.java) → [SpringDataUserRepository.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/SpringDataUserRepository.java) → [SpringDataStaffSessionRepository.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/SpringDataStaffSessionRepository.java).
5. The frontend publishes the new auth state and the guard renders permitted pages.
   Failed refresh clears that state; failed logout is surfaced for retry.

**Data:** `staff_user`, `staff_session` (V12). Bootstrap is a startup path, not an HTTP
slice: [IdentityBootstrap.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/IdentityBootstrap.java) creates missing configured
accounts without resetting existing staff-managed accounts.

**Checks:** [IdentityIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/IdentityIntegrationTest.java);
[identityApi.test.js](../../frontend/src/domains/identity/api/identityApi.test.js).

## 2. Identity: administer staff accounts

**Trigger:** ADMIN opens `/#/staff/users`, creates an account or changes its role,
enabled state or password.

Frontend: [UsersPage.jsx](../../frontend/src/domains/identity/pages/UsersPage.jsx) → [identityApi.js](../../frontend/src/domains/identity/api/identityApi.js)
uses `usersRequest` for GET/POST/PUT `/api/identity/users` and `/<built-in function id>`.
Backend: [StaffUsersController.java](../../backend/src/main/java/au/com/nakornthai/identity/currentuser/StaffUsersController.java) directly performs
transactional account updates using the identity repositories and EntityManager.
It checks the version, protects the last enabled ADMIN and revokes updated users'
sessions. The page reloads accounts after saving; updating the current user can
require sign-in again. [CurrentUserController.java](../../backend/src/main/java/au/com/nakornthai/identity/currentuser/CurrentUserController.java)
provides `/api/identity/me`; the current UI primarily uses login/refresh user data.

**Data:** identity tables. **Checks:** [IdentityIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/IdentityIntegrationTest.java).

## 3. Menu: homepage dishes and public collections

**Trigger:** homepage signature section or `/#/menu` collection selection/search.

1. Homepage: [HomePage.jsx](../../frontend/src/website/pages/HomePage.jsx) → [SignatureDishes.jsx](../../frontend/src/website/components/SignatureDishes.jsx).
   Full menu: [MenuPage.jsx](../../frontend/src/domains/menu/pages/MenuPage.jsx).
2. Both use [useMenu.js](../../frontend/src/domains/menu/hooks/useMenu.js) → [menuApi.js](../../frontend/src/domains/menu/api/menuApi.js)
   to GET `/api/menu/collections/{slug}/items`. The hook handles loading, retries
   and cancellation of obsolete reads.
3. [ListMenuController.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuController.java) → [ListMenuQuery.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuQuery.java) → [ListMenuHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/ListMenuHandler.java) → [MenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/MenuItemRepository.java) → [JpaMenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/JpaMenuItemRepository.java).
4. The JPA adapter uses [SpringDataMenuCollectionRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuCollectionRepository.java) → [SpringDataMenuItemRepository.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/SpringDataMenuItemRepository.java) → [MenuItemMapper.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemMapper.java)
   to filter visible records and map images, variations and food profiles.
5. [MenuItem.java](../../backend/src/main/java/au/com/nakornthai/menu/domain/MenuItem.java) → [MenuResponse.java](../../backend/src/main/java/au/com/nakornthai/menu/listmenu/MenuResponse.java) defines the
   public response. [menuModel.js](../../frontend/src/domains/menu/model/menuModel.js) adapts it for rendering.
   Search on MenuPage filters loaded dishes in the browser.

**Data:** the 12 menu tables. Entry entities:
[MenuCollectionJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionJpaEntity.java) → [MenuCollectionItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuCollectionItemJpaEntity.java) → [MenuItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemJpaEntity.java) → [MenuItemVariationJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemVariationJpaEntity.java) → [MenuItemImageJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemImageJpaEntity.java).
Supporting dietary/allergen entities and repositories live in the same infrastructure
folder; MenuItemMapper is the read path into their profiles.

**Checks:** [ListMenuApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/ListMenuApiTest.java) → [ListMenuHandlerTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/ListMenuHandlerTest.java) → [MenuSchemaIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/MenuSchemaIntegrationTest.java) → [MenuEntityIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/menu/listmenu/MenuEntityIntegrationTest.java);
[menuApi.test.js](../../frontend/src/domains/menu/api/menuApi.test.js).

## 4. Menu: dashboard read, create, edit prices and archive

**Trigger:** ADMIN opens `/#/staff/menu` and loads or edits dishes.

Frontend: [StaffMenuPage.jsx](../../frontend/src/domains/staff/pages/StaffMenuPage.jsx) → [menuApi.js](../../frontend/src/domains/menu/api/menuApi.js).
`getStaffMenu` loads dashboard data; `saveMenuItem` chooses POST/PUT; `archiveMenuItem`
uses DELETE with a version. Writes acquire fresh CSRF.

| Operation | Backend thread |
|---|---|
| GET `/api/staff/menu/items` | [GetMenuItemController.java](../../backend/src/main/java/au/com/nakornthai/menu/getitem/GetMenuItemController.java) → [GetMenuItemHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/getitem/GetMenuItemHandler.java) → [MenuAdminService.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuAdminService.java) → [MenuItemResponse.java](../../backend/src/main/java/au/com/nakornthai/menu/getitem/MenuItemResponse.java) |
| POST `/api/staff/menu/items` | [CreateMenuItemController.java](../../backend/src/main/java/au/com/nakornthai/menu/createitem/CreateMenuItemController.java) → [CreateMenuItemRequest.java](../../backend/src/main/java/au/com/nakornthai/menu/createitem/CreateMenuItemRequest.java) → [CreateMenuItemHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/createitem/CreateMenuItemHandler.java) → MenuAdminService |
| PUT `/api/staff/menu/items/{id}` | [UpdateMenuItemController.java](../../backend/src/main/java/au/com/nakornthai/menu/updateitem/UpdateMenuItemController.java) → [UpdateMenuItemHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/updateitem/UpdateMenuItemHandler.java) → MenuAdminService; shares CreateMenuItemRequest |
| DELETE `/api/staff/menu/items/{id}` | [DeleteMenuItemController.java](../../backend/src/main/java/au/com/nakornthai/menu/deleteitem/DeleteMenuItemController.java) → [DeleteMenuItemHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/deleteitem/DeleteMenuItemHandler.java) → MenuAdminService |

MenuAdminService owns locking/version checks, collection membership synchronization,
price updates and archiving. Prices arrive as decimal dollars and persist as cents.
Name/description changes invalidate dietary verification. Dashboard state is reloaded
after writes; conflicts require fresh data before saving again.

**Data:** menu items, variations and memberships; category/collection option reads.
**Checks:** [MenuAdminApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/createitem/MenuAdminApiTest.java); menu API tests above.

## 5. Menu: upload an image or change its focus

**Trigger:** image controls on StaffMenuPage, using `saveMenuImage` in menuApi.

POST multipart `/api/staff/menu/items/{id}/image` →
[MenuImageController.java](../../backend/src/main/java/au/com/nakornthai/menu/updateitem/MenuImageController.java) → [MenuImageService.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuImageService.java).
The service validates the version, file and focus metadata, re-encodes supported
uploads and updates [MenuItemImageJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuItemImageJpaEntity.java).
Omitting a file updates the existing image's metadata. The refreshed dish supplies
an image URL; the browser reads `/media/menu/{name}` through MenuImageController.

**Storage:** image metadata in PostgreSQL; JPEG bytes in configured filesystem media
storage. **Checks:** [MenuImageServiceTest.java](../../backend/src/test/java/au/com/nakornthai/menu/createitem/MenuImageServiceTest.java) → [MenuAdminApiTest.java](../../backend/src/test/java/au/com/nakornthai/menu/createitem/MenuAdminApiTest.java).

## 6. Ordering: cart and guest checkout

**Trigger:** add a menu variation, review the cart and submit pickup checkout.

1. [MenuPage.jsx](../../frontend/src/domains/menu/pages/MenuPage.jsx) → [CartContext.jsx](../../frontend/src/domains/ordering/model/CartContext.jsx) → [cartReducer.js](../../frontend/src/domains/ordering/model/cartReducer.js) → [Cart.jsx](../../frontend/src/domains/ordering/components/Cart.jsx)
   manages the client cart and its sessionStorage persistence.
2. [CheckoutPage.jsx](../../frontend/src/domains/ordering/pages/CheckoutPage.jsx) → [orderApi.js](../../frontend/src/domains/ordering/api/orderApi.js)
   reads options, checks prices and generates a request UUID/private tracking token.
   The exact pending payload stays in sessionStorage for lost-response recovery.
3. `submitOrder` obtains CSRF and POSTs `/api/orders` →
   [CreateOrderController.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderController.java) → [CreateOrderRequest.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderRequest.java) → [CreateOrderHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderHandler.java).
4. The handler locks/reads menu items and variations, validates publication,
   availability and expected price, then persists order snapshots and an initial event:
   [OrderJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderJpaEntity.java) → [OrderItemJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderItemJpaEntity.java) → [OrderEventJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderEventJpaEntity.java).
5. [OrderMapper.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderMapper.java) → [CreateOrderResponse.java](../../backend/src/main/java/au/com/nakornthai/ordering/createorder/CreateOrderResponse.java)
   returns the receipt. Checkout saves the tracking reference, clears the cart and
   navigates to confirmation. Conflicting/lost responses can trigger a private lookup.

Checkout now uses [checkoutApi.js](../../frontend/src/domains/ordering/api/checkoutApi.js)
to revalidate all collections listed in the shared
[menuCollections.js](../../frontend/src/domains/menu/model/menuCollections.js).
Mixed Chef’s Specials, regular-menu and drinks carts use current prices. Unavailable
items (including lunch), missing variations and service failures block checkout.
A 404 collection is skipped, but its missing cart items remain unavailable.
[checkoutApi.test.js](../../frontend/src/domains/ordering/api/checkoutApi.test.js)
covers these cases. The backend still performs authoritative validation.

**Data:** V11 order tables with V16 additions, read dependency on menu tables.
Payment creation/capture is a subsequent receipt-page flow, described below.
**Checks:** [CreateOrderIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java);
[orderApi.test.js](../../frontend/src/domains/ordering/api/orderApi.test.js).

## 7. Ordering: private guest tracking

**Trigger:** `/#/order-confirmation` reads the stored receipt and polls for progress.

[OrderConfirmationPage.jsx](../../frontend/src/domains/ordering/pages/OrderConfirmationPage.jsx) → [orderApi.js](../../frontend/src/domains/ordering/api/orderApi.js)
→ GET `/api/orders/{id}` with `X-Order-Token` →
[GetOrderController.java](../../backend/src/main/java/au/com/nakornthai/ordering/getorder/GetOrderController.java) → [GetOrderHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/getorder/GetOrderHandler.java) → [SpringDataOrderRepository.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/SpringDataOrderRepository.java) → [OrderMapper.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderMapper.java).
The handler checks the tracking-token hash and returns a restricted response; wrong
or missing tokens yield 404. The page refreshes status without exposing a public
order list. **Checks:** CreateOrderIntegrationTest and orderApi tests.

## 8. Ordering: FOH and kitchen work queues

**Trigger:** `/#/staff/foh` or `/#/staff/kitchen`, followed by a staff status action.

[KitchenDashboardPage.jsx](../../frontend/src/domains/staff/pages/KitchenDashboardPage.jsx) → [StaffOrdersPage.jsx](../../frontend/src/domains/staff/pages/StaffOrdersPage.jsx)
shares the operational view; the kitchen wrapper selects kitchen behavior.
[orderApi.js](../../frontend/src/domains/ordering/api/orderApi.js) serializes polling and mutation calls.

Queue reads → [ListOrdersController.java](../../backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersController.java) → [ListOrdersHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/listorders/ListOrdersHandler.java) → [SpringDataOrderRepository.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/SpringDataOrderRepository.java) → [OrderMapper.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderMapper.java).
FOH can see contact details and recent history; kitchen receives preparation data
with customer name/phone suppressed.

Status writes → PATCH `/api/staff/orders/{id}/status` →
[ChangeOrderStatusController.java](../../backend/src/main/java/au/com/nakornthai/ordering/changestatus/ChangeOrderStatusController.java) → [ChangeOrderStatusCommand.java](../../backend/src/main/java/au/com/nakornthai/ordering/changestatus/ChangeOrderStatusCommand.java) → [ChangeOrderStatusHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/changestatus/ChangeOrderStatusHandler.java).
The handler enforces action-specific roles, valid transitions and versions, then
updates the order and appends an OrderEventJpaEntity. The UI reloads the queue.

**Checks:** [CreateOrderIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/ordering/createorder/CreateOrderIntegrationTest.java); orderApi tests.
The empty backend `staff/` controllers are not part of this path.

## 9. Reservations: guest table request

**Trigger:** `/#/reservations` form submission.

[ReservationPage.jsx](../../frontend/src/domains/reservation/pages/ReservationPage.jsx) → [reservationApi.js](../../frontend/src/domains/reservation/api/reservationApi.js)
→ CSRF then POST `/api/reservations` →
[CreateReservationController.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateReservationController.java) → [CreateReservationRequest.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateReservationRequest.java) → [CreateReservationHandler.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateReservationHandler.java) → [SpringDataReservationRepository.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/SpringDataReservationRepository.java) → [ReservationJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationJpaEntity.java).
The handler validates Melbourne time and checks exact retry identity. The page shows
a reference and a request acknowledgment, not a guaranteed table.

**Data:** `reservation` (V13). **Checks:**
[CreateReservationIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/reservation/createreservation/CreateReservationIntegrationTest.java);
[reservationApi.test.js](../../frontend/src/domains/reservation/api/reservationApi.test.js).

## 10. Reservations: staff confirmation and attendance

**Trigger:** ADMIN/FOH opens `/#/staff/reservations`, selects a date and updates a booking.

[ReservationAdminPage.jsx](../../frontend/src/domains/reservation/pages/ReservationAdminPage.jsx) → [reservationApi.js](../../frontend/src/domains/reservation/api/reservationApi.js)
→ GET `/api/staff/reservations?date=...` or PATCH `/api/staff/reservations/{id}` →
[ListReservationsController.java](../../backend/src/main/java/au/com/nakornthai/reservation/listreservations/ListReservationsController.java).
This controller directly queries SpringDataReservationRepository and performs
transactional EntityManager updates; the scaffolded ListReservationsHandler and
CancelReservationHandler are not used. It checks version/transitions and stores
the latest staff note/actor before the page reloads its date list.

**Data/checks:** same reservation entity and tests as the guest request. There is
no automatic capacity check or notification call in either thread.

## 11. Functions: public venue enquiry

**Trigger:** header Functions link opens `/#/functions`.

[FunctionsPage.jsx](../../frontend/src/website/pages/FunctionsPage.jsx) → [functionApi.js](../../frontend/src/domains/reservation/api/functionApi.js)
→ CSRF then POST `/api/functions` →
[CreateFunctionEnquiryController.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateFunctionEnquiryController.java) → [CreateFunctionEnquiryRequest.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateFunctionEnquiryRequest.java) → [CreateFunctionEnquiryHandler.java](../../backend/src/main/java/au/com/nakornthai/reservation/createreservation/CreateFunctionEnquiryHandler.java) → [SpringDataFunctionEnquiryRepository.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/SpringDataFunctionEnquiryRepository.java) → [FunctionEnquiryJpaEntity.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/FunctionEnquiryJpaEntity.java).
The handler validates guest details/date and protects duplicate retries. A successful
response displays a reference; no venue hold or email is generated.

**Data:** `function_enquiry` (V15), separate from table reservations.
**Checks:** [FunctionEnquiryIntegrationTest.java](../../backend/src/test/java/au/com/nakornthai/reservation/createreservation/FunctionEnquiryIntegrationTest.java);
[functionApi.test.js](../../frontend/src/domains/reservation/api/functionApi.test.js).

## 12. Functions: staff follow-up and event confirmation

**Trigger:** ADMIN/FOH opens `/#/staff/functions`, filters the queue and saves follow-up.

[FunctionEnquiriesPage.jsx](../../frontend/src/domains/staff/pages/FunctionEnquiriesPage.jsx) → [functionApi.js](../../frontend/src/domains/reservation/api/functionApi.js)
→ GET `/api/staff/functions?status=...&page=...` or PATCH `/api/staff/functions/{id}` →
[FunctionEnquiriesController.java](../../backend/src/main/java/au/com/nakornthai/reservation/listreservations/FunctionEnquiriesController.java).
The controller uses SpringDataFunctionEnquiryRepository for pagination and directly
locks/updates FunctionEnquiryJpaEntity for writes. It checks versions, transitions
and the required agreed date for confirmation. The UI reloads the selected queue.
Preferred customer dates remain distinct from arranged dates; follow-up is manual.

**Data/checks:** same enquiry entity and tests as the public slice.

## 13. Staff home and unfinished domains

[StaffDashboardPage.jsx](../../frontend/src/domains/staff/pages/StaffDashboardPage.jsx) reads AuthContext and shows role-aware
links. It has no separate dashboard aggregation request; each destination calls its
own domain API. Backend `staff/` remains empty.

| Scaffold | Executable workflow today |
|---|---|
| Customer | None; guest details live on orders, reservations and enquiries |
| Payment webhooks/refunds | Not implemented; PayPal capture and PayID reconciliation threads are documented below |
| Status notifications | Not implemented; requested SMS/email OTP verification is documented below |
| Restaurant / availability | None; opening hours and capacity are not enforced by these empty packages |
| Unused hooks/handlers inside active domains | Their existence does not put them in a runtime thread |

Do not treat empty test files as coverage. The linked tests above contain executable
checks; several other `*Test.java` files are still placeholders.

## Cross-cutting troubleshooting and extension points

Backend request logging: [LoggingAspect.java](../../backend/src/main/java/au/com/nakornthai/shared/observability/LoggingAspect.java).
Feature error handling: [IdentityExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/identity/infrastructure/IdentityExceptionHandler.java) → [MenuWriteExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/menu/infrastructure/MenuWriteExceptionHandler.java) → [OrderExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderExceptionHandler.java) → [ReservationExceptionHandler.java](../../backend/src/main/java/au/com/nakornthai/reservation/infrastructure/ReservationExceptionHandler.java).
Security failures may occur before these handlers and have empty bodies.

When tracing an issue, start with the page's API helper and HTTP status, follow its
controller into the actual handler/service, then inspect the owning entity/transaction.
Use the tests linked to that slice to reproduce the boundary involved. Do not infer
a missing implementation from the name of a scaffolded file alone.

For new slices, document the trigger, route, frontend state/client, authorization,
controller/handler, persistence, response handling and executable tests. Keep the
existing folder structure; describe any proposed restructuring before implementing it.

## V16: cart, payments and tracking recovery

The menu's inline Cart is now mounted inside
[CartDock.jsx](../../frontend/src/domains/ordering/components/CartDock.jsx), which
[App.jsx](../../frontend/src/app/App.jsx) keeps outside AppRouter so it persists
across routes. Checkout records an optional email and selected payment method.

[PaymentForm.jsx](../../frontend/src/domains/payment/components/PaymentForm.jsx)
uses [paymentApi.js](../../frontend/src/domains/payment/api/paymentApi.js) →
[CreatePaymentController.java](../../backend/src/main/java/au/com/nakornthai/payment/createpayment/CreatePaymentController.java)
→ [CreatePaymentHandler.java](../../backend/src/main/java/au/com/nakornthai/payment/createpayment/CreatePaymentHandler.java)
→ [PayPalPaymentProvider.java](../../backend/src/main/java/au/com/nakornthai/payment/infrastructure/PayPalPaymentProvider.java)
or manual PayID receipt recording. Staff use
[PaymentStatus.jsx](../../frontend/src/domains/payment/components/PaymentStatus.jsx).

[OrderTrackingPage.jsx](../../frontend/src/domains/ordering/pages/OrderTrackingPage.jsx)
→ [OrderVerificationController.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderVerificationController.java)
→ [OrderVerificationHandler.java](../../backend/src/main/java/au/com/nakornthai/notification/orderconfirmation/OrderVerificationHandler.java)
→ [TwilioVerifyClient.java](../../backend/src/main/java/au/com/nakornthai/notification/infrastructure/TwilioVerifyClient.java).
Verified challenges issue hashed grants checked by
[OrderAccessService.java](../../backend/src/main/java/au/com/nakornthai/ordering/infrastructure/OrderAccessService.java).
Payment and notification are therefore no longer wholly scaffolded; remaining empty
provider/refund/status-notification files are not part of these paths.
See [setup and limitations](../payment/payments-and-tracking.md).
