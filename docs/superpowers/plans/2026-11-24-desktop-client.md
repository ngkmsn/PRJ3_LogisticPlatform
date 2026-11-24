# Desktop Client (Admin/Operator) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the Figma-generated `fe_dispatcher` into a real Electron desktop app for `ADMIN`/`DISPATCHER` that runs every screen against the live backend, plus the small backend changes it needs.

**Architecture:** React SPA (HashRouter, TanStack Query) split into `api/`, `auth/`, `components/`, `pages/`; one `apiFetch` wrapper owns base URL, token and error mapping. Electron serves the built SPA over the custom scheme `app://dispatcher`, which the gateway allows via CORS. Backend: order-service opens order list/detail to staff; gateway gets the Electron origin and higher demo rate limits; a bash script seeds demo data through public APIs.

**Tech Stack:** React 19, Vite 8, TypeScript 5.7, Tailwind CSS v4, react-router-dom 7, @tanstack/react-query 5, leaflet + react-leaflet 5, Vitest + @testing-library/react + jsdom, Electron + electron-builder, pnpm. Backend: Java 21 target / Quarkus 3.15 (build with JDK 23: `export JAVA_HOME=$(/usr/libexec/java_home -v 23)`).

**Spec:** `docs/superpowers/specs/2026-11-24-desktop-client-design.md`

## Global Constraints

- Only roles `ADMIN` and `DISPATCHER` may use the app; `CUSTOMER`/`DRIVER` login is refused with the message `This account cannot use the desktop client`.
- UI copy stays in English.
- All HTTP goes through the API gateway; responses are `{ success, message, data }`; `401` = invalid/expired token → sign out, `403` = insufficient permission → show inline message.
- No mock data may remain anywhere in `fe_dispatcher/src` (no hard-coded orders/drivers/exceptions/KPIs, no `sample.dispatcher.jwt`).
- Reports and Settings are removed from navigation; Assignment + Exceptions are merged into one page named `Needs Action`.
- Electron origin is exactly `app://dispatcher`; window 1440×900, min 1200×760, `contextIsolation: true`, `nodeIntegration: false`.
- Default server URL: `""` in browser dev (Vite proxies `/api` → `http://localhost:8080`), `http://localhost:8080` in Electron (`VITE_API_BASE_URL` at build time); user-editable on the login screen and persisted in `localStorage` key `serverUrl`.
- Token storage: `localStorage` keys `token` and `user`.
- Polling only on the page that is open: Dashboard 30 s, Needs Action 15 s, Drivers 10 s; Orders does not poll (refresh button only).
- Package manager `pnpm`; delete `package-lock.json`.
- Backend tests must stay green: `mvn -B install -pl <module>`.

## Review Focus

1. **Server URL typed with a trailing slash or without `http://`** (e.g. `localhost:8080/`) → requests must still reach `http://localhost:8080/api/...`. Test in Task 4 (`normalizeServerUrl`).
2. **Token expires mid-session** (24 h JWT, demo machine left running) → the next request must land on the login page, not leave a page stuck on a spinner or an error. Test in Task 4 (`apiFetch` 401 → `onUnauthorized`) and Task 5 (`AuthProvider` clears session).
3. **Backend down / wrong server URL at login** (network error, not an HTTP status) → the login form must show `Cannot reach server at <url>` instead of a generic crash. Test in Task 4 (`ApiError` with `status: 0`) and Task 5 (login page renders it).
4. **Assigning a driver who just became unavailable** (backend returns 400/409 with a message) → the assign modal must show the backend's message and stay open; the list must refetch. Test in Task 8.
5. **Driver with no GPS yet** (monitoring item without latitude/longitude) → the drivers page must list the driver and the map must simply omit the marker, not throw. Test in Task 9.

---

### Task 1: order-service — staff can list and view all orders

**Files:**
- Modify: `services/order-service/src/main/java/com/logistics/order/service/OrderService.java` (`listMyOrders` ~line 149, `getOrderDetails` ~line 183)
- Modify: `services/order-service/src/test/java/com/logistics/order/controller/OrderListingIntegrationTest.java` (`listOrders_nonCustomer_forbidden`)
- Modify: `services/order-service/src/test/java/com/logistics/order/controller/OrderDetailsIntegrationTest.java` (`getOrderDetails_nonCustomer_forbidden`)
- Modify: `services/order-service/src/test/java/com/logistics/order/service/OrderServiceTest.java` (tests at ~258 and ~333 if they use ADMIN/DISPATCHER)
- Test: `services/order-service/src/test/java/com/logistics/order/controller/StaffOrderAccessIntegrationTest.java`

