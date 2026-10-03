/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.PAYMENT_TEST_URL || 'http://127.0.0.1:5174/';
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
  if(url==='/api/orders/options')return Response.json({enabled:true});
  if(url==='/api/payments/options')return Response.json({paypal:!sessionStorage.getItem('payment-test-disabled'),payid:!sessionStorage.getItem('payment-test-disabled'),payAtRestaurant:true});
  if(url==='/api/menu/collections')return Response.json([menu]);
  if(url==='/api/menu/collections/pickup/items')return Response.json(menu);
  if(url==='/api/orders/contact-verifications'&&window.verificationUnavailable)return Response.json({message:'SMS verification unavailable'},{status:503});
  if(url==='/api/orders/contact-verifications')return Response.json({id:'challenge',expiresAt:new Date(Date.now()+600000).toISOString(),resendAt:new Date(Date.now()+60000).toISOString()});
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
try{
 await send('Page.enable');await send('Runtime.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await send('Page.navigate',{url:`${base}#/checkout`});
 await waitFor("document.querySelector('select option[value=PAYPAL]')");
 assert.equal(await evaluate("!!document.querySelector('select option[value=PAYID]')"),true);
 const savedCart=await evaluate("sessionStorage.getItem('nakorn-pickup-cart')");
 await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');
 await evaluate("(()=>{const el=document.querySelector('select');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el,'PAYPAL');el.dispatchEvent(new Event('change',{bubbles:true}));})()");
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Place pickup order').disabled"),true);
 await evaluate('window.verificationUnavailable=true');await click('Send verification code');await waitFor("document.body.innerText.includes('SMS verification unavailable')");
 await evaluate('window.verificationUnavailable=false');await input('[autocomplete=tel]','0412 345 678');
 await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await input('[autocomplete=one-time-code]','123456');
 for(const error of ['Invalid verification code','Verification expired']){
  await evaluate(`window.verificationError=${JSON.stringify(error)}`);await click('Verify code');await waitFor(`document.body.innerText.includes(${JSON.stringify(error)})`);
  assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Place pickup order').disabled"),true);
 }
 await evaluate("window.verificationError=''");await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
 await input('[autocomplete=tel]','0499999999');
 assert.equal(await evaluate("document.body.innerText.includes('Verified')"),false);
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Place pickup order').disabled"),true);
 await input('[autocomplete=tel]','0412345678');await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
 await input('[autocomplete=email]','Guest@example.test');
 await click('Place pickup order');await waitFor("!!document.querySelector('a[href*=checkoutnow]')");
 assert.equal(await evaluate("window.requests.find(r=>r.url==='/api/orders'&&r.body).body.paymentMethod"),'PAYPAL');
 assert.equal(await evaluate("window.requests.find(r=>r.url==='/api/orders'&&r.body).body.phoneVerificationId"),'challenge');
 assert.equal(await evaluate("window.requests.find(r=>r.url==='/api/orders'&&r.body).body.email"),'Guest@example.test');
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders/contact-verifications').every(r=>r.body.channel==='SMS')"),true);
 assert.equal(await evaluate("document.querySelector('a[href*=checkoutnow]').href"),'https://www.sandbox.paypal.com/checkoutnow?token=PP');
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders'&&r.body).length"),1);
 assert.equal(await evaluate("JSON.parse(sessionStorage.getItem('nakorn-pickup-receipt')).trackingToken.length"),64);
 await evaluate("window.paymentError='Payment provider unavailable'");await click('Confirm / check PayPal payment');await waitFor("document.body.innerText.includes('Payment provider unavailable')");
 await evaluate("window.paymentError=''");await click('Retry PayPal setup / check');await waitFor("!document.querySelector('section.order-panel section button').disabled");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders'&&r.body).length"),1);
 await send('Page.navigate',{url:`${base}?paypal=cancel#/order-confirmation`});await waitFor("document.body.innerText.includes('approval was cancelled')");
 await waitFor("window.requests.some(r=>r.url.endsWith('/check'))");
 assert.equal(await evaluate("document.body.innerText.includes('Payment received')"),false);
 await send('Page.navigate',{url:`${base}?paypal=return#/order-confirmation`});await waitFor("window.requests.some(r=>r.url.endsWith('/check'))");
 await evaluate("window.paid=true");await click('Confirm / check PayPal payment');await waitFor("document.body.innerText.includes('Payment received: $19.90')");

 for(const method of ['PAYID','PAY_AT_RESTAURANT']){
  await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.removeItem('payment-test-order');sessionStorage.removeItem('nakorn-pickup-receipt')`);
  await send('Page.navigate',{url:`${base}?paymentCase=${method}#/checkout`});await waitFor("document.querySelector('select option[value=PAYID]')");
  await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');
  await evaluate(`(()=>{const el=document.querySelector('select');Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype,'value').set.call(el,${JSON.stringify(method)});el.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
  await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
  await click('Place pickup order');
  await waitFor(method==='PAYID'?"document.body.innerText.includes('merchant@example.com')":"document.body.innerText.includes('Pay at the restaurant when collecting.')");
  assert.equal(await evaluate("window.requests.find(r=>r.url==='/api/orders'&&r.body).body.paymentMethod"),method);
  if(method==='PAYID'){
   const text=await evaluate('document.body.innerText');
   for(const expected of ['Test Restaurant','$19.90 AUD','Bank reference:','Verify the recipient','awaiting payment confirmation'])assert.ok(text.includes(expected),expected);
   assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).some(b=>/I paid|Confirm bank receipt/i.test(b.textContent))"),false);
   await send('Page.reload');await waitFor("document.body.innerText.includes('merchant@example.com')");
  }
 }
 await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.setItem('payment-test-disabled','true')`);
 await send('Page.navigate',{url:`${base}?paymentCase=disabled#/checkout`});await waitFor("document.querySelector('select')");
 await waitFor("window.requests.some(r=>r.url==='/api/payments/options')");
 assert.equal(await evaluate("!!document.querySelector('select option[value=PAYPAL],select option[value=PAYID]')"),false);

 await evaluate("window.created.paymentMethod='PAYID';window.created.version=0;window.created.paidAt=null;window.created.createdAt=new Date().toISOString();sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));sessionStorage.setItem('payment-test-staff','true')");
 await send('Page.navigate',{url:`${base}?paymentCase=staff#/staff/orders`});
 await waitFor("document.querySelector('[name=bankReference]')");
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Accept order').disabled"),true);
 await input('[name=bankReference]','BANK-TEST-123');
 await evaluate("document.querySelector('[name=bankReceiptChecked]').click()");
 await click('Confirm PayID payment');await waitFor("document.body.innerText.includes('Payment verified.')");
 const command=await evaluate("window.requests.find(r=>r.url.endsWith('/payid-confirm')).body");
 assert.deepEqual(command,{version:0,bankReference:'BANK-TEST-123',bankReceiptChecked:true});
 await waitFor("Array.from(document.querySelectorAll('button')).some(b=>b.textContent==='Accept order'&&!b.disabled)");
 assert.deepEqual(errors,[]);console.log('Payments browser: checkout selection, automatic initiation, approval URL, cancel/return checking, safe retry, paid state, PayID refresh and pay-at-restaurant passed.');
}finally{await send('Page.close');ws.close();}
