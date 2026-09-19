/** Browser workflow checks with mocked APIs; no application database is used.
 * Start Vite on 5174 and Chromium with --remote-debugging-port=9223, then run
 * npm run test:menu-browser. Override MENU_ADMIN_TEST_URL / CHROME_DEBUG_URL if needed.
 * Uses Node's built-in WebSocket and Chromium's DevTools protocol; no dependencies.
 */
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
const base = process.env.MENU_ADMIN_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0; const pending = new Map(), errors = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) { const waiter = pending.get(message.id); pending.delete(message.id); message.error ? waiter.reject(message.error) : waiter.resolve(message.result); }
  else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence; pending.set(id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params }));
});
const evaluate = async expression => {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 150; i++) { if (await evaluate(`Boolean(${expression})`)) return; await new Promise(r => setTimeout(r, 40)); }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};
const click = async (text, tag = 'button') => {
  const selector = `Array.from(document.querySelectorAll(${JSON.stringify(tag)})).find(el => el.textContent.trim() === ${JSON.stringify(text)} && !el.disabled)`;
  await waitFor(selector); await evaluate(`${selector}.click()`);
  await evaluate(`new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())))`);
};
const link = async (text, nav) => click(text, nav ? `nav[aria-label="${nav}"] a` : 'a');
const field = async (label, value) => evaluate(`(() => {
  const el = Array.from(document.querySelectorAll('label')).find(l => l.textContent.startsWith(${JSON.stringify(label)})).querySelector('input,select,textarea');
  Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value').set.call(el, ${JSON.stringify(value)});
  el.dispatchEvent(new Event(el.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
})()`);
const loaded = () => waitFor(`!document.body.innerText.includes('Loading menu administration') && document.querySelector('.menu-admin-toolbar, .menu-admin-form, .menu-admin-section-heading')`);
const fixture = () => {
  window.prompts = []; window.acceptDiscard = true;
  window.confirm = message => { window.prompts.push(message); return window.acceptDiscard; };
  window.alert = message => window.prompts.push(message);
  window.writes = []; window.failWrite = null; window.failRead = false;
  const availability = { available: true, reason: 'AVAILABLE', evaluatedAt: '2026-09-21T02:00:00Z' };
  window.rows = ['main', 'lunch'].map((id, index) => ({
    collection: { id, version: 0, data: { name: index ? 'Lunch Special' : 'Main Menu', slug: id, description: null, status: 'PUBLISHED', active: true, timezone: 'UTC', dailyCutoffTime: index ? '14:30:00' : null, displayOrder: index, startsAt: null, endsAt: null } },
    memberships: [{ id: 'dish', version: 0, data: { collectionCategoryId: null, priceOverrideMinor: index ? 1590 : null, displayOrder: 0 } }],
    categories: [], schedules: [], availability,
    orderingAvailability: { ...availability, available: false, reason: 'RESTAURANT_CLOSED' }, restaurantTimezone: 'Australia/Melbourne', restaurantOpen: false,
  }));
  window.items = { items: ['dish', 'second'].map((id, index) => ({ id, name: index ? 'Second curry' : 'Shared curry', slug: id, description: 'A curry', categoryId: 'canonical', status: 'PUBLISHED', available: true, displayOrder: 0, collectionIds: index ? [] : ['main', 'lunch'], prices: [{ id: `price-${id}`, name: 'Standard', amount: '24.90' }], version: 0 })), categories: [{ id: 'canonical', name: 'Curry' }, { id: 'alt', name: 'Lunch category' }], collections: rows.map(r => ({ id: r.collection.id, name: r.collection.data.name })) };
  const realFetch = window.fetch;
  window.fetch = async (url, options = {}) => {
    if (!String(url).startsWith('/api')) return realFetch(url, options);
    if (String(url).endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (url === '/api/identity/refresh') return Response.json({ accessToken: 'test', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: 'admin', username: 'Admin', role: 'ADMIN' } });
    const write = options.method && options.method !== 'GET';
    if (write) {
      window.writes.push({ url, method: options.method, data: options.body ? JSON.parse(options.body) : null, headers: options.headers });
      if (window.failWrite) return Response.json({ message: window.failWrite.message }, { status: window.failWrite.status });
    }
    if (!write && window.failRead) return new Response(null, { status: 503 });
    if (url === '/api/staff/menu/items' && !write) return Response.json(window.items);
    if (String(url).startsWith('/api/staff/menu/items') && write) {
      const body = JSON.parse(options.body); let item = window.items.items.find(i => i.id === body.id);
      if (!item) { item = { ...body, id: 'created-item', version: 0 }; window.items.items.push(item); return Response.json({ id: item.id }, { status: 201 }); }
      Object.assign(item, body, { version: item.version + 1 }); return new Response(null, { status: 204 });
    }
    const root = '/api/staff/menu/collections';
    if (url === root && !write) return Response.json(window.rows);
    if (String(url).startsWith(root) && write) {
      const body = options.body ? JSON.parse(options.body) : null;
      const parts = String(url).slice(root.length).split('/').filter(Boolean);
      const row = window.rows.find(r => r.collection.id === parts[0]);
      if (!parts.length) { const collection = { id: 'created-collection', version: 0, data: body }; window.rows.push({ collection, memberships: [], categories: [], schedules: [], availability, orderingAvailability: availability, restaurantTimezone: 'Australia/Melbourne', restaurantOpen: true }); return Response.json(collection); }
      if (parts.length === 1) { row.collection = { ...row.collection, version: row.collection.version + 1, data: body }; return Response.json(row.collection); }
      const kind = parts[1] === 'items' ? 'memberships' : parts[1], childId = parts[2]?.split('?')[0] || `child-${kind}`;
      if (options.method === 'DELETE') { row[kind] = row[kind].filter(x => x.id !== childId); return new Response(null, { status: 204 }); }
      const resource = { id: childId, version: (body.version ?? -1) + 1, data: body }; const index = row[kind].findIndex(x => x.id === childId);
      if (index < 0) row[kind].push(resource); else row[kind][index] = resource;
      return Response.json(resource);
    }
    return Response.json({});
  };
};
try {
  await send('Runtime.enable'); await send('Page.enable');
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `(${fixture.toString()})()` });
  await send('Emulation.setDeviceMetricsOverride', { width: 1360, height: 1000, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: `${base}#/staff/menu` }); await loaded();
  assert.equal(await evaluate(`document.querySelector('h1')?.textContent`), 'Menu items');
  assert.equal(await evaluate(`document.querySelector('form') === null`), true);
  await link('Shared curry'); await loaded();
  assert.equal(await evaluate('location.hash'), '#/staff/menu/items/dish');
  assert.equal(await evaluate(`document.querySelector('table') === null`), true);
  await field('Name', 'Updated curry'); await click('Save changes');
  await waitFor(`document.body.innerText.includes('Item saved.') && !document.querySelector('fieldset')?.disabled`);
  assert.equal(await evaluate('items.items[0].name'), 'Updated curry');
  await link('Images', 'Item sections'); await loaded();
  await field('Photo description', 'An unsaved photo caption'); await evaluate('acceptDiscard = false');
  await link('Items', 'Menu navigation'); assert.equal(await evaluate('location.hash'), '#/staff/menu/items/dish/images');
  await evaluate('acceptDiscard = true'); await click('Cancel photo changes');
  await link('Overview', 'Item sections'); await loaded();
  await link('Pricing / variations', 'Item sections'); await loaded(); await field('Standard price', '25.90'); await click('Save changes');
  await waitFor(`items.items[0].prices[0].amount === '25.90' && !document.querySelector('fieldset')?.disabled`);
  assert.equal(await evaluate(`writes.at(-1).headers['X-CSRF-TOKEN']`), 'test');
  await evaluate('history.back()'); await loaded(); await waitFor(`location.hash === '#/staff/menu/items/dish/overview'`);
  await field('Name', 'Unsaved name'); await evaluate('acceptDiscard = false');
  await link('Collections', 'Menu navigation');
  assert.equal(await evaluate('location.hash'), '#/staff/menu/items/dish/overview');
  assert.equal(await evaluate(`document.querySelector('input').value`), 'Unsaved name');
  await evaluate('history.back()'); await waitFor(`prompts.length >= 3 && location.hash === '#/staff/menu/items/dish/overview'`);
  assert.equal(await evaluate('location.hash'), '#/staff/menu/items/dish/overview');
  await evaluate('acceptDiscard = true'); await click('Cancel changes');
  await evaluate('history.back()'); await waitFor(`location.hash === '#/staff/menu/items/dish/images'`); await loaded();
  await evaluate('history.forward()'); await waitFor(`location.hash === '#/staff/menu/items/dish/overview'`); await loaded();
  await link('Collections', 'Menu navigation'); await loaded();
  assert.equal(await evaluate('location.hash'), '#/staff/menu/collections');
  assert.equal(await evaluate(`document.querySelector('form') === null`), true);
  await link('Main Menu'); await loaded(); assert.equal(await evaluate(`document.querySelector('h1')?.textContent`), 'Main Menu');
  await link('Collections', 'Menu navigation'); await loaded(); await link('Lunch Special'); await loaded();
  await link('Availability', 'Collection sections'); await loaded();
  await send('Page.reload'); await loaded(); await waitFor(`document.querySelector('h1')?.textContent === 'Lunch Special'`);
  assert.equal(await evaluate('location.hash'), '#/staff/menu/collections/lunch/availability');
  assert.equal(await evaluate(`document.querySelector('a[aria-current="page"][href$="/availability"]').textContent`), 'Availability');
  const mainSnapshot = await evaluate('JSON.stringify(rows[0])');
  await link('Overview', 'Collection sections'); await loaded(); await field('Name', 'Lunch edited'); await click('Save overview');
  await waitFor(`rows[1].collection.data.name === 'Lunch edited' && !document.querySelector('fieldset')?.disabled`);
  assert.equal(await evaluate(`document.querySelector('input[type="time"]') === null`), true);
  await link('Categories', 'Collection sections'); await loaded(); await click('Add category placement'); await field('Category', 'alt'); await click('Save placement');
  await waitFor(`rows[1].categories.length === 1 && !document.querySelector('form')`);
  await link('Items', 'Collection sections'); await loaded(); await click('Add existing item'); await field('Category placement', 'child-categories'); await field('Price override', '0'); await click('Save membership');
  await waitFor(`rows[1].memberships.length === 2 && !document.querySelector('form')`);
  assert.equal(await evaluate('rows[1].memberships[1].data.priceOverrideMinor'), 0);
  assert.equal(await evaluate('rows[1].memberships[1].data.collectionCategoryId'), 'child-categories');
  await evaluate(`Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Remove membership') && b.textContent.includes('Second curry')).click()`);
  assert.match(await evaluate(`document.querySelector('.menu-admin-confirm').textContent`), /canonical menu item, its other collections and historical orders remain/);
  await click('Keep it'); assert.equal(await evaluate('rows[1].memberships.length'), 2);
  await evaluate(`Array.from(document.querySelectorAll('button')).find(b => b.textContent.includes('Remove membership') && b.textContent.includes('Second curry')).click()`);
  await click('Confirm removal'); await waitFor(`rows[1].memberships.length === 1 && !document.querySelector('.menu-admin-confirm')`);
  assert.equal(await evaluate('items.items.length'), 2);
  await link('Availability', 'Collection sections'); await loaded(); await field('Cutoff time', '14:00:00'); await click('Save availability');
  await waitFor(`rows[1].collection.data.dailyCutoffTime === '14:00:00' && !document.querySelector('fieldset')?.disabled`);
  assert.equal(await evaluate('rows[1].collection.data.timezone'), 'UTC');
  await click('Add schedule'); await field('Start time', '17:00'); await field('End time', '01:00'); await click('Save schedule');
  await waitFor(`rows[1].schedules.length === 1 && document.body.innerText.includes('Ends the following day')`);
  assert.equal(await evaluate('JSON.stringify(rows[0])'), mainSnapshot);
  await field('Starts at', '2026-12-02T12:00'); await field('Ends at', '2026-12-01T12:00');
  await evaluate(`failWrite = {status:400,message:'Collection end must follow start'}`); await click('Save availability');
  await waitFor(`document.querySelector('[role="alert"]')`);
  assert.match(await evaluate(`document.querySelector('[role="alert"]').textContent`), /end must follow start/);
  assert.equal(await evaluate(`Array.from(document.querySelectorAll('label')).find(l=>l.textContent.startsWith('Starts at')).querySelector('input').value`), '2026-12-02T12:00');
  await evaluate(`failWrite = {status:409,message:'Menu resource changed; reload before saving'}`); await click('Save availability');
  await waitFor(`document.body.innerText.includes('Menu resource changed; reload before saving')`);
  assert.equal(await evaluate(`document.querySelector('fieldset')?.disabled`), true);
  await evaluate('failWrite = null'); await click('Refresh data'); await waitFor(`!document.querySelector('fieldset')?.disabled`);
  await field('Cutoff time', '13:00:00'); await evaluate('failRead = true'); await click('Save availability');
  await waitFor(`document.body.innerText.includes('Saved successfully, but refreshing failed')`);
  assert.equal(await evaluate(`document.querySelector('fieldset')?.disabled`), true);
  await evaluate('failRead = false'); await click('Refresh data'); await waitFor(`!document.querySelector('fieldset')?.disabled`);
  await link('Collections', 'Menu navigation'); await loaded();
  for (const width of [1360, 820, 390]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false });
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, `No page overflow at ${width}px`);
    if (width === 390) assert.equal(await evaluate(`getComputedStyle(document.querySelector('tbody tr')).display`), 'block');
    if (process.env.MENU_ADMIN_SCREENSHOT_DIR) {
      const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
      await writeFile(`${process.env.MENU_ADMIN_SCREENSHOT_DIR}/menu-admin-${width}.png`, Buffer.from(screenshot.data, 'base64'));
    }
  }
  await link('Items', 'Menu navigation'); await loaded(); await link('Create item'); await loaded();
  await field('Name', 'New dish'); await field('Slug', 'new-dish'); await field('Description', 'A new dish'); await click('Create item');
  await waitFor(`location.hash === '#/staff/menu/items/created-item'`); await loaded();
  await link('Collections', 'Menu navigation'); await loaded(); await link('Create collection'); await loaded();
  await field('Name', 'Seasonal'); await field('Slug', 'seasonal'); await click('Create collection');
  await waitFor(`location.hash === '#/staff/menu/collections/created-collection'`);
  assert.equal(await evaluate('rows.at(-1).collection.data.name'), 'Seasonal');
  assert.deepEqual(errors, []);
  console.log('PASS: item index/detail/save/pricing/create; collection index/Main Menu/Lunch Special/metadata/create; membership/category/zero override/removal; availability/cutoff/overnight schedule; unsaved links and Back; deep-link refresh; validation and committed-write reload protection; desktop/tablet/mobile layouts.');
} finally {
  await send('Page.close').catch(() => {});
  ws.close();
}