**Interfaces:**
- Produces: `GET /api/orders?page&size&status` — CUSTOMER: own orders (unchanged); ADMIN/DISPATCHER: all orders, newest `createdAt` first, same `PagedResponse<OrderDto>` shape; DRIVER: 403. `GET /api/orders/{id}` — ADMIN/DISPATCHER may view any order. Service-token behaviour unchanged.

- [ ] **Step 1: Write `StaffOrderAccessIntegrationTest`** (RestAssured, same token setup as `OrderListingIntegrationTest`): customer A and customer B each create one order; then
  - `staffList_dispatcherSeesAllCustomersOrders`: dispatcher `GET /api/orders?size=50` → 200 and `data.content.id` contains both order ids.
  - `staffList_adminFiltersByStatus`: admin `GET /api/orders?status=PENDING` → 200, every `data.content.status` is `PENDING`.
  - `staffDetail_dispatcherViewsOtherCustomersOrder`: dispatcher `GET /api/orders/{idOfA}` → 200, `data.id == idOfA`.
  - `customerList_stillOwnOnly`: customer A list does not contain B's id; customer A `GET /api/orders/{idOfB}` → 403.
  - `driver_stillForbidden`: driver list → 403, driver detail → 403.
- [ ] **Step 2:** Run `mvn -B test -pl services/order-service -Dtest=StaffOrderAccessIntegrationTest` → FAIL (staff gets 403).
- [ ] **Step 3: Implement.** In `listMyOrders`, resolve the principal; if role is ADMIN or DISPATCHER build the query without the `customerId` clause (keep the status filter, order by `createdAt desc`); CUSTOMER path unchanged; any other role → `AccessDeniedException("Access denied: Only customers and operations staff are permitted to view orders")`. In `getOrderDetails`, allow ADMIN/DISPATCHER (ownership check still applies only to CUSTOMER); other non-customer roles keep the 403 with message `Only customers and operations staff are permitted to view order details`.
- [ ] **Step 4:** Update the four existing denial tests so they assert DRIVER is forbidden and ADMIN now gets 200 (change expected message substrings to the new text).
- [ ] **Step 5:** Run `mvn -B install -pl services/order-service` → all tests pass.
- [ ] **Step 6: Commit** `feat(order): allow operations staff to list and view all orders`.

### Task 2: gateway — Electron origin and demo rate limits

**Files:**
- Modify: `services/api-gateway/src/main/resources/application.properties` (CORS origins, rate-limit policies)
- Modify: `services/api-gateway/src/main/java/com/logistics/gateway/ratelimit/GatewayRateLimitConfig.java:50,58` (defaults)
- Test: `services/api-gateway/src/test/java/com/logistics/gateway/GatewayCorsTest.java`

**Interfaces:**
- Produces: preflight from `Origin: app://dispatcher` is allowed; `orders` policy 120/60 s, `default` policy 120/60 s.

- [ ] **Step 1:** Add test `preflight_electronOrigin_allowed`: OPTIONS `/api/orders` with `Origin: app://dispatcher`, `Access-Control-Request-Method: GET` → 200 and `Access-Control-Allow-Origin` = `app://dispatcher`.
- [ ] **Step 2:** Run `mvn -B test -pl services/api-gateway -Dtest=GatewayCorsTest` → new test FAILS.
- [ ] **Step 3:** Set `quarkus.http.cors.origins=${GATEWAY_CORS_ORIGINS:/https?://(localhost|127\\.0\\.0\\.1)(:[0-9]+)?/,app://dispatcher}`; set `gateway.rate-limit.policies.orders.limit=120` and `gateway.rate-limit.policies.default.limit=120` in properties and the matching code defaults. If `GatewayRateLimitHandlerTest` pins the old numbers, update those expectations.
- [ ] **Step 4:** Run `mvn -B install -pl services/api-gateway` → all pass.
- [ ] **Step 5: Commit** `feat(gateway): allow Electron origin and raise demo rate limits`.

