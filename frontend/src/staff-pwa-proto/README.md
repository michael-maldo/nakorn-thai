# Nakorn Thai Staff PWA Prototype

Independent evaluation application at `/staff-pwa-proto/`. It does not replace
Staff Web, `/staff/`, Android Staff or the customer website. See
[DEPLOYMENT.md](DEPLOYMENT.md) for the independent VPS release and domain boundary.

## Run and build

From `frontend/`, using the existing locked dependencies:

```sh
npm run dev:staff-proto
```

Open `http://127.0.0.1:5175/staff-pwa-proto/`. API/media requests proxy to the
existing backend on `127.0.0.1:8080`; real UI actions therefore operate against
that configured backend. Browser mutation tests use synthetic APIs instead.

```sh
npm run build:staff-proto
npm run preview:staff-proto
```

Preview: `http://127.0.0.1:5176/staff-pwa-proto/`. Output:
`frontend/dist-staff-pwa-proto/`, separate from the existing `dist/` build.
Preview is for static/installation evaluation; it has no backend proxy configured.
The dedicated `deploy-staff-pwa-proto.yml` workflow builds/deploys only this
prototype. The existing workflow excludes prototype-only paths. See DEPLOYMENT.md.

The ordinary `npm run build` still builds the existing applications independently.
The prototype has its own public directory, so its manifest/icons do not enter
those applications' build output.

## Structure and architecture

- `main.jsx`: prototype-only React tree, login and access boundary.
- `StaffProtoApp.jsx`: Android-inspired header, collapsible sidebar, drawer,
  persistent footer, Manage sheet and role-filtered feature mounting.
- `app/`: independent route registry and guarded hash/history handling.
- `features/`: prototype-local snapshots of the actual Staff Web workflow
  dependency graph, preserving domain ownership (`identity`, `ordering`, `menu`,
  `reservation`, `restaurant`, `payment`, `staff`). Local snapshots allow adaptation
  without changes to protected application code.
- `services/mutations.js`: identical in-flight management writes coalesce before
  CSRF acquisition. Completed/failed writes are never cached or automatically
  replayed. Order writes retain the existing serialized queue and ownership guards.
- `components/`: prototype SVG icons and native-style modal management sheet.
- `styles/prototype.css`: Android palette, typography hierarchy, 208px/72px rail,
  48px controls, cards, responsive tables, dialogs and safe-area handling.
- `pwa/`, `public/`: independent worker generation, registration, manifest/icons.
- `tests/`: source fingerprints, API/model contracts, synthetic browser workflows,
  responsive checks and production installation verification.

The prototype copies only the transitive workflow modules required by Staff Web's
active staff routes, plus test dependencies. The original snapshots are recorded
in `tests/source-baseline.json`. Small pure/API modules are deliberately local as
well: this isolates identity listeners, token state, menu guards, order API queues
and draft state. Four existing menu presentation JPG assets are imported unchanged.
There is no new common architecture and no change to original domain modules.

## Visual reference

Inspected Android resources: `activity_main.xml`, `fragment_orders.xml`,
`item_order.xml`, `sheet_manage.xml`, `menu/footer.xml`, `values/colors.xml`,
`values/dimens.xml` and `values/themes.xml`.

The prototype uses the native green `#236653`, ink `#26343D`, muted `#52626D`,
selected `#E4F0EB`, board `#F6F8FA`, line `#E0E6EA` and white card surfaces.
It adapts the native app bar/sidebar/drawer/Manage sheet/footer patterns rather
than mounting the desktop Web shell. The footer exposes Home, Orders and Manage;
no new analytics backend/reporting feature is invented. Native-only timekeeping
and printing are not Staff Web parity requirements.

## Routing and isolation

Prototype route examples:

- `/staff-pwa-proto/#/staff`
- `/staff-pwa-proto/#/staff/orders`
- `/staff-pwa-proto/#/staff/orders?order=<uuid>`
- `/staff-pwa-proto/#/staff/menu/items/<id>/options`
- `/staff-pwa-proto/#/staff/menu/collections/<id>/availability`

Hash syntax intentionally matches copied workflow links; the document URL and
router are independent. Neither existing router is imported. Unknown routes show
an unavailable view; unauthorized routes do not mount their feature or fetch its
protected data. Dirty menu/settings navigation supports Stay/Discard, native
Back/Forward restoration, sign-out guards and beforeunload warnings.

Manifest ID/start/scope and worker scope: `/staff-pwa-proto/`.
Worker script: `/staff-pwa-proto/sw.js`.
Cache namespace: `nakorn-proto-staff-<asset-hash>`.
All build assets use `/staff-pwa-proto/assets/`, independent of customer assets.
Sidebar preference: `nakorn-staff-pwa-proto:sidebar`.
History marker: `protoMenuPosition`.
No credentials, configuration secrets, orders or draft data are persisted by the
prototype. In-memory feature state resets on identity/role/session changes while
the shell retains external focus.

The distinct cache prefix is important: the existing PWA deletes `nakorn-staff-*`
caches. Prototype cache cleanup deletes only `nakorn-proto-staff-*` entries.
Only build-generated immutable JS/CSS are cached. No API, HTML, customer, payment
or media response is cached. Online access is required; this is not offline order
processing. Worker changes wait for deliberate activation through the update UI.

### Authentication boundary

Authentication preserves the existing server cookie paths, HttpOnly refresh
credentials, CSRF endpoints, JWT refresh and role enforcement. Access state is
prototype-local memory. The `nakorn-staff-refresh` Web Lock is intentionally retained
for coordination with the same backend's rotating refresh cookie; it is not a
storage key or prototype notification channel.

**Same-origin browser tabs share the backend refresh-session cookie.** Independent
frontend state cannot provide simultaneous independent server logins on that origin
without a backend contract change, which was not made. Signing in/out or revoking
accounts can affect other same-origin applications through the existing backend
session rules. Use a separate browser profile for isolated manual evaluation.

## Orders, notifications and printing

Prototype order state/polling/serialization/drafts are local copies of Staff Web's
implementation, not imports from the protected PWA. There is one queue poller in
the mounted order workspace; summaries use that loaded data. The dashboard and
other pages do not start another queue poller. Development React StrictMode can
repeat mount reads; the production browser check confirms one initial queue read.

Active/history filtering, direct lookup, role-specific transitions, PayID receipt
confirmation, PayPal check and read-only financial review use existing contracts.
History is completed/cancelled orders *placed within the last 24 hours*, capped at
200; it is not an all-time order archive. Finance review does not issue refunds.

Staff Web has no PWA audio recovery/acknowledgement engine to migrate. No audio,
push notification, locked-screen recovery or background alarm engine is introduced.
No original PWA notification code is imported. Epson hardware printing is absent
and disabled; the Manage sheet explicitly says printing is disabled.

## Verification and limitations

See [PARITY.md](PARITY.md), [VERIFICATION.md](VERIFICATION.md) and
[tests/README.md](tests/README.md).

All feature families are implemented. Browser verification uses synthetic API
responses and is separate from real backend integration/authorization proof.
Production worker installation is tested in Chromium. Physical iPad Safari,
Android tablet interaction, OS installation and live-provider behavior have not
been certified. No production data, provider credentials or hardware was used.

The original manual release remains independent. Dedicated CI/CD now handles
prototype-only pushes and manual dispatch without publishing other applications.
