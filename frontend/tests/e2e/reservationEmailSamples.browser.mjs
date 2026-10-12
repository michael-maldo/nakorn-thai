/** Actual Java-rendered fictional samples exported by ReservationEmailTest; no mail sent. */
import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
const directory=process.env.RESERVATION_EMAIL_SAMPLES_DIR||'/tmp/nakorn-reservation-email-samples';
const debug=process.env.CHROME_DEBUG_URL||'http://127.0.0.1:9223';
const page=await(await fetch(`${debug}/json/new?about:blank`,{method:'PUT'})).json();
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
let sequence=0;const pending=new Map();ws.addEventListener('message',event=>{const msg=JSON.parse(event.data);if(msg.id){const p=pending.get(msg.id);pending.delete(msg.id);msg.error?p.reject(msg.error):p.resolve(msg.result);}});
const send=(method,params={})=>new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
const normalize=text=>text.replace(/\s+/g,' ').trim();
try{
 await send('Page.enable');const tree=await send('Page.getFrameTree');
 const files=(await readdir(directory)).filter(f=>f.endsWith('.html'));assert.equal(files.length,5);
 for(const file of files){
  const html=await readFile(`${directory}/${file}`,'utf8'),plain=await readFile(`${directory}/${file.replace('.html','.txt')}`,'utf8');
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  await send('Page.setDocumentContent',{frameId:tree.frameTree.frame.id,html});
  const result=await send('Runtime.evaluate',{expression:"JSON.stringify({overflow:document.documentElement.scrollWidth>innerWidth,text:document.querySelector('[role=main] > div').innerText,lang:document.documentElement.lang,title:document.title,scripts:document.querySelectorAll('script').length,links:Array.from(document.querySelectorAll('a')).map(a=>({href:a.href,text:a.textContent}))})",returnByValue:true});
  const data=JSON.parse(result.result.value);assert.equal(data.overflow,false,file);assert.equal(data.scripts,0,file);assert.equal(data.lang,'en-AU');assert.equal(plain.split('\n')[0],`Subject: ${data.title}`);
  assert.ok(data.text.includes('Australia/Melbourne'));assert.ok(data.text.includes('4 guests'));assert.ok(data.text.includes('12345678'));assert.ok(!data.text.includes('INTERNAL PRIVATE NOTE'));
  const body=plain.substring(plain.indexOf('\n\n')+2);
  if(file.startsWith('STAFF_')){
   assert.deepEqual(data.links,[{href:'https://restaurant.example.test/#/staff/reservations',text:'View reservations'}]);
   const suffix=`View reservations\n${data.links[0].href}\n`;assert.ok(body.endsWith(suffix));assert.equal(normalize(data.text),normalize(body.slice(0,-suffix.length)));
   assert.ok(data.text.includes("<script>alert('x')</script>"));assert.ok(data.text.includes('REQUESTED — not yet confirmed'));
  }else{assert.equal(data.links.length,0);assert.equal(normalize(data.text),normalize(body));}
 }
 console.log('Five reservation email samples passed: 390px layout, escaped content, subject/plain/HTML parity, timezone, no customer actions and authenticated staff link.');
}finally{await send('Page.close');ws.close();}