### Task 3: demo data script

**Files:**
- Create: `scripts/demo-data.sh`
- Modify: `README.md` (one short "Demo data" subsection under Build & Run)

**Interfaces:**
- Consumes: Task 1 not required. Uses public APIs only: `POST /api/auth/login`, `POST /api/users`, `POST /api/drivers`, `PATCH /api/drivers/me/availability`, `POST /api/shipments/tracking/location`, `POST /api/orders`, driver actions under `/api/shipments/assignments/{orderId}/...`.
- Produces: users `driver2/Driver@123` and `driver3/Driver@123` (role DRIVER, with driver profiles); GPS for `driver`, `driver2`, `driver3` around TP.HCM (≈10.77, 106.70 ± 0.03); ≥5 customer orders covering ASSIGNED, IN_TRANSIT, DELIVERED, UNASSIGNED, FAILED.

- [ ] **Step 1: Write the script** (`set -euo pipefail`, `GATEWAY=${GATEWAY:-http://localhost:8080}`, `curl` + `python3 -c` for JSON). Idempotent: a `409` on user creation is treated as "exists" and the script looks the user up via `GET /api/users?size=100`. Sequence: set all drivers AVAILABLE + post GPS → create 3 orders (auto-assigned) and move one to IN_TRANSIT and one to DELIVERED via the assigned driver's token → create 1 order and drive it to `fail` (body `{"failureReason":"CUSTOMER_UNAVAILABLE","notes":"demo"}`; if the backend rejects that reason code, use a value from the validation message) → set all drivers UNAVAILABLE → create 1 order (becomes UNASSIGNED) → set drivers AVAILABLE again. Print a summary table of order numbers and final statuses.
- [ ] **Step 2: Verify** with the stack running: `bash scripts/demo-data.sh` twice → both runs exit 0; `GET /api/shipments/assignments/actionable` as dispatcher shows `unassignedCount ≥ 1` and `failedCount ≥ 1`.
- [ ] **Step 3: Commit** `chore: add demo data script`.

### Task 4: frontend foundation — tooling, config, API client

**Files:**
- Modify: `fe_dispatcher/package.json`, `fe_dispatcher/vite.config.ts`, `fe_dispatcher/tsconfig.json`, `fe_dispatcher/.gitignore`
- Delete: `fe_dispatcher/.figma/`, `fe_dispatcher/package-lock.json`, `fe_dispatcher/AGENTS.md`, `fe_dispatcher/CLAUDE.md` (Figma Make instructions no longer apply), `fe_dispatcher/dist/` from git if tracked
- Create: `fe_dispatcher/src/api/client.ts`, `fe_dispatcher/src/api/types.ts`, `fe_dispatcher/src/api/config.ts`
- Test: `fe_dispatcher/src/api/client.test.ts`, `fe_dispatcher/src/api/config.test.ts`

**Interfaces:**
- Produces:
  - `config.ts`: `normalizeServerUrl(raw: string): string` (trim, drop trailing `/`, prefix `http://` when no scheme, `""` stays `""`); `getServerUrl(): string` (localStorage `serverUrl` ?? `import.meta.env.VITE_API_BASE_URL ?? ""`); `setServerUrl(url: string): void`.
  - `client.ts`: `class ApiError extends Error { status: number; }` (`status: 0` = network failure); `setUnauthorizedHandler(fn: () => void): void`; `apiFetch<T>(path: string, init?: RequestInit): Promise<T>` — prefixes `getServerUrl()`, adds `Authorization: Bearer <localStorage token>` when present and `Content-Type: application/json` when a body is sent, returns `json.data`, throws `ApiError(json.message ?? statusText, status)` on non-2xx or `success === false`, calls the unauthorized handler on 401 before throwing; network error → `ApiError("Cannot reach server at <base or current origin>", 0)`.
  - `types.ts`: `ApiResponse<T>`, `Paged<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }`, `Role = "ADMIN" | "DISPATCHER" | "DRIVER" | "CUSTOMER"`, `UserSummary { id; username; email; role: Role; status }`, `LoginResponse { accessToken; tokenType; expiresIn; user: UserSummary }`, plus DTOs moved verbatim from `App.tsx` (`OperationsDashboardDto` and its parts, `ActionableOrderItem`, `ActionableOrdersData`, `DriverMonitoringItem`) and `OrderDto`, `OrderStatusHistoryDto`, `AssignmentHistoryDto` matching the backend JSON.

