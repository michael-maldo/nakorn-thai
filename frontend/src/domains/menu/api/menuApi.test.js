import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { archiveMenuItem, getSignatureDishes, getStaffCsrf, saveMenuItem } from './menuApi.js';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('public menu uses collection API and forwards cancellation without credentials', async () => {
  const controller = new AbortController();
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/menu/collections/signature-dishes/items');
    assert.equal(options.signal, controller.signal);
    assert.equal(options.headers.Authorization, undefined);
    return Response.json({ items: [{ name: 'Database curry' }] });
  };
  assert.equal((await getSignatureDishes(controller.signal)).items[0].name, 'Database curry');
});

test('staff gets a CSRF token with explicit authentication', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/staff/menu/csrf');
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers.Authorization, 'Basic test');
    return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'token' });
  };
  assert.equal((await getStaffCsrf('Basic test')).token, 'token');
});

for (const id of [undefined, 'dish-id']) {
  test(`save chooses ${id ? 'update' : 'create'} and sends security and version fields`, async () => {
    const dish = { id, name: 'Curry', version: id ? 3 : null, collectionIds: ['collection'] };
    globalThis.fetch = async (url, options) => {
      assert.equal(url, `/api/staff/menu/items${id ? '/dish-id' : ''}`);
      assert.equal(options.method, id ? 'PUT' : 'POST');
      assert.equal(options.headers.Authorization, 'Basic test');
      assert.equal(options.headers['X-CSRF-TOKEN'], 'token');
      assert.equal(JSON.parse(options.body).version, dish.version);
      return id ? new Response(null, { status: 204 }) : Response.json({ id: 'new' }, { status: 201 });
    };
    const saved = await saveMenuItem(dish, 'Basic test', { headerName: 'X-CSRF-TOKEN', token: 'token' });
    assert.deepEqual(saved, id ? null : { id: 'new' });
  });
}

test('archive includes the version and does not parse an empty response', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/staff/menu/items/dish-id?version=4');
    assert.equal(options.method, 'DELETE');
    return new Response(null, { status: 204 });
  };
  assert.equal(await archiveMenuItem({ id: 'dish-id', version: 4 }, 'Basic test', { headerName: 'X-CSRF-TOKEN', token: 'token' }), null);
});

for (const [status, message] of [[401, /Sign-in failed/], [409, /changed or its slug/], [502, /unavailable/]]) {
  test(`HTTP ${status} shows an actionable error even when the proxy returns HTML`, async () => {
    globalThis.fetch = async () => new Response('<html>Error</html>', { status });
    await assert.rejects(getSignatureDishes(), message);
  });
}

 test('HTML served by a missing API proxy produces a readable error', async () => {
  globalThis.fetch = async () => new Response('<html>Restaurant homepage</html>');
  await assert.rejects(getSignatureDishes(), /invalid response/);
});

test('image upload lets the browser generate multipart boundaries and sends CSRF', async () => {
  const { saveMenuImage } = await import('./menuApi.js');
  const body = new FormData(); body.append('focusX', '25'); body.append('version', '2');
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/staff/menu/items/dish/image');
    assert.equal(options.headers['Content-Type'], undefined);
    assert.equal(options.headers['X-CSRF-TOKEN'], 'token');
    assert.equal(options.body, body);
    return new Response(null, { status: 204 });
  };
  await saveMenuImage('dish', body, 'Basic test', { headerName: 'X-CSRF-TOKEN', token: 'token' });
});
