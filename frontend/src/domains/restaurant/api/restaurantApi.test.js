import test from 'node:test';
import assert from 'node:assert/strict';
import { restaurantRequest, getRestaurantAvailability } from './restaurantApi.js';
import { login, logout } from '../../identity/api/identityApi.js';

test('public availability uses server response without browser schedule calculations', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/restaurant/availability'); assert.equal(options.cache, 'no-store');
    return Response.json({ timezone: 'Australia/Melbourne', open: false, evaluatedAt: '2026-09-07T08:00:00Z' });
  };
  try { assert.equal((await getRestaurantAvailability()).open, false); } finally { globalThis.fetch = original; }
});
test('staff writes use current identity and fresh CSRF, preserving version and overnight times', async () => {
  const original = globalThis.fetch, calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh-csrf' });
    if (url.endsWith('/login')) return Response.json({ accessToken: 'test-access', user: { role: 'ADMIN' }, expiresAt: new Date(Date.now() + 600000).toISOString() });
    return new Response(null, { status: 204 });
  };
  try {
    await login('admin', 'test-password'); calls.length = 0;
    await restaurantRequest('/hours/id', { method: 'PUT', body: { dayOfWeek: 1, opensAt: '17:00', closesAt: '01:00', active: true, version: 2 } });
    assert.equal(calls[0].url, '/api/staff/restaurant/csrf');
    assert.equal(calls[1].options.headers.Authorization, 'Bearer test-access');
    assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'fresh-csrf');
    assert.equal(calls[1].options.credentials, 'same-origin');
    assert.equal(JSON.parse(calls[1].options.body).closesAt, '01:00');
    assert.equal(JSON.parse(calls[1].options.body).version, 2);
    calls.length = 0;
    await restaurantRequest('/closed-dates/id?version=3', { method: 'DELETE' });
    assert.equal(calls[0].url, '/api/staff/restaurant/csrf');
    assert.equal(calls[1].options.method, 'DELETE');
    await logout();
  } finally { globalThis.fetch = original; }
});
test('server errors remain actionable and failed CSRF prevents a write', async () => {
  const original = globalThis.fetch; let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ message: 'Schedule entry changed; reload before saving' }, { status: 409 }); };
  try {
    await assert.rejects(restaurantRequest('/settings', { method: 'PUT', body: {} }), /reload before saving/);
    assert.equal(calls, 1);
  } finally { globalThis.fetch = original; }
});