- [ ] **Step 1:** Remove Figma plugins from `vite.config.ts` (keep react + tailwind + `@` alias); add `server: { port: 5173, proxy: { "/api": "http://localhost:8080" } }` and `base: "./"`; add `test: { environment: "jsdom", setupFiles: "src/test/setup.ts" }`. Scripts: `dev`, `build` (`tsc -b && vite build`), `typecheck` (`tsc -b --noEmit`), `test` (`vitest run`). Install: `pnpm add react-router-dom @tanstack/react-query leaflet react-leaflet` and `pnpm add -D vitest jsdom @testing-library/react @testing-library/jest-dom @types/leaflet`. Create `src/test/setup.ts` importing `@testing-library/jest-dom/vitest`.
- [ ] **Step 2: Write tests.** `config.test.ts`: `normalizeServerUrl("localhost:8080/") === "http://localhost:8080"`, `normalizeServerUrl(" https://api.x.com// ") === "https://api.x.com"`, `normalizeServerUrl("") === ""`. `client.test.ts` (mock `globalThis.fetch`): returns `data` on `{success:true,data:{a:1}}`; with token in localStorage sends `Authorization: Bearer t1`; 403 `{success:false,message:"nope"}` throws `ApiError` with `status 403`, `message "nope"` and does NOT call the unauthorized handler; 401 calls the handler once then throws; `fetch` rejecting with `TypeError` throws `ApiError` with `status 0` and message starting `Cannot reach server at`.
- [ ] **Step 3:** `pnpm test` → FAIL (modules missing).
- [ ] **Step 4:** Implement `config.ts`, `client.ts`, `types.ts`.
- [ ] **Step 5:** `pnpm test` → PASS; `pnpm typecheck` → clean (App.tsx may still compile unchanged).
- [ ] **Step 6: Commit** `refactor(fe): drop Figma Make tooling, add API client and tests`.

### Task 5: auth, routing and app shell

**Files:**
- Create: `fe_dispatcher/src/auth/AuthContext.tsx`, `fe_dispatcher/src/auth/LoginPage.tsx`, `fe_dispatcher/src/auth/RequireRole.tsx`, `fe_dispatcher/src/components/` (move `Icon`, `Badge`, `Button`, `SectionTitle`, `FilterBar`, `Pagination`, `AppShell` out of `App.tsx`, one file each or a small `ui.tsx`), `fe_dispatcher/src/pages/` placeholders
- Modify: `fe_dispatcher/src/App.tsx` (becomes providers + routes only), `fe_dispatcher/src/main.tsx`
- Test: `fe_dispatcher/src/auth/auth.test.tsx`

**Interfaces:**
- Consumes: Task 4 `apiFetch`, `ApiError`, `setUnauthorizedHandler`, `getServerUrl`, `setServerUrl`, `normalizeServerUrl`, `LoginResponse`, `Role`.
- Produces: `isDesktopRole(role: Role): boolean` (true only for ADMIN, DISPATCHER); `useAuth(): { user: UserSummary | null; login(username: string, password: string, serverUrl: string): Promise<void>; logout(): void }` — `login` throws `ApiError` on failure and throws `Error("This account cannot use the desktop client")` for other roles without storing anything; `<RequireRole roles={Role[]}>` redirects to `/login` when signed out and renders `You don't have permission to view this page.` when the role is not in `roles`. Routes (HashRouter): `/login`, `/` (Dashboard), `/orders`, `/orders/:id`, `/needs-action`, `/drivers`, `/users` (ADMIN only). Sidebar entries in that order; `Users` hidden for DISPATCHER; sign-out button shows current username.

- [ ] **Step 1: Write tests:** `isDesktopRole("DISPATCHER")` true, `isDesktopRole("CUSTOMER")` false; rendering `LoginPage` and submitting with fetch mocked to return a CUSTOMER login shows `This account cannot use the desktop client` and leaves `localStorage.token` unset; with fetch rejecting, shows text starting `Cannot reach server at`; with a DISPATCHER login, `localStorage.token` is set and the app navigates to `/`; after `setUnauthorizedHandler`'s handler fires, `localStorage.token` is cleared and `/login` renders.
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement. Login form fields: Username, Password, Server (prefilled from `getServerUrl()`, placeholder `http://localhost:8080`). `AuthProvider` registers `setUnauthorizedHandler(logout)` on mount. Wrap the app in `QueryClientProvider` (`retry: 1`, `refetchOnWindowFocus: false`).
- [ ] **Step 4:** `pnpm test` and `pnpm typecheck` → pass.
- [ ] **Step 5: Commit** `feat(fe): real login, role guard, routing and app shell`.

