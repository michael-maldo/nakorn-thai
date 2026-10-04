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
 await send('Page.navigate',{url:`${base}#/reservations`});
 await waitFor("document.querySelector('fieldset') && !document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('button[type=submit],button.button-primary').disabled"),true);
 assert.equal(await evaluate("document.querySelectorAll('[aria-label=\"EMAIL verification\"]').length"),0);
 await input('[name=email]','Guest@example.test');
 assert.equal(await evaluate("document.querySelector('button.button-primary').disabled"),true);
 await input('[name=phone]','0412345678');await click('Send verification code');
 await waitFor("document.body.innerText.includes('Code sent.')");
 await input('[autocomplete=one-time-code]','123456');
 await evaluate("window.verificationError='Code invalid'");await click('Verify code');
 await waitFor("document.body.innerText.includes('Code invalid')");
 assert.equal(await evaluate("document.querySelector('button.button-primary').disabled"),true);
 await evaluate("window.verificationError='Code expired'");await click('Verify code');await waitFor("document.body.innerText.includes('Code expired')");
 await evaluate("window.verificationError=''");await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
 assert.equal(await evaluate("document.querySelector('button.button-primary').disabled"),false);
 await input('[name=phone]','0499999999');
 await waitFor("document.querySelector('button.button-primary').disabled");
 await evaluate("window.startError='Verification channel unavailable'");await click('Send verification code');await waitFor("document.body.innerText.includes('Verification channel unavailable')");
 await input('[name=phone]','0412345678');await evaluate("window.startError=''");await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("!document.querySelector('button.button-primary').disabled");
 await input('[name=name]','Guest');await input('[name=time]','2026-10-10T19:00');
 await evaluate('window.submissionError=true');await click('Request booking');await waitFor("document.body.innerText.includes('Response lost')");
 // Renewing verification for unchanged booking details retains its request UUID.
 await input('[name=phone]','0499999999');await input('[name=phone]','0412345678');
 await evaluate("window.challengeId='renewed-challenge';window.submissionError=false");
 await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("!document.querySelector('button.button-primary').disabled");
 await click('Request booking');await waitFor('window.requests.length===2');
 assert.equal(await evaluate('window.requests[0].requestId===window.requests[1].requestId'),true);
 assert.equal(await evaluate('window.requests[1].phoneVerificationId'),'renewed-challenge');
 assert.equal(await evaluate('window.bookingIds.size'),1);
 const body=await evaluate('window.requests[0]');assert.equal(body.phoneVerificationId,'challenge');assert.equal(body.emailVerificationId,undefined);assert.equal(body.email,'Guest@example.test');assert.equal(body.phone,'0412345678');
 assert.equal(await evaluate("window.verificationRequests.every(r=>r.channel==='SMS')"),true);
 assert.deepEqual(errors,[]);console.log('Reservation browser: verification, errors, expiry, destination change, optional email and lost-response retry with renewed SMS passed.');
}finally{await send('Page.close');ws.close();}
