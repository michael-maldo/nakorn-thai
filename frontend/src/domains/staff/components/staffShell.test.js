import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

let server, StaffShell, LoginPage, AuthProvider, identity;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false, watch: null }, appType: 'custom', logLevel: 'error' });
  StaffShell = (await server.ssrLoadModule('/src/domains/staff/components/StaffShell.jsx')).default;
  LoginPage = (await server.ssrLoadModule('/src/domains/identity/pages/LoginPage.jsx')).default;
  ({ AuthProvider } = await server.ssrLoadModule('/src/domains/identity/model/AuthContext.jsx'));
  identity = await server.ssrLoadModule('/src/domains/identity/api/identityApi.js');
});
after(async () => { await server?.close(); });

async function renderShell(role, hash = '#/staff') {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async url => Response.json(url.endsWith('/csrf')
    ? { headerName: 'X-CSRF-TOKEN', token: 'csrf' }
    : { accessToken: 'test', expiresAt: new Date(Date.now() + 900000).toISOString(), user: { username: 'staff-member', role } });
  try { await identity.login('staff-member', 'test-password'); }
  finally { globalThis.fetch = originalFetch; }
  return renderToStaticMarkup(createElement(AuthProvider, null,
    createElement(StaffShell, { hash }, createElement('main', null, 'Workspace content'))));
}

test('login screen exposes labelled credential fields and no dashboard navigation', () => {
  const html = renderToStaticMarkup(createElement(AuthProvider, null, createElement(LoginPage)));
  assert.match(html, /autoComplete="username"/);
  assert.match(html, /type="password"/);
  assert.match(html, /autoComplete="current-password"/);
  assert.match(html, /Sign in/);
  assert.doesNotMatch(html, /aria-label="Staff navigation"/);
});
test('admin shell has account and sidebar navigation with nested menu active state', async () => {
  const html = await renderShell('ADMIN', '#/staff/menu/items');
  assert.match(html, /aria-label="Staff account"/);
  assert.match(html, /aria-label="Staff navigation"/);
  assert.match(html, /href="#\/staff\/menu" aria-current="page"/);
  assert.match(html, /Staff accounts/);
  assert.match(html, /aria-expanded="false" aria-controls="staff-navigation"/);
  assert.match(html, /Skip to content/);
});
test('FOH shell exposes reservations and functions without administrator links', async () => {
  const html = await renderShell('FOH');
  assert.match(html, /Reservations/);
  assert.match(html, /Function enquiries/);
  assert.doesNotMatch(html, /href="#\/staff\/(menu|users|restaurant)"/);
});
test('BOH shell retains overview and sign out without unsupported staff screens', async () => {
  const html = await renderShell('BOH');
  assert.match(html, /href="#\/staff" aria-current="page"/);
  assert.match(html, /Sign out/);
  assert.doesNotMatch(html, /href="#\/staff\/(menu|users|restaurant|reservations|functions)"/);
});