### Task 6: Dashboard page

**Files:**
- Create: `fe_dispatcher/src/pages/DashboardPage.tsx`, `fe_dispatcher/src/api/dashboard.ts`
- Modify: remove `Dashboard` and its mock constants from `App.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `OperationsDashboardDto`.
- Produces: `fetchDashboard(refresh?: boolean): Promise<OperationsDashboardDto>` → `GET /api/shipments/operations/dashboard[?refresh=true]`.

- [ ] **Step 1:** Port the existing `Dashboard` UI onto `useQuery({ queryKey: ["dashboard"], queryFn: () => fetchDashboard(), refetchInterval: 30_000 })`; KPI tiles, recent orders and attention items come only from the DTO; the hard-coded `exceptions` list on the dashboard is replaced by `attentionItems`. Clicking a recent order navigates to `/orders/:id`. Refresh button calls `fetchDashboard(true)`.
- [ ] **Step 2: Verify** against the running stack after `scripts/demo-data.sh`: `pnpm dev`, log in as `dispatcher` → totals match `GET /api/shipments/operations/dashboard`. `grep -n "const orders = \[\|const exceptions = \[" src -r` → no match in dashboard code.
- [ ] **Step 3: Commit** `feat(fe): dashboard on live data`.

### Task 7: Orders list and order detail

**Files:**
- Create: `fe_dispatcher/src/pages/OrdersPage.tsx`, `fe_dispatcher/src/pages/OrderDetailPage.tsx`, `fe_dispatcher/src/api/orders.ts`
- Modify: remove `OrdersPage`, `OrderDetail` and the `orders` mock array from `App.tsx`

**Interfaces:**
- Consumes: Task 1 endpoints; `apiFetch`, `Paged`, `OrderDto`, `OrderStatusHistoryDto`, `AssignmentHistoryDto`.
- Produces: `listOrders(params: { page: number; size: number; status?: string }): Promise<Paged<OrderDto>>`; `getOrder(id: string): Promise<OrderDto>`; `getOrderHistory(id: string): Promise<OrderStatusHistoryDto[]>`; `getAssignmentHistory(orderId: string): Promise<AssignmentHistoryDto[]>` (`GET /api/shipments/assignments/{orderId}/history`).

- [ ] **Step 1:** Orders page: table (order number, recipient, address, total, status badge, created) with status filter (`ALL`, `PENDING`, `CONFIRMED`, `ASSIGNED`, `IN_TRANSIT`, `DELIVERED`, `CANCELLED`), page size 20, `Pagination` driven by `totalElements`, Refresh button, no polling; row click → `/orders/:id`.
- [ ] **Step 2:** Order detail: header + items + recipient, status timeline from order history, assignment history (driver, action, performed by, reason). If the order status is `PENDING`/`CONFIRMED` show an `Assign driver` button that opens the Task 8 modal. A 404 renders `Order not found`.
- [ ] **Step 3: Verify** live: list shows the demo orders; filter `DELIVERED` shows only delivered; detail of the delivered order shows PENDING → ASSIGNED → IN_TRANSIT → DELIVERED.
- [ ] **Step 4: Commit** `feat(fe): orders list and detail on live data`.

### Task 8: Needs Action page and assign modal

**Files:**
- Create: `fe_dispatcher/src/pages/NeedsActionPage.tsx`, `fe_dispatcher/src/components/AssignDriverModal.tsx`, `fe_dispatcher/src/api/assignments.ts`
- Modify: remove `AssignmentPage`, `ExceptionsPage`, `AssignModal`, `exceptions` mock from `App.tsx`
- Test: `fe_dispatcher/src/components/AssignDriverModal.test.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `ActionableOrdersData`, `DriverMonitoringItem`, `listDrivers` from Task 9 (if Task 9 is not done yet, add `listDrivers` here in `api/drivers.ts` and Task 9 reuses it).
- Produces: `listActionable(params: { status?: "ALL" | "UNASSIGNED" | "FAILED"; page: number; size: number }): Promise<ActionableOrdersData>`; `assignDriver(orderId: string, driverId: string, notes?: string): Promise<void>` (`POST .../{orderId}/manual-assign`, body `{driverId, notes}`); `reassignDriver(orderId: string, driverId: string, reason: string): Promise<void>` (`POST .../{orderId}/reassign`, body `{newDriverId, reason, notes?}`); `<AssignDriverModal orderId mode: "assign" | "reassign" onClose onDone />`.

