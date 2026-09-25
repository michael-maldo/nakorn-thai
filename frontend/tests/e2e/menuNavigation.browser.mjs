/** Browser workflow checks with mocked APIs; no application database is used.
 * Start Vite on 5174 and Chromium with --remote-debugging-port=9223, then run
 * npm run test:menu-navigation-browser. Override MENU_NAVIGATION_TEST_URL / CHROME_DEBUG_URL if needed.
 * Uses Node's built-in WebSocket and Chromium's DevTools protocol; no dependencies.
 */
import assert from 'node:assert/strict';
const base = process.env.MENU_NAVIGATION_TEST_URL || 'http://127.0.0.1:5174/';
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
  throw new Error(`Timed out: ${expression}\n${await evaluate(`JSON.stringify({ y: scrollY, current: document.querySelector('.menu-category-list [aria-current]')?.dataset.categoryId, focused: document.activeElement?.outerHTML })`)}`);
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
const fixture = () => {
  const availability = { available: true, reason: 'AVAILABLE', evaluatedAt: '2026-09-25T01:00:00Z' };
  const collections = ['main', 'lunch'].map(id => ({ id, slug: id, name: id === 'main' ? 'Main Menu' : 'Lunch Special', availability }));
  function menu(collection) {
    const categories = (collection.id === 'main'
      ? ['Entree', 'Soup', 'Salads', 'Curries', 'Stir fried', 'Noodles', 'Rice', 'Seafood', 'Vegetarian', 'Sides', 'Desserts', 'Drinks']
      : ['Lunch dishes']).map((name, index) => ({ id: `${collection.id}-${index}`, name, displayOrder: index }));
    return { ...collection, categories: [...categories, { id: 'empty', name: 'Empty category' }], items: categories.flatMap(category => [0, 1, 2, 3].map(index => ({
      id: `${category.id}-${index}`, name: `${category.name} dish ${index + 1}`, description: 'Fresh Thai flavours.', category,
      displayOrder: index, available: true, optionGroups: [],
      variations: [{ id: `v-${category.id}-${index}`, name: 'Standard', priceMinor: 1990, available: true, defaultVariation: true }],
    }))) };
  }
  const realFetch = window.fetch;
  window.fetch = async (url, options) => {
    if (!String(url).startsWith('/api')) return realFetch(url, options);
    if (url === '/api/menu/collections') return Response.json(collections);
    if (url === '/api/orders/options') return Response.json({ enabled: true });
    const collection = collections.find(c => url === `/api/menu/collections/${c.slug}/items`);
    if (collection) return Response.json(menu(collection));
    return Response.json({}, { status: 401 });
  };
};
const current = `document.querySelector('.menu-category-list [aria-current="location"]')?.dataset.categoryId`;
const list = `document.querySelector('.menu-category-list')`;
const buttonRect = async selector => evaluate(`(() => { const r = document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; })()`);
try {
  await send('Runtime.enable'); await send('Page.enable');
  await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await send('Emulation.setDeviceMetricsOverride', { width: 900, height: 900, deviceScaleFactor: 1, mobile: false });
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `(${fixture.toString()})();` });
  await send('Page.navigate', { url: `${base}#/menu` });
  await waitFor(`${list}?.children.length === 12`);
  assert.equal(await evaluate(`${list}.innerText.includes('Main Menu')`), false);
  assert.equal(await evaluate(`${list}.innerText.includes('Empty category')`), false);
  await waitFor(`document.querySelector('[aria-label="Scroll categories right"]')`);
  await evaluate(`document.querySelector('[data-category-id="main-5"]').click()`);
  await waitFor(`${current} === 'main-5'`);
  assert.equal(await evaluate(`document.getElementById('menu-category-main-5').getBoundingClientRect().top >= document.querySelector('.restaurant-menu-navigation').getBoundingClientRect().bottom`), true);
  assert.equal(await evaluate(`location.hash`), '#/menu');
  // Scrolling the document updates the active category and exposes its button horizontally.
  await evaluate(`window.scrollTo({ top: window.scrollY + document.getElementById('menu-category-main-9').getBoundingClientRect().top - document.querySelector('.restaurant-menu-navigation').getBoundingClientRect().bottom - 10, behavior: 'instant' })`);
  await waitFor(`${current} === 'main-9'`);
  assert.equal(await evaluate(`(() => { const b = document.querySelector('[aria-current="location"]').getBoundingClientRect(), t = ${list}.getBoundingClientRect(); return b.left >= t.left - 1 && b.right <= t.right + 1; })()`), true);
  await evaluate(`window.scrollTo({top: document.documentElement.scrollHeight, behavior: 'instant'})`);
  await waitFor(`${current} === 'main-11'`);
  // Mobile overflow arrows and actual pointer dragging, including drag-click suppression.
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  await evaluate(`window.scrollTo({ top: 0, behavior: 'instant' }); ${list}.scrollLeft = 0`);
  await waitFor(`${current} === 'main-0'`);
  await waitFor(`document.querySelector('[aria-label="Scroll categories left"]')?.disabled`);
  await evaluate(`document.querySelector('[aria-label="Scroll categories right"]').click()`);
  await waitFor(`${list}.scrollLeft > 10`);
  await evaluate(`${list}.scrollLeft = 0`);
  const rect = await buttonRect('.menu-category-list');
  const beforeY = await evaluate('window.scrollY');
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: rect.x + rect.width - 12, y: rect.y + 22, button: 'left', clickCount: 1 });
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rect.x + 12, y: rect.y + 22, button: 'left', buttons: 1 });
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: rect.x + 12, y: rect.y + 22, button: 'left', clickCount: 1 });
  await waitFor(`${list}.scrollLeft > 50`);
  assert.equal(await evaluate('window.scrollY'), beforeY);
  await evaluate(`${list}.scrollLeft = 0`);
  await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 1 });
  await send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: rect.x + rect.width - 12, y: rect.y + 22 }] });
  for (let step = 1; step <= 6; step++) {
    await send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: rect.x + rect.width - 12 - step * 25, y: rect.y + 22 }] });
    await new Promise(resolve => setTimeout(resolve, 25));
  }
  await send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
  await waitFor(`${list}.scrollLeft > 50`);
  assert.equal(await evaluate('window.scrollY'), beforeY);
  // Keyboard activation and search remove obsolete category targets.
  await send('Emulation.setTouchEmulationEnabled', { enabled: false });
  await evaluate(`document.querySelector('[data-category-id="main-2"]').focus()`);
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13, text: '\r', unmodifiedText: '\r' });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  await waitFor(`${current} === 'main-2'`);
  await field('Find a dish', 'Desserts');
  await waitFor(`${list}?.children.length === 1`);
  assert.equal(await evaluate(`${list}.innerText`), 'Desserts');
  await waitFor(`!document.querySelector('.menu-scroll-control')`);
  await field('Find a dish', 'No match');
  await waitFor(`!${list}`);
  await field('Menu collection', 'lunch');
  await waitFor(`${list}?.innerText === 'Lunch dishes'`);
  assert.equal(await evaluate(`document.querySelector('input[type="search"]').value`), '');
  assert.equal(await evaluate(`document.documentElement.scrollWidth <= window.innerWidth`), true);
  assert.deepEqual(errors, []);
  console.log('Passed: category navigation, sticky offsets, scroll tracking, overflow arrows, mouse drag, touch swipe, keyboard, search and collection changes.');
} finally { await send('Page.close'); ws.close(); }
