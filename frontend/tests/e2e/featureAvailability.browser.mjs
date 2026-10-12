/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.AVAILABILITY_TEST_URL || 'http://127.0.0.1:5174/';
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
 window.sms=true;window.paypal=true;window.optionsFailure=false;window.delayStart=false;window.delayPayment=false;
 window.requests=[];window.verificationUnavailable=false;window.verificationError='';window.paymentError='';window.paid=false;window.created=null;
 const menu={id:'c',slug:'pickup',name:'Pickup',availability:{available:true},categories:[],items:[{id:'d',name:'Curry',available:true,variations:[{id:'v',name:'Standard',priceMinor:1990,available:true}],optionGroups:[]}]};
 if(!sessionStorage.getItem('payment-test-initialized')){
  sessionStorage.clear();sessionStorage.setItem('payment-test-initialized','yes');
  sessionStorage.setItem('nakorn-pickup-cart',JSON.stringify({version:2,lines:[{collectionId:'c',collectionSlug:'pickup',collectionName:'Pickup',dishId:'d',dishName:'Curry',variationId:'v',variationName:'Standard',basePriceMinor:1990,unitPriceMinor:1990,selectedOptions:[],quantity:1}]}));
 }
 window.created=JSON.parse(sessionStorage.getItem('payment-test-order')||'null');
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  window.requests.push({url,body:options.body?JSON.parse(options.body):null});
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return sessionStorage.getItem('payment-test-staff')?Response.json({accessToken:'test',expiresAt:new Date(Date.now()+900000).toISOString(),user:{username:'front',role:'FOH'}}):new Response(null,{status:401});
  if(url==='/api/orders/contact-verifications/options')return window.optionsFailure?Response.json({message:'Options offline'},{status:503}):Response.json({sms:window.sms,email:true});
  if(url==='/api/orders/options')return window.optionsFailure?Response.json({message:'Options offline'},{status:503}):Response.json({enabled:window.sms,phoneRequired:true,message:window.sms?'Available':'SMS verification unavailable. Please contact the restaurant.'});
  if(url==='/api/payments/options')return window.optionsFailure?Response.json({message:'Options offline'},{status:503}):Response.json({paypal:window.paypal,payid:true,payAtRestaurant:true});
  if(url==='/api/menu/collections')return Response.json([menu]);
  if(url==='/api/menu/collections/pickup/items')return Response.json(menu);
  if(url==='/api/orders/contact-verifications'&&window.verificationUnavailable)return Response.json({message:'SMS verification unavailable'},{status:503});
  if(url==='/api/reservations/options')return window.optionsFailure?Response.json({message:'Options offline'},{status:503}):Response.json({enabled:window.sms,phoneRequired:true});
  if(url==='/api/restaurant/availability')return Response.json({timezone:'Australia/Melbourne'});
  if(url==='/api/order-verification/options')return Response.json({sms:window.sms,email:true});
  if(url==='/api/order-verification/start'){if(window.delayStart)await new Promise(resolve=>window.resolveStart=resolve);return Response.json({challengeId:'late-challenge',message:'Code sent'});}
  if(url==='/api/orders/contact-verifications'||url==='/api/reservations/contact-verifications'){if(window.delayStart)await new Promise(resolve=>window.resolveStart=resolve);return Response.json({id:'challenge',expiresAt:new Date(Date.now()+600000).toISOString(),resendAt:new Date(Date.now()).toISOString()});}
  if(url==='/api/orders/contact-verifications/challenge/verify'){
   if(window.verificationError)return Response.json({message:window.verificationError},{status:400});
   return Response.json({id:'challenge',verified:true,expiresAt:new Date(Date.now()+600000).toISOString()});
  }
  if(url==='/api/orders'&&options.method==='POST'){
   const body=JSON.parse(options.body);window.created={...body,id:body.requestId,reference:'ABC123',totalMinor:1990,status:'NEW',paidAt:null,items:[]};
   sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));return Response.json(window.created);
  }
  if(url.startsWith('/api/staff/payments/')&&url.endsWith('/payid-confirm')){
   window.created.paidAt=new Date().toISOString();window.created.version++;sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));return Response.json({paid:true});
  }
  if(url.startsWith('/api/staff/foh/orders'))return Response.json([window.created]);
  if(url.startsWith('/api/orders/'))return window.created?Response.json(window.created):Response.json({message:'Not found'},{status:404});
  if(url.startsWith('/api/payments/')){
   if(window.delayPayment&&!url.endsWith('/check'))await new Promise(resolve=>window.resolvePayment=resolve);
   if(window.paymentError)return Response.json({message:window.paymentError},{status:502});
   const method=window.created.paymentMethod;
   return Response.json({method,status:window.paid?'PAID':'PENDING',paid:window.paid,totalMinor:1990,currency:'AUD',approvalUrl:method==='PAYPAL'?'https://www.sandbox.paypal.com/checkoutnow?token=PP':null,payid:method==='PAYID'?'merchant@example.com':null,accountName:'Test Restaurant',reference:window.created.id});
  }
  throw new Error('Unexpected API: '+url);
 };
}
const input=async(selector,value)=>{
 await evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('input',{bubbles:true}));})()`);
};
const focus=()=>evaluate("window.dispatchEvent(new Event('focus'))");
const settle=()=>evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
try{
 await send('Page.enable');await send('Runtime.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await send('Page.navigate',{url:`${base}#/checkout`});
 await waitFor("document.querySelector('option[value=PAYPAL]')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/payments/options').length"),1);
 await evaluate("(()=>{const el=document.querySelector('select');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el,'PAYPAL');el.dispatchEvent(new Event('change',{bubbles:true}));})()");
 await evaluate('window.paypal=false');await focus();await waitFor("!document.querySelector('option[value=PAYPAL]')");await waitFor("document.querySelector('select').value==='PAY_AT_RESTAURANT'");
 assert.equal(await evaluate("!!document.querySelector('option[value=PAYID]')"),true);
 await input('[autocomplete=tel]','0412345678');await evaluate('window.delayStart=true');await click('Send verification code');await waitFor('window.resolveStart');
 await evaluate('window.sms=false');await focus();await waitFor("document.body.innerText.includes('SMS verification is currently unavailable')");
 await evaluate('window.resolveStart()');await settle();assert.equal(await evaluate("!!document.querySelector('[autocomplete=one-time-code]')"),false);
 await evaluate('window.sms=true;window.delayStart=false');await focus();await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await evaluate('window.optionsFailure=true');await focus();await waitFor("document.body.innerText.includes('Options offline')");assert.equal(await evaluate("!!document.querySelector('[autocomplete=one-time-code]')"),false);
 await evaluate('window.optionsFailure=false;window.paypal=true');await focus();await waitFor("document.querySelector('option[value=PAYPAL]')");await waitFor("!document.body.innerText.includes('Options offline')");
 // Reservation disablement is explained and refresh failures recover without stale alerts.
 await evaluate("window.location.hash='/reservations'");await waitFor("document.querySelector('[name=party]')");
 await evaluate('window.sms=false');await focus();await waitFor("document.body.innerText.includes('Online table requests are currently unavailable')");
 await evaluate('window.optionsFailure=true');await focus();await waitFor("document.body.innerText.includes('Options offline')");
 await evaluate('window.optionsFailure=false;window.sms=true');await focus();await waitFor("!document.body.innerText.includes('Options offline')&&!document.body.innerText.includes('Online table requests are currently unavailable')");
 // An in-flight contact request must not leave parent state stuck after unmount.
 await input('[name=phone]','0412345678');await evaluate('window.delayStart=true;window.resolveStart=null');await click('Send verification code');await waitFor('window.resolveStart');
 await evaluate("window.location.hash='/recover-order'");await waitFor("document.querySelector('select option[value=sms]')");await evaluate('window.resolveStart()');await settle();
 await evaluate("window.location.hash='/reservations';window.delayStart=false");await waitFor("document.querySelector('[name=phone]')&&!document.querySelector('[name=phone]').disabled");
 // Tracking must not resurrect an SMS challenge after falling back to email.
 await evaluate("window.location.hash='/recover-order'");await waitFor("document.querySelector('select option[value=sms]')");
 await input('input[pattern="[0-9a-fA-F-]{36}"]','11111111-1111-4111-8111-111111111111');await evaluate('window.delayStart=true;window.resolveStart=null');await click('Send verification code');await waitFor('window.resolveStart');
 await evaluate('window.sms=false');await focus();await waitFor("document.querySelector('select').value==='email'");await evaluate('window.resolveStart()');await settle();assert.equal(await evaluate("!!document.querySelector('[autocomplete=one-time-code]')"),false);
 // Disabled PayPal still permits checking an existing payment, without setup calls.
 await evaluate("window.created={id:'11111111-1111-4111-8111-111111111111',reference:'TEST',paymentMethod:'PAYPAL',status:'NEW',totalMinor:1990,items:[]};sessionStorage.setItem('nakorn-pickup-receipt',JSON.stringify({requestId:window.created.id,trackingToken:'test'}));window.paypal=false;window.location.hash='/order-confirmation'");
 await waitFor("document.body.innerText.includes('This payment method is currently unavailable')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/payments/'+window.created.id).length"),0);
 await click('Confirm / check PayPal payment');await waitFor("window.requests.some(r=>r.url.endsWith('/check'))");
 await evaluate('window.paypal=true;window.delayPayment=true');await focus();await waitFor('window.resolvePayment');
 await evaluate('window.paypal=false');await focus();await waitFor("document.body.innerText.includes('This payment method is currently unavailable')");
 await evaluate('window.resolvePayment()');await settle();assert.equal(await evaluate("!!document.querySelector('a[href*=checkoutnow]')"),false);
 await evaluate('window.paypal=true;window.delayPayment=false');await focus();await waitFor("document.querySelector('a[href*=checkoutnow]')");
 // Cash receipts do not subscribe to provider options.
 await evaluate("window.location.hash='/recover-order'");await waitFor("document.querySelector('select option[value=email]')");
 await evaluate("window.created.paymentMethod='PAY_AT_RESTAURANT';window.location.hash='/order-confirmation'");await waitFor("document.body.innerText.includes('Pay at the restaurant when collecting.')");
 const optionsCount=await evaluate("window.requests.filter(r=>r.url==='/api/payments/options').length");await focus();await settle();assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/payments/options').length"),optionsCount);
 assert.deepEqual(errors,[]);console.log('Feature availability browser: StrictMode requests, fallback, challenge races, error recovery, reservation feedback and existing PayPal recovery passed.');
}finally{await send('Page.close');ws.close();}
