/** Real deployment smoke test: read-only domain operations; optional login/logout. */
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {browser} from './browserHarness.mjs';
const base=new URL(process.env.PROTO_LIVE_URL||'https://nakorn.com.au/staff-pwa-proto/');
assert.equal(base.pathname,'/staff-pwa-proto/');
const results={url:base.href,assets:[],authenticated:[],limitations:[]};
async function read(path){const response=await fetch(new URL(path,base));assert.equal(response.status,200,path);return response;}
const html=await(await read('')).text();assert.match(html,/Staff Prototype/);
for(const path of [...html.matchAll(/(?:src|href)="([^"]+)"/g)].map(m=>m[1]).filter(p=>p.startsWith('/staff-pwa-proto/'))){const response=await read(path);results.assets.push({path,status:response.status,type:response.headers.get('content-type')});}
const manifest=await(await read('manifest.webmanifest')).json();assert.equal(manifest.scope,'/staff-pwa-proto/');assert.equal(manifest.id,manifest.scope);
for(const icon of manifest.icons)await read(icon.src);
const worker=await(await read('sw.js')).text();assert.match(worker,/nakorn-proto-staff-/);assert.doesNotMatch(worker,/nakorn-staff-/);
const assets=JSON.parse(/ASSETS=(\[[^;]+\])/.exec(worker)[1]);
assert.ok(assets.every(path=>path.startsWith('/staff-pwa-proto/assets/')));
for(const path of assets)await read(path);
const jsPath=assets.find(path=>path.endsWith('.js'));const js=await(await read(jsPath)).text();
for(const path of new Set([...js.matchAll(/\/staff-pwa-proto\/assets\/[^"'`\s)]+\.(?:jpg|png|woff2?)/g)].map(m=>m[0])))await read(path);
assert.equal((await fetch(new URL('assets/nonexistent-deployment-check.js',base))).status,404);
const availability=await(await read('/api/restaurant/availability')).json();assert.equal(typeof availability.timezone,'string');results.backendTimezone=availability.timezone;
const protectedResponse=await fetch(new URL('/api/staff/restaurant/configuration',base));assert.ok([401,403].includes(protectedResponse.status));results.unauthenticatedAdminStatus=protectedResponse.status;
const b=await browser();
try{
 await b.send('Page.navigate',{url:base.href});await b.waitFor("document.querySelector('[name=password]')");
 await b.send('Page.reload');await b.waitFor("document.querySelector('[name=password]')");
 await b.send('Page.navigate',{url:base.href+'#/staff/menu/items/new'});await b.waitFor("document.querySelector('[name=password]')");
 await b.waitFor("navigator.serviceWorker.getRegistration('/staff-pwa-proto/').then(r=>r?.active?.state==='activated')");
 assert.equal(new URL(await b.evaluate("navigator.serviceWorker.getRegistration('/staff-pwa-proto/').then(r=>r.scope)")).pathname,'/staff-pwa-proto/');
 for(const [label,prefix]of [['ADMIN','PROTO_SMOKE'],['FOH','PROTO_SMOKE_FOH']]){
  const username=process.env[prefix+'_USERNAME'],password=process.env[prefix+'_PASSWORD'];
  if(!username||!password){results.limitations.push(label+' authenticated smoke not run: no existing credentials supplied');continue;}
  await b.send('Page.navigate',{url:base.href});await b.waitFor("document.querySelector('[name=password]')");await b.field('Username',username);await b.field('Password',password);await b.click('Sign in');await b.waitFor("document.querySelector('.proto-app')");
  const permitted=label==='ADMIN'?['#/staff/restaurant','#/staff/menu','#/staff/functions','#/staff/reservations','#/staff/orders','#/staff/homepage','#/staff/users','#/staff/settings']:['#/staff/functions','#/staff/reservations','#/staff/orders','#/staff/ordering'];
  for(const route of permitted){await b.evaluate(`location.hash=${JSON.stringify(route)}`);await b.frame();await b.waitFor("document.querySelector('#proto-content h1')");assert.equal(await b.evaluate("document.querySelector('#proto-content h1').textContent==='Access restricted'"),false);}
  if(label==='FOH'){await b.evaluate("location.hash='#/staff/settings'");await b.waitFor("document.querySelector('#proto-content h1')?.textContent==='Access restricted'");}
  results.authenticated.push({role:label,routes:permitted.length});await b.click('Sign out');await b.waitFor("document.querySelector('[name=password]')");
 }
 await b.screenshot('live-prototype-login');assert.deepEqual(b.errors,[]);
 results.browserErrors=0;results.serviceWorkerScope='/staff-pwa-proto/';results.domainMutations=0;
 const dir=process.env.PROTO_ARTIFACTS||'/tmp/nakorn-staff-proto-live';await mkdir(dir,{recursive:true});await writeFile(`${dir}/results.json`,JSON.stringify(results,null,2));console.log(JSON.stringify(results,null,2));
}finally{await b.close();}
