/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.RESERVATION_TEST_URL || 'http://127.0.0.1:5174/';
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
function fixture() {
 window.requests=[];window.verificationRequests=[];window.verificationError='';window.startError='';window.challengeId='challenge';window.submissionError=false;window.bookingIds=new Set();
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return new Response(null,{status:401});
  if(url==='/api/orders/contact-verifications/options')return Response.json({sms:true,email:false});
  if(url==='/api/reservations/options')return Response.json({enabled:true,phoneRequired:true});
  if(url==='/api/restaurant/availability')return Response.json({timezone:'Australia/Melbourne'});
  if(url==='/api/reservations/contact-verifications'){
   window.verificationRequests.push(JSON.parse(options.body));
   if(window.startError)return Response.json({message:window.startError},{status:503});
   return Response.json({id:window.challengeId,expiresAt:new Date(Date.now()+600000).toISOString(),resendAt:new Date(Date.now()+60000).toISOString()});
  }
  if(url.endsWith('/verify')){
   if(window.verificationError)return Response.json({message:window.verificationError},{status:400});
   return Response.json({id:window.challengeId,verified:true,expiresAt:new Date(Date.now()+600000).toISOString()});
  }
  if(url==='/api/reservations'){const body=JSON.parse(options.body);window.requests.push(body);window.bookingIds.add(body.requestId);if(window.submissionError)return Response.json({message:'Response lost; retry your request'},{status:503});return Response.json({reference:'booking',message:'Booking request received.'});}
  throw new Error(`Unexpected API: ${url}`);
 };
}
const input=async(selector,value)=>{
 await evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('input',{bubbles:true}));})()`);
};
try {
 await send('Page.enable');await send('Runtime.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await send('Page.bringToFront');
 await send('Page.navigate',{url:`${base}#/reservations`});
 await waitFor("document.querySelector('[name=phone]') && !document.querySelector('[name=phone]').disabled && document.querySelectorAll('section[aria-label]').length");
 await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
 const value=()=>evaluate("document.querySelector('[name=phone]').value");
 const select=()=>evaluate("document.querySelector('[name=phone]').focus();document.querySelector('[name=phone]').select()");
 const type=text=>send('Input.insertText',{text});
 const blur=()=>evaluate("document.querySelector('[name=phone]').blur()");
 await select();
 for(const digit of '0434778435')await type(digit);
 assert.equal(await value(),'0434778435','Typing is not reformatted');
 await blur();assert.equal(await value(),'+61 434 778 435');
 await select();await type('434778435');await blur();assert.equal(await value(),'+61 434 778 435');
 await select();await type('+61 (0)434-778-435');await blur();assert.equal(await value(),'+61 434 778 435');
 await evaluate("document.querySelector('[name=phone]').focus();document.querySelector('[name=phone]').setSelectionRange(14,15)");
 await type('6');assert.equal(await value(),'+61 434 778 436');
 assert.equal(await evaluate("document.querySelector('[name=phone]').selectionStart"),15);
 await select();await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Backspace',code:'Backspace',windowsVirtualKeyCode:8});await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Backspace',code:'Backspace',windowsVirtualKeyCode:8});
 assert.equal(await value(),'');
 await type('04');assert.equal(await value(),'04');await blur();assert.equal(await value(),'+61 4');
 assert.equal(await evaluate("document.querySelector('[name=phone]').getAttribute('aria-invalid')"),'true');
 await select();await type('0434778435');await blur();
 assert.equal(await evaluate("document.querySelector('[name=phone]').validity.valid"),true);
 await click('Send verification code');
 assert.deepEqual(await evaluate('window.verificationRequests[0]'),{channel:'SMS',destination:'+61434778435'});
 assert.deepEqual(errors,[]);
 console.log('Australian phone browser: natural entry, paste, blur spacing, cursor replacement, deletion, partial validation and canonical verification payload passed.');
}finally{await send('Page.close');ws.close();}