- [ ] **Step 1: Write modal test:** drivers list mocked with two drivers; selecting one and confirming calls `assignDriver` with that driver's `driverId`; when `assignDriver` rejects with `ApiError("Driver is not available", 400)` the modal shows `Driver is not available`, stays open, and `onDone` is not called.
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement page (tabs All / Unassigned / Failed with counts from `unassignedCount`/`failedCount`, `refetchInterval: 15_000`) and modal (driver picker lists real drivers with availability + distance if present; UNAVAILABLE drivers shown but disabled; `reassign` mode requires a reason). On success invalidate `["actionable"]`, `["dashboard"]`, `["orders"]`.
- [ ] **Step 4:** `pnpm test` → PASS. **Verify** live: the demo UNASSIGNED order disappears from the list after manual assign and its detail shows `MANUAL_ASSIGN` in assignment history.
- [ ] **Step 5: Commit** `feat(fe): needs-action queue with manual assign and reassign`.

### Task 9: Drivers page with map

**Files:**
- Create: `fe_dispatcher/src/pages/DriversPage.tsx`, `fe_dispatcher/src/components/DriverMap.tsx`, `fe_dispatcher/src/api/drivers.ts` (or extend it)
- Modify: remove `DriversPage`, `DriverDetailModal`, `TrackDriverModal`, `drivers` mock from `App.tsx`
- Test: `fe_dispatcher/src/components/DriverMap.test.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `DriverMonitoringItem`.
- Produces: `listDrivers(): Promise<DriverMonitoringItem[]>` (`GET /api/shipments/tracking/operations/drivers`; unwrap `content` if paged); `getDriver(driverId: string): Promise<DriverMonitoringItem>`; `<DriverMap drivers={DriverMonitoringItem[]} selectedId?: string />`; exported helper `driversWithPosition(drivers): DriverMonitoringItem[]` (only items with finite latitude and longitude).

- [ ] **Step 1: Write test:** `driversWithPosition` drops items whose latitude/longitude are null/undefined and keeps the rest; rendering `DriverMap` with one positioned and one unpositioned driver does not throw (mock `react-leaflet` `MapContainer`/`TileLayer`/`Marker` to plain divs and assert one marker).
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement: left list (name, vehicle, availability, active order, last GPS age, stale badge), right Leaflet map (OSM tiles `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png`, center TP.HCM 10.7769, 106.7009, zoom 12; marker popup with name + active order link). Import `leaflet/dist/leaflet.css` and fix the default marker icon paths for Vite. Under the map, a text fallback lists coordinates so the page is still useful offline. `refetchInterval: 10_000`.
- [ ] **Step 4:** `pnpm test` → PASS. **Verify** live: three demo drivers appear on the map near TP.HCM.
- [ ] **Step 5: Commit** `feat(fe): driver monitoring with live map`.

### Task 10: Users admin and driver profile creation

**Files:**
- Create: `fe_dispatcher/src/pages/UsersPage.tsx`, `fe_dispatcher/src/components/CreateUserModal.tsx`, `fe_dispatcher/src/components/CreateDriverProfileModal.tsx`, `fe_dispatcher/src/api/users.ts`
- Modify: remove `ReportsPage`, `SettingsPage` and any remaining mock from `App.tsx`; delete leftover dead code so `App.tsx` holds only routes/providers
- Test: `fe_dispatcher/src/components/CreateDriverProfileModal.test.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `Paged`, `UserSummary`.
- Produces: `listUsers(params: { page: number; size: number }): Promise<Paged<UserSummary>>`; `createUser(body: { username; email; password; role: Role }): Promise<UserSummary>`; `setUserStatus(id: string, status: "ACTIVE" | "INACTIVE" | "LOCKED"): Promise<void>` (`PATCH /api/users/{id}/status`, body `{status}`; the lock action sends `LOCKED`, unlock sends `ACTIVE`); `createDriverProfile(body: { userId; fullName; phoneNumber; licenseNumber; licenseClass; vehicleType; vehiclePlate; address? }): Promise<void>` (`POST /api/drivers`).

