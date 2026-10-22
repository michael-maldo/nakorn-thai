import { createHash } from 'node:crypto';
// Cache only this entry's immutable bundle graph. No HTML, APIs, media or runtime writes.
export function staffWorkerPlugin() {
  return { name: 'staff-static-worker', generateBundle(_options, bundle) {
    const entry = Object.values(bundle).find(file => file.type === 'chunk' && file.isEntry && file.facadeModuleId?.endsWith('/staff/index.html'));
    if (!entry) throw new Error('Staff entry not found; refusing to build an incomplete PWA.');
    const assets = new Set();
    const visit = chunk => {
      if (assets.has('/' + chunk.fileName)) return;
      assets.add('/' + chunk.fileName);
      for (const css of chunk.viteMetadata?.importedCss || []) assets.add('/' + css);
      for (const imported of chunk.imports) if (bundle[imported]?.type === 'chunk') visit(bundle[imported]);
    };
    visit(entry);
    const list = [...assets].sort();
    const version = createHash('sha256').update(list.join()).digest('hex').slice(0, 16);
    this.emitFile({ type: 'asset', fileName: 'staff/sw.js', source: `
const CACHE='nakorn-staff-${version}', ASSETS=${JSON.stringify(list)};
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(ASSETS))));
self.addEventListener('message',event=>{if(event.data?.type==='ACTIVATE')self.skipWaiting();});
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('nakorn-staff-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=='GET'||url.origin!==self.location.origin||url.search||!ASSETS.includes(url.pathname))return;
 event.respondWith(caches.open(CACHE).then(async cache=>(await cache.match(event.request))||fetch(event.request)));
});
` });
  } };
}
