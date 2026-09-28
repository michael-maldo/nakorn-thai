/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.HOMEPAGE_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0;
const pending = new Map(), errors = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); pending.delete(message.id);
    message.error ? waiter.reject(message.error) : waiter.resolve(message.result);
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
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
  for (let i = 0; i < 150; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 40));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};
const click = async text => {
  const button = `Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`;
  await waitFor(button); await evaluate(`${button}.click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
};
const toggle = async name => {
  await evaluate(`Array.from(document.querySelectorAll('.homepage-collection-choices label')).find(l => l.textContent.includes(${JSON.stringify(name)})).querySelector('input').click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(resolve))');
};
function fixture() {
  window.confirm = () => true;
  window.savedHomepage = { version: 0, collectionIds: ['first'], collections: [
    { id: 'first', name: 'First collection', status: 'PUBLISHED' },
    { id: 'second', name: 'Second collection', status: 'PUBLISHED' },
    { id: 'draft', name: 'Draft collection', status: 'DRAFT' },
  ] };
  window.failHomepageSave = false; window.homepageWrites = [];
  const realFetch = window.fetch;
  window.fetch = async (url, options = {}) => {
    if (!String(url).startsWith('/api')) return realFetch(url, options);
    if (String(url).endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'test' });
    if (url === '/api/identity/refresh') return Response.json({ accessToken: 'test', expiresAt: new Date(Date.now() + 3600000).toISOString(), user: { id: 'admin', username: 'Admin', role: 'ADMIN' } });
    if (url === '/api/staff/menu/homepage') {
      if (options.method === 'PUT') {
        if (window.failHomepageSave) return Response.json({ message: 'Home page settings changed. Reload before saving.' }, { status: 409 });
        const body = JSON.parse(options.body);
        if (options.headers['X-CSRF-TOKEN'] !== 'test' || body.version !== savedHomepage.version) return new Response(null, { status: 409 });
        window.homepageWrites.push(body);
        savedHomepage = { ...savedHomepage, ...body, version: body.version + 1 };
      }
      return Response.json(savedHomepage);
    }
    if (url === '/api/menu/homepage') return Response.json(savedHomepage.collectionIds
      .map(id => savedHomepage.collections.find(c => c.id === id)).filter(c => c.status === 'PUBLISHED')
      .map(c => ({ ...c, items: Array.from({ length: 6 }, (_, i) => ({ id: `${c.id}-${i}`, name: `${c.name} dish ${i}`, description: 'Test dish', available: true, image: null })) })));
    throw new Error(`Unexpected API: ${url}`);
  };
}
try {
  await send('Page.enable'); await send('Runtime.enable');
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `(${fixture.toString()})()` });
  await send('Page.navigate', { url: `${base}#/staff/homepage` });
  await waitFor("document.querySelectorAll('.homepage-collection-choices input').length === 3");
  await toggle('Second collection'); await toggle('Draft collection');
  await evaluate("document.querySelector('[aria-label=\"Move Second collection up\"]').click()");
  await click('Save home page collections');
  await waitFor("document.body.innerText.includes('Home page collections saved.')");
  assert.deepEqual(await evaluate('savedHomepage.collectionIds'), ['second', 'first', 'draft']);
  await click('Refresh settings');
  assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.homepage-collection-order li > span')).map(el => el.textContent)"), ['Second collection', 'First collection', 'Draft collection']);
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  assert.equal(await evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1'), true);
  await evaluate("window.location.hash = 'home'");
  await waitFor("document.querySelectorAll('.homepage-menu-collection').length === 2");
  assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.homepage-menu-collection-title')).map(el => el.textContent)"), ['Second collection', 'First collection']);
  assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.homepage-menu-collection')).map(el => el.querySelectorAll('.dish-card').length)"), [4, 4]);
  await evaluate("window.location.hash = '/staff/homepage'");
  await waitFor("document.querySelectorAll('.homepage-collection-choices input').length === 3");
  await toggle('First collection'); await evaluate('window.failHomepageSave = true');
  await click('Save home page collections');
  await waitFor("document.body.innerText.includes('These settings changed elsewhere.')");
  assert.equal(await evaluate("document.querySelector('form fieldset').disabled"), true);
  await evaluate('window.failHomepageSave = false'); await click('Refresh settings');
  for (const name of ['First collection', 'Second collection', 'Draft collection']) await toggle(name);
  await click('Save home page collections');
  await waitFor('savedHomepage.collectionIds.length === 0');
  await evaluate("window.location.hash = 'home'");
  await waitFor("document.querySelector('.hero') && !document.querySelector('.signature')");
  assert.deepEqual(errors, []);
  console.log('Home page browser checks passed: select/order/save/reload, conflict recovery, mobile layout, four dishes per published collection, empty selection.');
} finally {
  await send('Page.close'); ws.close();
}