- [ ] **Step 1: Write test:** submitting `CreateDriverProfileModal` with empty `licenseNumber` shows a required-field error and does not call the API; with all fields it calls `createDriverProfile` with the selected user's `userId`.
- [ ] **Step 2:** `pnpm test` → FAIL.
- [ ] **Step 3:** Implement: users table (username, email, role, status, created) with lock/unlock action; `New user` modal (role select of all four roles); for a user with role DRIVER, a `Create driver profile` action opening the profile modal (vehicle type options `MOTORBIKE`, `CAR`, `TRUCK_2T`). Route `/users` wrapped in `RequireRole roles={["ADMIN"]}`.
- [ ] **Step 4:** `pnpm test` → PASS. **Verify** live as admin: create user `driver4` (DRIVER) → create its profile → it appears on the Drivers page and in the assign modal. Then `grep -rn "sample.dispatcher.jwt\|const drivers: DriverMonitoringItem\[\] = \[\|const orders = \[\|const exceptions = \[" fe_dispatcher/src` → no matches.
- [ ] **Step 5: Commit** `feat(fe): user administration and driver profile creation`.

### Task 11: Electron packaging

**Files:**
- Create: `fe_dispatcher/electron/main.ts`, `fe_dispatcher/electron/tsconfig.json`, `fe_dispatcher/electron-builder.yml`, `fe_dispatcher/.env.electron` (`VITE_API_BASE_URL=http://localhost:8080`)
- Modify: `fe_dispatcher/package.json` (`main`, scripts), `fe_dispatcher/.gitignore` (`release/`, `dist-electron/`)

**Interfaces:**
- Produces scripts: `electron:dev` (Vite on 5173 + Electron loading `http://localhost:5173`), `electron:build` (`vite build --mode electron` → compile `electron/main.ts` to `dist-electron/` → `electron-builder --dir`), output `release/mac-arm64/Logistics Dispatcher.app`.

- [ ] **Step 1:** `pnpm add -D electron electron-builder concurrently wait-on`. In `main.ts`: `protocol.registerSchemesAsPrivileged([{ scheme: "app", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } }])`; on ready, `protocol.handle("app", ...)` serving files from the packaged `dist/` (default to `index.html`); create `BrowserWindow` per Global Constraints and load `app://dispatcher/index.html` (or the dev URL when `process.env.ELECTRON_DEV_URL` is set); open external links in the system browser.
- [ ] **Step 2: Verify dev:** backend running → `pnpm electron:dev` → window opens, login as dispatcher works.
- [ ] **Step 3: Verify packaged:** `pnpm electron:build` → open the `.app` → login, Dashboard, Needs Action, Drivers map all load; DevTools network shows requests to `http://localhost:8080/api/...` with `Origin: app://dispatcher` and 200 responses.
- [ ] **Step 4: Commit** `feat(fe): package dispatcher as Electron desktop app`.

### Task 12: End-to-end demo check and docs

**Files:**
- Modify: `README.md` (new section `## Desktop Client (fe_dispatcher)`: prerequisites, `pnpm install`, `pnpm dev`, `pnpm electron:dev`, `pnpm electron:build`, server URL field, demo accounts; Roadmap row `PB-061`)

- [ ] **Step 1:** With a fresh stack (`docker compose up -d`, 5 services, `bash scripts/demo-data.sh`), run the spec §6 script in the packaged app: dispatcher → Dashboard → open an order → Needs Action manual assign → Drivers map; admin → create DRIVER user + profile → visible in assign modal; `customer` login refused with `This account cannot use the desktop client`.
- [ ] **Step 2:** `pnpm test`, `pnpm typecheck`, `pnpm build` all clean; `mvn -B install -pl services/order-service,services/api-gateway` green.
- [ ] **Step 3:** Update README.
- [ ] **Step 4: Commit** `docs: desktop client usage and PB-061`.
