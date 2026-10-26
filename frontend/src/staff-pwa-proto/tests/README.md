# Prototype tests

All mutation browser tests use in-memory API fixtures. No application database is used.
`prototype.browser.mjs` also blocks actual `/api/*` network traffic through CDP.
The transplanted Web suites intercept API calls in their own fixtures.

From `frontend/`:

```sh
npm run test:staff-proto
npm test
npm run build:staff-proto
npm run build
npm run test:staff-pwa-build
```

Start `npm run dev:staff-proto` on 5175 and an isolated Chrome instance:

```sh
google-chrome --headless=new --no-sandbox --disable-gpu --remote-debugging-port=9225 --user-data-dir=/tmp/nakorn-staff-proto-chrome about:blank
npm run test:staff-proto-browser
```

The browser runner executes the comprehensive prototype suite followed by the
adapted menu, order-draft and settings suites sequentially. Do not run foreground
or audio browser suites concurrently: another page taking focus pauses the
protected PWA's foreground monitor.

Production installation checks:

```sh
npm run preview:staff-proto
npm run test:staff-proto-install-browser
```

Defaults: dev URL `http://127.0.0.1:5175/staff-pwa-proto/`, preview URL
`http://127.0.0.1:5176/staff-pwa-proto/`, CDP `http://127.0.0.1:9225`.
Override with `PROTO_TEST_URL`, `PROTO_PREVIEW_URL`, `CHROME_DEBUG_URL`.
The adapted suites additionally support `MENU_ADMIN_TEST_URL`, `ORDERS_TEST_URL`
and `SETTINGS_TEST_URL`. Screenshots/results default to `/tmp/nakorn-staff-proto`;
use `PROTO_ARTIFACTS` to change it.

`source-baseline.json` fingerprints the original workflow dependency graph.
`protected-baseline.json` fingerprints the existing PWA, Android sources, Web
shell/styles and original build entry. Unit tests assert these remain unchanged.
Reference domain API/model tests execute against the prototype-local modules.

These are mocked browser and Node contract tests, not real-backend integration
or physical Safari/Android-device verification. No hardware printing or audio
recovery is implemented or claimed in this prototype.

CI/CD deployment helpers and rollback tests: `python3 -B -m unittest discover -s
frontend/src/staff-pwa-proto/deploy -p 'test_*.py' -v` from the repository root.
Tests use temporary directories and injected smoke checks; no live activation occurs.
