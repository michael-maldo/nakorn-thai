import {createHash} from 'node:crypto';
export function prototypeWorkerPlugin(){return {name:'staff-prototype-worker',generateBundle(_options,bundle){
 const entry=Object.values(bundle).find(file=>file.type==='chunk'&&file.isEntry&&file.facadeModuleId?.endsWith('/staff-pwa-proto/index.html'));
 if(!entry)throw new Error('Prototype entry missing');
 const assets=new Set();function visit(chunk){if(assets.has('/'+chunk.fileName))return;assets.add('/'+chunk.fileName);for(const css of chunk.viteMetadata?.importedCss||[])assets.add('/'+css);for(const imported of chunk.imports)if(bundle[imported]?.type==='chunk')visit(bundle[imported]);}visit(entry);
 const list=[...assets].sort();if(!list.every(path=>path.startsWith('/staff-pwa-proto/assets/')))throw new Error('Prototype assets must stay inside their URL namespace');const version=createHash('sha256').update(list.join()).digest('hex').slice(0,16);
 this.emitFile({type:'asset',fileName:'staff-pwa-proto/sw.js',source:`const CACHE='nakorn-proto-staff-${version}',ASSETS=${JSON.stringify(list)};
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS))));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('nakorn-proto-staff-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('message',event=>{if(event.data?.type==='ACTIVATE_PROTOTYPE')self.skipWaiting();});
self.addEventListener('fetch',event=>{const url=new URL(event.request.url);if(event.request.method!=='GET'||url.origin!==self.location.origin||url.search||!ASSETS.includes(url.pathname))return;event.respondWith(caches.open(CACHE).then(async cache=>(await cache.match(event.request))||fetch(event.request)));});`});
}};}
