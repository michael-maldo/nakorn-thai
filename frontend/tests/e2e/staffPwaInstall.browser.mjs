// Existing repository CDP harness: all staff APIs are synthetic, no printer traffic.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { installPwaFixtures } from './staffPwaFixtures.mjs';
import { staffSessionFixture } from './staffIdentityFixtures.mjs';
const base = process.env.STAFF_LAYOUT_TEST_URL || 'http://127.0.0.1:5175/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const artifacts = process.env.STAFF_PWA_ARTIFACTS || '/tmp/nakorn-staff-pwa';
await mkdir(artifacts, {recursive:true});
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0, checks = 0;
const pending = new Map(), errors = [], results = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); if (!waiter) return; pending.delete(message.id); clearTimeout(waiter.timeout);
    message.error ? waiter.reject(message.error) : waiter.resolve(message.result);
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence;
  const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
  pending.set(id, { resolve, reject, timeout }); ws.send(JSON.stringify({ id, method, params }));
});
ws.addEventListener('close', () => { for (const waiter of pending.values()) { clearTimeout(waiter.timeout); waiter.reject(new Error('Browser connection closed')); } pending.clear(); });
const evaluate = async expression => {
  const value = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true, userGesture: true });
  if (value.exceptionDetails) throw new Error(JSON.stringify(value.exceptionDetails));
  return value.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 160; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};

try {
  await send('Page.enable'); await send('Runtime.enable'); await send('Page.bringToFront');
  await send('Page.addScriptToEvaluateOnNewDocument', {source: `(${installPwaFixtures.toString()})(${staffSessionFixture.toString()});`});
  await send('Page.navigate', {url: `${base}staff/`});
  await waitFor("document.querySelector('.sp-order-card')");
  await waitFor("navigator.serviceWorker.controller");
  const result = await evaluate(`(async()=>{
    const manifest=await (await fetch('/staff/manifest.webmanifest')).json();
    const registration=await navigator.serviceWorker.getRegistration('/staff/');
    const keys=await caches.keys(), urls=[];
    for(const key of keys.filter(key=>key.startsWith('nakorn-staff-'))) for(const request of await (await caches.open(key)).keys())urls.push(new URL(request.url).pathname);
    return {manifest,scope:registration.scope,worker:registration.active.scriptURL,urls,printed:document.body.textContent.includes('Browser printing disabled')};
  })()`);
  assert.equal(result.manifest.name,'Nakorn Thai Staff');
  assert.equal(result.manifest.scope,'/staff/'); assert.equal(result.manifest.start_url,'/staff/');
  assert.equal(result.manifest.display,'standalone'); assert.equal(result.manifest.orientation,'any');
  assert.ok(result.scope.endsWith('/staff/')); assert.ok(result.worker.endsWith('/staff/sw.js'));
  assert.ok(result.urls.length>0); assert.ok(result.urls.every(url=>/^\/assets\/.+\.(js|css)$/.test(url)),JSON.stringify(result.urls));
  assert.ok(result.urls.every(url=>!url.includes('/api/')));
  const response=await fetch(`${base}staff/sw.js`); assert.ok(response.ok);
  const worker=await response.text(); assert.ok(!worker.includes('caches.match(event.request)')); assert.ok(worker.includes('ASSETS.includes(url.pathname)'));
  const websiteWorker=await evaluate("navigator.serviceWorker.getRegistration('/').then(r=>!!r)");
  assert.equal(websiteWorker,false,'customer site outside PWA worker scope');
  assert.equal(errors.length,0,JSON.stringify(errors));
  console.log('PASS: production manifest, service-worker activation, static-only cache and customer-scope isolation (14 assertions).');
} finally { await send('Page.close').catch(()=>{}); ws.close(); }
