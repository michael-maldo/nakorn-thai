import test from 'node:test';
import assert from 'node:assert/strict';
import { login, logout, refreshAccess, fetchWithIdentity, identityState } from './identityApi.js';
const response = (body, status = 200) => new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const session = (accessToken) => ({ accessToken, expiresAt: new Date(Date.now() + 900000).toISOString(), user: { username: 'admin', role: 'ADMIN' } });
test('JWT requests refresh once on 401 and logout clears identity', async () => {
  const original = globalThis.fetch; let reads = 0; let refreshes = 0;
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    if (url.endsWith('/login')) { assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf'); return response(session('first')); }
    if (url.endsWith('/refresh')) { refreshes++; return response(session('second')); }
    if (url.endsWith('/logout')) return response(null, 204);
    reads++; assert.equal(options.headers.Authorization, reads === 1 ? 'Bearer first' : 'Bearer second');
    return response({}, reads === 1 ? 401 : 200);
  };
  try {
    await login('admin', 'password');
    assert.equal((await fetchWithIdentity('/api/staff/menu/items', { headers: { Authorization: 'Bearer first' } })).status, 200);
    assert.equal(refreshes, 1); assert.equal(reads, 2);
    await logout(); assert.equal(identityState().user, null); assert.equal(identityState().accessToken, '');
  } finally { globalThis.fetch = original; }
});
test('concurrent refreshes share a request and failure clears identity', async () => {
  const original = globalThis.fetch; let count = 0; let fail = false;
  globalThis.fetch = async (url) => {
    if (url.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    count++; return response(fail ? {} : session('rotated'), fail ? 401 : 200);
  };
  try {
    await Promise.all([refreshAccess(), refreshAccess(), refreshAccess()]);
    assert.equal(count, 1); assert.equal(identityState().accessToken, 'rotated');
    fail = true; await assert.rejects(refreshAccess(), /sign in/i);
    assert.equal(identityState().user, null);
  } finally { globalThis.fetch = original; }
});
