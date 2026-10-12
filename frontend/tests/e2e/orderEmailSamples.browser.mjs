/** Reviews generated fictional email artifacts in local Chromium; sends no mail. */
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
const directory=new URL('../../../docs/ordering/email-samples/',import.meta.url);
const debug=process.env.CHROME_DEBUG_URL||'http://127.0.0.1:9223';
const page=await(await fetch(`${debug}/json/new?about:blank`,{method:'PUT'})).json();
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
let sequence=0;const pending=new Map();ws.addEventListener('message',event=>{const msg=JSON.parse(event.data);if(msg.id){const p=pending.get(msg.id);pending.delete(msg.id);msg.error?p.reject(msg.error):p.resolve(msg.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
try{
 await send('Page.enable');const tree=await send('Page.getFrameTree');
 const files=(await readdir(directory)).filter(f=>f.endsWith('.html'));assert.equal(files.length,5);
 const customerLinks=new Set();
 for(const file of files){
  const html=await readFile(new URL(file,directory),'utf8');const plain=await readFile(new URL(file.replace('.html','.txt'),directory),'utf8');
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await send('Page.setDocumentContent',{frameId:tree.frameTree.frame.id,html});
  const result=await send('Runtime.evaluate',{expression:"JSON.stringify({overflow:document.documentElement.scrollWidth>innerWidth,text:document.body.innerText,action:document.querySelector('a').textContent,href:document.querySelector('a').href})",returnByValue:true});
  const data=JSON.parse(result.result.value);assert.equal(data.overflow,false,file);assert.ok(data.text.includes('Total: AUD 99.20'),file);assert.ok(plain.includes(data.action+'\n'+data.href+'\n'),file);
  assert.ok(plain.startsWith('Subject: Nakorn Thai — '));assert.ok(plain.split('\n')[0].includes('#ABCD1234'));
  if(file.startsWith('customer-')){assert.ok(data.href.includes('#/track-order?t='));customerLinks.add(data.href);}else assert.ok(data.href.includes('#/staff/orders?order='));
 }
 assert.equal(customerLinks.size,1);
 console.log('Five actual HTML email samples: 390px layout, no horizontal overflow, retained total, accessible CTA text and plain/HTML URL parity passed.');
}finally{await send('Page.close');ws.close();}
