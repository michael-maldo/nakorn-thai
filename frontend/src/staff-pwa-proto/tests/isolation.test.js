import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync,readdirSync} from 'node:fs';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {routes,resolveRoute,visibleRoutes,canAccess} from '../app/routes.js';
import {singleMutation} from '../services/mutations.js';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../../../..');
const local=resolve(root,'frontend/src/staff-pwa-proto');
test('all Staff Web destinations are independently routed and role restricted',()=>{
 assert.equal(routes.length,10);
 assert.equal(resolveRoute('#/staff/menu/items/dish/images').path,'#/staff/menu');
 assert.equal(resolveRoute('#/unknown'),null);
 assert.equal(canAccess('#/staff/settings','FOH'),false);
 assert.equal(canAccess('#/staff/functions','BOH'),false);
 assert.equal(canAccess('#/staff/ordering','FOH'),false);
 assert.deepEqual(visibleRoutes('BOH').map(x=>x.label),['Home','Orders']);
});
test('workflow source fingerprints match the reviewed shared-Orders extraction baseline',()=>{
 const baseline=JSON.parse(readFileSync(resolve(local,'tests/source-baseline.json')));
 for(const [path,hash] of Object.entries(baseline))assert.equal(createHash('sha256').update(readFileSync(resolve(root,'frontend/src',path))).digest('hex'),hash,path);
});
test('manifest, cache, registration, storage and build are independently scoped',()=>{
 const manifest=JSON.parse(readFileSync(resolve(local,'public/staff-pwa-proto/manifest.webmanifest')));
 assert.equal(manifest.scope,'/staff-pwa-proto/');assert.equal(manifest.id,manifest.scope);assert.equal(manifest.start_url,manifest.scope);
 for(const icon of manifest.icons)assert.ok(existsSync(resolve(local,'public'+icon.src)));
 const worker=readFileSync(resolve(local,'pwa/buildWorker.js'),'utf8');assert.match(worker,/nakorn-proto-staff-/);assert.doesNotMatch(worker,/nakorn-staff-/);
 const register=readFileSync(resolve(local,'pwa/register.js'),'utf8');assert.match(register,/scope:'\/staff-pwa-proto\/'/);
 const app=readFileSync(resolve(local,'StaffProtoApp.jsx'),'utf8');assert.match(app,/nakorn-staff-pwa-proto:sidebar/);
 assert.match(readFileSync(resolve(root,'frontend/vite.proto.config.js'),'utf8'),/outDir:'dist-staff-pwa-proto'/);
});
test('identical concurrent writes dispatch once; successful and failed results are never cached',async()=>{
 let count=0,release;const operation=()=>{count++;return new Promise(resolve=>{release=resolve;});};
 const a=singleMutation('test',operation),b=singleMutation('test',operation);assert.equal(a,b);
 await Promise.resolve();assert.equal(count,1);release('saved');assert.equal(await a,'saved');
 assert.equal(await singleMutation('test',()=>++count),2);
 await assert.rejects(singleMutation('fail',()=>{throw Error('uncertain');}),/uncertain/);
 assert.equal(await singleMutation('fail',()=>3),3);
});
test('prototype never imports protected order, audio, worker or printer components',()=>{
 function walk(dir){return readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?walk(resolve(dir,entry.name)):[resolve(dir,entry.name)]);}
 for(const path of walk(local).filter(p=>/\.(jsx?|css)$/.test(p)&&!p.includes('/tests/')&&!p.endsWith('.test.js'))){
  const text=readFileSync(path,'utf8');assert.doesNotMatch(text,/from ['"][^'"]*\/staff-pwa\//,path);assert.doesNotMatch(text,/createSoundEngine|registerStaffWorker/,path);
 }
});
test('PWA extraction baseline and untouched Android, Web shell and styles retain reviewed fingerprints',()=>{
 const baseline=JSON.parse(readFileSync(resolve(local,'tests/protected-baseline.json')));
 for(const [path,hash] of Object.entries(baseline))assert.equal(createHash('sha256').update(readFileSync(resolve(root,path))).digest('hex'),hash,path);
});
