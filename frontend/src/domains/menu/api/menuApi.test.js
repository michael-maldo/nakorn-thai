import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { archiveMenuItem, getMenuCollection, getMenuCollections, getStaffCsrf, saveMenuItem } from './menuApi.js';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('public menu uses selected collection API and forwards cancellation without authorization', async () => {
  const controller = new AbortController();
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/menu/collections/seasonal-menu/items');
    assert.equal(options.signal, controller.signal);
    assert.equal(options.headers.Authorization, undefined);
    return Response.json({ items: [{ name: 'Database curry' }] });
  };
  assert.equal((await getMenuCollection('seasonal-menu', controller.signal)).items[0].name, 'Database curry');
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
      if (url === '/api/staff/menu/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'token' });
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
    if (url === '/api/staff/menu/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    assert.equal(url, '/api/staff/menu/items/dish-id?version=4');
    assert.equal(options.method, 'DELETE');
    return new Response(null, { status: 204 });
  };
  assert.equal(await archiveMenuItem({ id: 'dish-id', version: 4 }, 'Basic test', { headerName: 'X-CSRF-TOKEN', token: 'token' }), null);
});

for (const [status, message] of [[401, /Sign-in failed/], [409, /changed or its slug/], [502, /unavailable/]]) {
  test(`HTTP ${status} shows an actionable error even when the proxy returns HTML`, async () => {
    globalThis.fetch = async () => new Response('<html>Error</html>', { status });
    await assert.rejects(getMenuCollection('seasonal-menu'), message);
  });
}

 test('HTML served by a missing API proxy produces a readable error', async () => {
  globalThis.fetch = async () => new Response('<html>Restaurant homepage</html>');
  await assert.rejects(getMenuCollection('seasonal-menu'), /invalid response/);
});

test('image upload lets the browser generate multipart boundaries and sends CSRF', async () => {
  const { saveMenuImage } = await import('./menuApi.js');
  const body = new FormData(); body.append('focusX', '25'); body.append('version', '2');
  globalThis.fetch = async (url, options) => {
    if (url === '/api/staff/menu/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    assert.equal(url, '/api/staff/menu/items/dish/image');
    assert.equal(options.headers['Content-Type'], undefined);
    assert.equal(options.headers['X-CSRF-TOKEN'], 'token');
    assert.equal(options.body, body);
    return new Response(null, { status: 204 });
  };
  await saveMenuImage('dish', body, 'Basic test', { headerName: 'X-CSRF-TOKEN', token: 'token' });
});


test('each write refreshes the token after intervening authenticated reads', async () => {
  const { getStaffMenu } = await import('./menuApi.js');
  let generation = 0;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push([url, options.method || 'GET']);
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: `fresh-${++generation}` });
    if (!options.method) { generation++; return Response.json({ items: [] }); }
    assert.equal(options.headers['X-CSRF-TOKEN'], `fresh-${generation}`);
    return new Response(null, { status: 204 });
  };
  const stale = { headerName: 'X-CSRF-TOKEN', token: 'login-token' };
  await getStaffMenu('Basic test');
  await saveMenuItem({ id: 'dish', version: 1 }, 'Basic test', stale);
  await getStaffMenu('Basic test');
  await archiveMenuItem({ id: 'dish', version: 2 }, 'Basic test', stale);
  assert.deepEqual(calls.map((call) => call[1]), ['GET', 'GET', 'PUT', 'GET', 'GET', 'DELETE']);
});

test('failed token refresh prevents the write', async () => {
  let calls = 0;
  globalThis.fetch = async (url) => {
    calls++; assert.equal(url, '/api/staff/menu/csrf');
    return new Response(null, { status: 401 });
  };
  await assert.rejects(saveMenuItem({ id: 'dish' }, 'Basic test', { token: 'old' }), /Sign-in failed/);
  assert.equal(calls, 1);
});

test('collection discovery consumes backend slugs and preserves unavailable collections', async () => {
  const controller = new AbortController();
  const collections = [{ id: 'new-id', slug: 'new-seasonal-offer', name: 'Seasonal', availability: { available: false, reason: 'OUTSIDE_SCHEDULE' } }];
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/menu/collections'); assert.equal(options.signal, controller.signal);
    return Response.json(collections);
  };
  assert.deepEqual(await getMenuCollections(controller.signal), collections);
});

test('invalid collection discovery fails instead of inventing a fallback menu', async () => {
  globalThis.fetch = async () => Response.json({ items: [] });
  await assert.rejects(getMenuCollections(), /invalid collection list/);
});

// Cutoff management reuses collection writes, identity and a freshly acquired CSRF token.
test('collection cutoff save preserves independent schedule timezone and optimistic version', async () => {
  const { saveCollectionConfiguration } = await import('./menuApi.js');
  const original = globalThis.fetch, calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return Response.json(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'fresh' } : { id: 'collection', version: 4 });
  };
  try {
    await saveCollectionConfiguration({ id: 'collection', version: 3, data: { name: 'Menu', timezone: 'UTC', dailyCutoffTime: '14:30:00' } }, 'Basic test');
    assert.equal(calls[0].url, '/api/staff/menu/csrf');
    assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'fresh');
    assert.equal(calls[1].options.credentials, 'same-origin');
    const body = JSON.parse(calls[1].options.body);
    assert.equal(body.dailyCutoffTime, '14:30:00'); assert.equal(body.timezone, 'UTC'); assert.equal(body.version, 3);
  } finally { globalThis.fetch = original; }
});

