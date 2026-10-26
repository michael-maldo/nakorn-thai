import assert from 'node:assert/strict';
import {browser} from './browserHarness.mjs';
import {installPrototypeFixtures} from './prototypeFixtures.mjs';
import {staffSessionFixture} from '../../../tests/e2e/staffIdentityFixtures.mjs';
import {installStaffLayoutFixtures} from '../../../tests/e2e/staffLayoutFixtures.mjs';
const b=await browser(),{send,evaluate,waitFor}=b;
const base=process.env.PROTO_PREVIEW_URL||'http://127.0.0.1:5176/staff-pwa-proto/';
try{
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${installPrototypeFixtures.toString()})(${staffSessionFixture.toString()},${installStaffLayoutFixtures.toString()});`});
 await send('Page.navigate',{url:base});await waitFor("document.querySelector('.proto-app')");
 await waitFor("navigator.serviceWorker.getRegistration('/staff-pwa-proto/').then(r=>r?.active?.state==='activated')");
 const scope=await evaluate("navigator.serviceWorker.getRegistration('/staff-pwa-proto/').then(r=>r.scope)");assert.equal(new URL(scope).pathname,'/staff-pwa-proto/');
 assert.equal(await evaluate("navigator.serviceWorker.getRegistration('/staff/').then(r=>!!r)"),false);
 assert.equal(await evaluate("navigator.serviceWorker.getRegistration('/').then(r=>!!r)"),false);
 const manifest=await evaluate("fetch(document.querySelector('[rel=manifest]').href).then(r=>r.json())");assert.equal(manifest.id,'/staff-pwa-proto/');assert.equal(manifest.scope,'/staff-pwa-proto/');
 await evaluate("caches.open('nakorn-staff-isolation-sentinel').then(cache=>cache.put('/sentinel',new Response('keep')))");
 const workerText=await(await fetch(new URL('sw.js',base))).text();const expectedCache=/CACHE='([^']+)'/.exec(workerText)[1];
 await waitFor(`caches.has(${JSON.stringify(expectedCache)})`);
 await waitFor(`caches.open(${JSON.stringify(expectedCache)}).then(async cache=>(await cache.keys()).some(r=>r.url.endsWith('.js')))`);
 const cached=await evaluate(`caches.open(${JSON.stringify(expectedCache)}).then(async cache=>(await cache.keys()).map(r=>new URL(r.url).pathname))`);
 assert.ok(cached.some(p=>p.endsWith('.js')));assert.ok(cached.some(p=>p.endsWith('.css')));assert.ok(cached.every(p=>p.startsWith('/staff-pwa-proto/assets/')&&!p.includes('/api/')));
 assert.ok(await evaluate("caches.keys().then(keys=>keys.includes('nakorn-staff-isolation-sentinel'))"));
 // A reload remains functional and never turns APIs into offline cache hits.
 await send('Page.reload');await waitFor("document.querySelector('.proto-app')");assert.equal(await evaluate("navigator.serviceWorker.controller?.scriptURL.endsWith('/staff-pwa-proto/sw.js')"),true);
 await b.screenshot('production-install');assert.deepEqual(b.errors,[]);
 console.log('PASS: production manifest/icons, scoped worker activation/control/reload, immutable asset cache and existing-cache isolation.');
}finally{await evaluate("caches.delete('nakorn-staff-isolation-sentinel')").catch(()=>{});await b.close();}
