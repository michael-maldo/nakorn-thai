import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {prototypeWorkerPlugin} from '../pwa/buildWorker.js';
test('prototype worker caches only immutable scripts/styles and cleans only its own namespace',async()=>{
 let emitted;const plugin=prototypeWorkerPlugin();
 plugin.generateBundle.call({emitFile:file=>emitted=file},{},{entry:{type:'chunk',isEntry:true,facadeModuleId:'/app/staff-pwa-proto/index.html',fileName:'staff-pwa-proto/assets/prototype-test.js',imports:[],viteMetadata:{importedCss:new Set(['staff-pwa-proto/assets/prototype-test.css'])}}});
 assert.equal(emitted.fileName,'staff-pwa-proto/sw.js');
 const handlers={},deleted=[],added=[];let claimed=false;
 const cache={addAll:async assets=>added.push(...assets),match:async()=>null};
 vm.runInNewContext(emitted.source,{self:{addEventListener:(name,handler)=>handlers[name]=handler,location:{origin:'https://test.invalid'},clients:{claim:async()=>{claimed=true;}},skipWaiting:()=>{}},URL,caches:{open:async()=>cache,keys:async()=>['nakorn-staff-old','nakorn-proto-staff-obsolete','customer-cart','other-cache'],delete:async name=>{deleted.push(name);}},fetch:async()=>({ok:true})});
 let waiting;handlers.install({waitUntil:promise=>waiting=promise});await waiting;assert.deepEqual(added.sort(),['/staff-pwa-proto/assets/prototype-test.css','/staff-pwa-proto/assets/prototype-test.js']);
 handlers.activate({waitUntil:promise=>waiting=promise});await waiting;assert.deepEqual(deleted,['nakorn-proto-staff-obsolete']);assert.equal(claimed,true);
 for(const path of ['/api/staff/foh/orders','/api/identity/refresh','/staff/','/staff-pwa-proto/','/media/menu/image.png','/staff-pwa-proto/assets/prototype-test.js?secret=x']){
  let intercepted=false;handlers.fetch({request:{method:'GET',url:'https://test.invalid'+path},respondWith:()=>intercepted=true});assert.equal(intercepted,false,path);
 }
 let intercepted=false;handlers.fetch({request:{method:'GET',url:'https://test.invalid/staff-pwa-proto/assets/prototype-test.js'},respondWith:promise=>{intercepted=true;waiting=promise;}});await waiting;assert.equal(intercepted,true);
});