test('staff collection listing retains drafts, memberships and server availability', async () => {
  const { getCollectionConfiguration } = await import('./menuApi.js');
  const rows = [{ collection: { id: 'draft', data: { status: 'DRAFT', active: false } }, memberships: [{ id: 'item' }],
    availability: { available: false, reason: 'NOT_PUBLISHED' }, restaurantTimezone: 'Australia/Perth' }];
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/staff/menu/collections'); assert.equal(options.headers.Authorization, 'Basic test');
    return Response.json(rows);
  };
  assert.deepEqual(await getCollectionConfiguration('Basic test'), rows);
});

test('create collection sends metadata and null version with fresh CSRF', async () => {
  const { saveCollectionConfiguration } = await import('./menuApi.js');
  const data = { name: 'Seasonal', slug: 'seasonal', description: null, status: 'DRAFT', active: false,
    displayOrder: 4, timezone: 'UTC', startsAt: '2026-12-01T00:00:00Z', endsAt: null, dailyCutoffTime: '14:30:00' };
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    assert.equal(url, '/api/staff/menu/collections'); assert.equal(options.method, 'POST');
    assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh');
    assert.deepEqual(JSON.parse(options.body), { ...data, version: null });
    return Response.json({ id: 'new', version: 0 }, { status: 201 });
  };
  assert.equal((await saveCollectionConfiguration({ data, version: null }, 'Basic test')).id, 'new');
});

for (const override of [null, 0, 1590]) {
  test(`membership save preserves ${override} price override and collection-specific placement`, async () => {
    const { saveCollectionMembership } = await import('./menuApi.js');
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
      assert.equal(url, '/api/staff/menu/collections/lunch/items/shared-item'); assert.equal(options.method, 'PUT');
      assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh');
      assert.deepEqual(JSON.parse(options.body), { collectionCategoryId: 'lunch-placement', priceOverrideMinor: override, displayOrder: 3, version: 7 });
      return Response.json({ id: 'shared-item', version: 8 });
    };
    await saveCollectionMembership('lunch', 'shared-item', { version: 7, data: { collectionCategoryId: 'lunch-placement', priceOverrideMinor: override, displayOrder: 3 } }, 'Basic test');
  });
}

for (const kind of ['schedules', 'categories', 'items']) {
  test(`removing ${kind} targets only the selected collection and resource version`, async () => {
    const { removeCollectionChild } = await import('./menuApi.js');
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
      assert.equal(url, `/api/staff/menu/collections/selected/${kind}/resource?version=2`);
      assert.equal(options.method, 'DELETE'); assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh');
      return new Response(null, { status: 204 });
    };
    await removeCollectionChild('selected', kind, { id: 'resource', version: 2 }, 'Basic test');
  });
}

for (const kind of ['schedules', 'categories']) for (const id of [undefined, 'existing']) {
  test(`${kind} ${id ? 'update' : 'create'} preserves its payload and version`, async () => {
    const { saveCollectionChild } = await import('./menuApi.js');
    const data = kind === 'schedules' ? { ruleType: 'SPECIFIC_DATE', dayOfWeek: null, specificDate: '2026-12-24', startTime: '17:00', endTime: '01:00', active: false, displayOrder: 2 } : { categoryId: 'canonical', displayOrder: 2 };
    globalThis.fetch = async (url, options) => {
      if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
      assert.equal(url, `/api/staff/menu/collections/selected/${kind}${id ? '/existing' : ''}`);
      assert.equal(options.method, id ? 'PUT' : 'POST');
      assert.deepEqual(JSON.parse(options.body), { ...data, version: id ? 3 : null });
      return Response.json({ id: id || 'new', version: id ? 4 : 0 });
    };
    await saveCollectionChild('selected', kind, { id, version: id ? 3 : null, data }, 'Basic test');
  });
}

test('collection validation and stale-edit errors retain server explanation and status', async () => {
  const { saveCollectionConfiguration } = await import('./menuApi.js');
  for (const [status, message] of [[400, 'Supply distinct start and end times, or neither'], [409, 'Menu resource changed; reload before saving']]) {
    globalThis.fetch = async url => url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' }) : Response.json({ message }, { status });
    await assert.rejects(saveCollectionConfiguration({ id: 'collection', version: 2, data: {} }, 'Basic test'), error => error.status === status && error.message === message);
  }
});

test('item option writes keep prices and versions on the assignment with fresh CSRF', async () => {
  const { saveItemOptionGroup, createItemOptionGroup, saveSharedOption, removeItemOptionGroup } = await import('./menuApi.js');
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: `csrf-${calls.length}` });
    assert.equal(options.credentials, 'same-origin');
    assert.equal(options.headers['X-CSRF-TOKEN'], `csrf-${calls.length - 1}`);
    return options.method === 'DELETE' ? new Response(null, { status: 204 }) : Response.json({ id: 'group', version: 4 });
  };
  const assignment = { minSelections: 1, maxSelections: 1, displayOrder: 0, version: 3, groupVersion: 2, prices: [{ optionId: 'beef', priceDeltaMinor: 200 }] };
  await saveItemOptionGroup('dish', 'group', assignment, 'Basic test');
  assert.equal(calls[1].url, '/api/staff/menu/items/dish/option-groups/group');
  assert.deepEqual(JSON.parse(calls[1].options.body), assignment);
  await createItemOptionGroup('dish', { name: 'Protein', options: [{ name: 'Beef', code: 'beef', priceDeltaMinor: 300 }] }, 'Basic test');
  assert.equal(calls[3].url, '/api/staff/menu/items/dish/option-groups');
  await saveSharedOption('group', { id: 'beef', version: 1, data: { name: 'Beef', code: 'beef', active: true, displayOrder: 0 } }, 'Basic test');
  assert.equal(JSON.parse(calls[5].options.body).priceDeltaMinor, undefined);
  await removeItemOptionGroup('dish', { id: 'group', version: 4 }, 'Basic test');
  assert.equal(calls[7].url, '/api/staff/menu/items/dish/option-groups/group?version=4');
});
