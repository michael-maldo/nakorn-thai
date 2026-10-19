/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { staffSessionFixture } from './staffIdentityFixtures.mjs';
const base = process.env.PAYMENT_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0;
const approvalNavigations=[];let lastCheckout,lastEvidence;
const pending = new Map(), errors = [];
ws.addEventListener('message', async event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); pending.delete(message.id);
    message.error ? waiter.reject(new Error(`${waiter.method}: ${JSON.stringify(message.error)}`)) : waiter.resolve(message.result);
  } else if(message.method==='Runtime.bindingCalled'){lastEvidence=JSON.parse(message.params.payload);
  } else if(message.method==='Fetch.requestPaused'){
    // Intercept BEFORE network dispatch: these tests never contact PayPal.
    approvalNavigations.push(message.params.request.url);lastCheckout=lastEvidence;
    await send('Fetch.fulfillRequest',{requestId:message.params.requestId,responseCode:302,responseHeaders:[{name:'Location',value:`${base}?paymentMock=approval#/mock-paypal`}],body:''});
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence; pending.set(id, { resolve, reject, method }); ws.send(JSON.stringify({ id, method, params }));
});
const evaluate = async expression => {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 150; i++) {
    try{if(await evaluate(`Boolean(${expression})`))return;}catch(e){if(!/navigated|context.*destroyed|Cannot find context|not attached/i.test(e.message))throw e;}
    await new Promise(resolve => setTimeout(resolve, 40));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};
const click = async text => {
  const button = `Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`;
  await waitFor(button); await evaluate(`${button}.click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))').catch(e=>{if(!/navigated|context.*destroyed|Cannot find context/i.test(e.message))throw e;});
};
function fixture() {
 window.paymentTestDocument=String(Math.random());
 window.requests=[];window.verificationUnavailable=false;window.verificationError='';window.paymentError='';window.paid=false;window.created=null;
 const menu={id:'c',slug:'pickup',name:'Pickup',availability:{available:true},categories:[],items:[{id:'d',name:'Curry',available:true,variations:[{id:'v',name:'Standard',priceMinor:1990,available:true}],optionGroups:[]}]};
 if(!sessionStorage.getItem('payment-test-initialized')){
  sessionStorage.clear();sessionStorage.setItem('payment-test-initialized','yes');
  sessionStorage.setItem('nakorn-pickup-cart',JSON.stringify({version:2,lines:[{collectionId:'c',collectionSlug:'pickup',collectionName:'Pickup',dishId:'d',dishName:'Curry',variationId:'v',variationName:'Standard',basePriceMinor:1990,unitPriceMinor:1990,selectedOptions:[],quantity:1}]}));
 }
 window.created=JSON.parse(sessionStorage.getItem('payment-test-order')||'null');
 window.paid=!!window.created?.paidAt;
 window.checkMode=sessionStorage.getItem('payment-test-mode')||'approval';
 window.paymentError=sessionStorage.getItem('payment-test-network-error')||'';
 window.setupError=!!sessionStorage.getItem('payment-test-setup-error');
 window.holdCheck=!!sessionStorage.getItem('payment-test-hold-check');
 if(sessionStorage.getItem('payment-test-fast-checks')){const original=window.setTimeout;window.setTimeout=(fn,delay,...args)=>original(fn,[2000,5000,10000,20000].includes(delay)?50:delay,...args);}
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  window.requests.push({url,body:options.body?JSON.parse(options.body):null,orderToken:options.headers?.['X-Order-Token']});
  window.paymentTestEvidence?.(JSON.stringify({requests:window.requests,receipt:JSON.parse(sessionStorage.getItem('nakorn-pickup-receipt')||'null')}));
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return sessionStorage.getItem('payment-test-staff')?Response.json(staffSessionFixture({role:'FOH'})):new Response(null,{status:401});
  if(url==='/api/orders/contact-verifications/options')return Response.json({sms:true,email:false});
  if(url==='/api/orders/options')return Response.json({enabled:true,phoneRequired:!sessionStorage.getItem('payment-test-no-verification')});
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
   const body=JSON.parse(options.body);window.created={...body,id:body.requestId,reference:'ABC123',totalMinor:1990,status:'NEW',paidAt:null,refundStatus:'NONE',paymentReviewRequired:false,items:[{dishName:'Curry',variationName:'Standard',quantity:1,unitPriceMinor:1990,selectedOptions:[]}]};
   sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));if(window.holdOrder)await new Promise(resolve=>window.releaseOrder=resolve);return Response.json(window.created);
  }
  if(url.startsWith('/api/staff/payments/')&&url.endsWith('/payid-confirm')){
   window.created.paidAt=new Date().toISOString();window.created.version++;sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));return Response.json({paid:true});
  }
  if(url.startsWith('/api/staff/foh/orders'))return Response.json([window.created]);
  if(url==='/api/staff/payments/review')return Response.json({orders:[{id:window.created.id,status:'CANCELLED',payment_method:'PAYPAL',refund_status:'REQUIRED',payment_review_required:true,review_reason:'PROVIDER_UNAVAILABLE'}],webhookEvents:[]});
  if(url.startsWith('/api/orders/'))return window.created?Response.json({...window.created,paidAt:window.paid?window.created.paidAt||new Date().toISOString():null}):Response.json({message:'Not found'},{status:404});
  if(url.startsWith('/api/payments/')){
   if(url.endsWith('/check')&&window.holdCheck){window.holdCheck=false;await new Promise(resolve=>window.releaseCheck=resolve);}
   if(window.paymentError)return Response.json({message:window.paymentError},{status:502});
   const method=window.created.paymentMethod;
   if(method==='PAYPAL'&&!url.endsWith('/check')&&window.setupError)return Response.json({message:'Unavailable'},{status:502});
   return Response.json({method,status:window.paid?'PAID':'PENDING',paid:window.paid,totalMinor:1990,currency:'AUD',canResumeApproval:window.checkMode==='approval'&&!window.paid&&!window.created.paymentReviewRequired,canStartPayment:window.checkMode==='not-started'&&!window.paid&&!window.created.paymentReviewRequired,financialStatus:window.paid?'CAPTURED':'UNCONFIRMED',refundStatus:window.created.refundStatus||'NONE',paymentReviewRequired:window.created.paymentReviewRequired,approvalUrl:method==='PAYPAL'?'https://www.sandbox.paypal.com/checkoutnow?token=PP':null,payid:method==='PAYID'?'merchant@example.com':null,accountName:'Test Restaurant',reference:window.created.id});
  }
  throw new Error('Unexpected API: '+url);
 };
}
const input=async(selector,value)=>{
 await evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('input',{bubbles:true}));})()`);
};

async function reload(){
 const previous=await evaluate('window.paymentTestDocument');await send('Page.reload');await waitFor(`window.paymentTestDocument!==${JSON.stringify(previous)}&&!!window.paymentTestDocument`);
}
async function responsive(name){
 for(const [width,height] of [[390,844],[768,1024],[1440,1000]]){
  await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<600});
  await evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
  assert.equal(await evaluate("!document.querySelector('.cart-dock')||getComputedStyle(document.querySelector('.cart-dock')).display==='none'"),true);
  assert.equal(await evaluate('document.documentElement.scrollWidth<=window.innerWidth'),true,`${name} overflow at ${width}`);
  assert.equal(await evaluate("Array.from(document.querySelectorAll('.customer-checkout input:not([type=radio]),.customer-checkout textarea,.customer-checkout .button')).every(el=>{const r=el.getBoundingClientRect();return r.left>=0&&r.right<=innerWidth+1})"),true,`${name} controls at ${width}`);
  if(name==='checkout')assert.ok(await evaluate("Array.from(document.querySelectorAll('.checkout-payment-choice')).every(el=>el.getBoundingClientRect().height>=44)"));
  const shot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
  await writeFile(`/tmp/nakorn-${name}-${width}.png`,Buffer.from(shot.data,'base64'));
 }
}
try{
 await send('Page.enable');await send('Runtime.enable');
 await send('Runtime.addBinding',{name:'paymentTestEvidence'});
 await send('Fetch.enable',{patterns:[{urlPattern:'https://*paypal.com/*',requestStage:'Request'}]});
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`window.staffSessionFixture=(${staffSessionFixture.toString()});(${fixture.toString()})()`});
 await send('Page.navigate',{url:`${base}#/checkout`});
 await waitFor("document.querySelector('input[value=PAYPAL]')");
 assert.equal(await evaluate("!!document.querySelector('input[value=PAYID]')"),true);
 const savedCart=await evaluate("sessionStorage.getItem('nakorn-pickup-cart')");
 await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');
 await evaluate("document.querySelector('input[value=PAYPAL]').click()");
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Continue to PayPal').disabled"),true);
 await evaluate('window.verificationUnavailable=true');await click('Send verification code');await waitFor("document.body.innerText.includes('SMS verification unavailable')");
 await evaluate('window.verificationUnavailable=false');await input('[autocomplete=tel]','0412 345 678');
 // Spacing edits preserve the same canonical destination and its retry cooldown.
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).some(b=>b.disabled&&b.textContent.startsWith('Resend in '))"),true);
 await evaluate("window.originalDateNow=Date.now;Date.now=()=>window.originalDateNow()+61000");
 await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await input('[autocomplete=one-time-code]','123456');
 for(const error of ['Invalid verification code','Verification expired']){
  await evaluate(`window.verificationError=${JSON.stringify(error)}`);await click('Verify code');await waitFor(`document.body.innerText.includes(${JSON.stringify(error)})`);
  assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Continue to PayPal').disabled"),true);
 }
 await evaluate("window.verificationError=''");await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
 await input('[autocomplete=tel]','0499999999');
 assert.equal(await evaluate("document.body.innerText.includes('Verified')"),false);
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Continue to PayPal').disabled"),true);
 await input('[autocomplete=tel]','0412345678');await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
 await input('[autocomplete=email]','Guest@example.test');
 await responsive('checkout');

 // Synchronous duplicate submits share one request ID, one setup and one handoff.
 await evaluate("document.querySelector('form').requestSubmit();document.querySelector('form').requestSubmit()");
 await waitFor("window.location.hash==='#/mock-paypal'");
 assert.equal(approvalNavigations.length,1);
 assert.equal(approvalNavigations[0],'https://www.sandbox.paypal.com/checkoutnow?token=PP');
 const writes=lastCheckout.requests;
 assert.equal(writes.filter(r=>r.url==='/api/orders'&&r.body).length,1);
 const submission=writes.find(r=>r.url==='/api/orders'&&r.body).body;
 assert.equal(submission.paymentMethod,'PAYPAL');assert.equal(submission.phoneVerificationId,'challenge');assert.equal(submission.email,'Guest@example.test');
 assert.equal(writes.filter(r=>r.url===`/api/payments/${submission.requestId}`).length,1);
 const savedReceipt=lastCheckout.receipt;assert.equal(savedReceipt.trackingToken.length,64);
 const providerParameters='token=4D100105X1957711S&PayerID=3AEHFAN7TUYQ6';
 for(const callback of [
  `?paypal=return#/order-confirmation&${providerParameters}`,
  `?paypal=cancel#/order-confirmation&${providerParameters}`,
  '#/order-confirmation',
  `?paypal=return&${providerParameters}#/order-confirmation`,
  `#/order-confirmation?paypal=return&${providerParameters}`,
 ]){
  await send('Page.navigate',{url:`${base}${callback}`});
  await waitFor("document.querySelector('h1')?.textContent==='Your pickup order'");
  await waitFor("document.body.innerText.includes('Payment not completed')");
  if(callback.includes('paypal=cancel'))assert.ok((await evaluate('document.body.innerText')).includes('You cancelled PayPal approval'));
  const requests=await evaluate('window.requests');
  assert.equal(requests.some(r=>r.url==='/api/orders'&&r.body),false);
  assert.equal(requests.some(r=>r.url===`/api/payments/${savedReceipt.requestId}`),false);
  assert.ok(requests.some(r=>r.url===`/api/payments/${savedReceipt.requestId}/check`&&r.orderToken===savedReceipt.trackingToken));
  assert.equal(await evaluate("document.querySelectorAll('[aria-label=\"Order payment\"] button').length"),1);
  assert.equal(await evaluate("document.body.innerText.includes('Payment successful')"),false);
 }
 // Resume only after a fresh backend permission check, never by starting again.
 await click('Continue to PayPal');await waitFor("window.location.hash==='#/mock-paypal'");
 assert.equal(approvalNavigations.length,2);
 assert.equal(lastCheckout.requests.some(r=>r.url===`/api/payments/${savedReceipt.requestId}`),false);
 await send('Page.navigate',{url:`${base}?case=paid-preparation#/order-confirmation`});
 await waitFor("document.body.innerText.includes('Payment not completed')");
 await evaluate("window.created.paidAt=new Date().toISOString();sessionStorage.setItem('payment-test-order',JSON.stringify(window.created))");
 await reload();await waitFor("document.body.innerText.includes('Payment successful')");
 assert.ok((await evaluate('document.body.innerText')).includes('Your order is awaiting restaurant confirmation.'));
 assert.equal(await evaluate("window.requests.some(r=>r.url.startsWith('/api/payments/'))"),false);
 await responsive('receipt');
 // Real restaurant status takes precedence over the initial awaiting-confirmation copy.
 await evaluate("window.created.status='ACCEPTED';sessionStorage.setItem('payment-test-order',JSON.stringify(window.created))");
 await reload();await waitFor("document.body.innerText.includes('Accepted by the restaurant')");
 assert.equal(await evaluate("document.body.innerText.includes('Your order is awaiting restaurant confirmation.')"),false);
 for(const target of ['#/menu',`#/order-confirmation&${providerParameters}`]){
  await evaluate(`window.location.hash=${JSON.stringify(target)}`);
  await waitFor(target==='#/menu'?"document.querySelector('h1')?.textContent==='Our menu'":"document.body.innerText.includes('Payment successful')");
 }
 assert.equal(await evaluate("window.requests.some(r=>r.url.startsWith('/api/payments/'))"),false);
 // Pending financial outcomes and outages get bounded verification, never another setup.
 for(const scenario of ['pending','network']){
  await evaluate(`window.created.paidAt=null;window.created.status='NEW';sessionStorage.setItem('payment-test-order',JSON.stringify(window.created));sessionStorage.setItem('payment-test-mode','uncertain');sessionStorage.setItem('payment-test-fast-checks','true');${scenario==='network'?"sessionStorage.setItem('payment-test-network-error','offline');sessionStorage.removeItem('payment-test-hold-check')":"sessionStorage.removeItem('payment-test-network-error');sessionStorage.setItem('payment-test-hold-check','true')"}`);
  await send('Page.navigate',{url:`${base}?case=${scenario}#/order-confirmation`});
  if(scenario==='pending'){await waitFor('window.releaseCheck');assert.ok((await evaluate('document.body.innerText')).includes('Confirming your payment…'));assert.equal(await evaluate("document.body.innerText.includes('Continue to PayPal')"),false);await evaluate('window.releaseCheck()');}
  await waitFor("document.body.innerText.includes(\"We're still checking your payment\")");
  assert.equal(await evaluate("window.requests.filter(r=>r.url.endsWith('/check')).length"),5);
  assert.equal(await evaluate(`window.requests.some(r=>r.url==='/api/payments/${savedReceipt.requestId}')`),false);
  assert.equal(await evaluate("document.body.innerText.includes('Continue to PayPal')"),false);
  if(scenario==='pending'){
   await evaluate("window.paid=true");await waitFor("document.body.innerText.includes('Payment successful')");
  }
 }
 await evaluate("sessionStorage.removeItem('payment-test-hold-check')");
 // Webhook/worker capture is picked up by normal receipt reads, without any setup.
 await evaluate("sessionStorage.removeItem('payment-test-network-error');window.created.paidAt=new Date().toISOString();sessionStorage.setItem('payment-test-order',JSON.stringify(window.created))");
 await reload();await waitFor("document.body.innerText.includes('Payment successful')");
 assert.equal(await evaluate("window.requests.some(r=>r.url.startsWith('/api/payments/'))"),false);

 // A later authoritative receipt read resolves an earlier review display without reload.
 await evaluate("window.created.paidAt=null;window.created.paymentReviewRequired=true;sessionStorage.setItem('payment-test-order',JSON.stringify(window.created))");
 await reload();await waitFor("document.body.innerText.includes('Your payment needs a review')");
 await evaluate("window.created.paymentReviewRequired=false;window.paid=true");
 await waitFor("document.body.innerText.includes('Payment successful')");
 assert.equal(await evaluate("document.body.innerText.includes('Your payment needs a review')"),false);
 // Holds/refund obligations never become an invitation to pay again or a success banner.
 for(const kind of ['review','refund']){
  await evaluate(`window.created.paymentReviewRequired=true;window.created.refundStatus=${JSON.stringify(kind==='refund'?'REQUIRED':'NONE')};sessionStorage.setItem('payment-test-order',JSON.stringify(window.created))`);
  await reload();await waitFor(kind==='review'?"document.body.innerText.includes('Your payment needs a review')":"document.body.innerText.includes('Refund status: REQUIRED')");
  assert.equal(await evaluate("document.body.innerText.includes('Payment successful')||document.body.innerText.includes('Continue to PayPal')"),false);
 }
 await evaluate("sessionStorage.removeItem('nakorn-pickup-receipt');sessionStorage.removeItem('nakorn-pending-pickup')");
 await reload();await waitFor("document.body.innerText.includes('No order receipt is available in this browser tab.')");
 assert.ok(await evaluate("Array.from(document.querySelectorAll('a')).some(a=>a.textContent==='Recover order tracking')"));
 assert.equal(await evaluate("window.requests.some(r=>r.url.startsWith('/api/payments/'))"),false);
 // Failed setup: save the receipt, offer checks, and never automatically repeat the write.
 await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.removeItem('payment-test-order');sessionStorage.setItem('payment-test-setup-error','true');sessionStorage.setItem('payment-test-no-verification','true')`);
 await send('Page.navigate',{url:`${base}?case=setup-failed#/checkout`});await waitFor("document.querySelector('input[value=PAYPAL]')");
 await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');await evaluate("document.querySelector('input[value=PAYPAL]').click()");
 await click('Continue to PayPal');await waitFor("document.body.innerText.includes(\"We're still checking your payment\")");
 const failedReceipt=await evaluate("JSON.parse(sessionStorage.getItem('nakorn-pickup-receipt'))");
 assert.equal(await evaluate(`window.requests.filter(r=>r.url==='/api/payments/${failedReceipt.requestId}').length`),1);
 assert.equal(approvalNavigations.length,2);
 await reload();await waitFor("document.body.innerText.includes(\"We're still checking your payment\")");
 assert.equal(await evaluate(`window.requests.some(r=>r.url==='/api/payments/${failedReceipt.requestId}')`),false);
 // Explicit retry is offered only if the backend says starting this same order is safe.
 await evaluate("window.checkMode='not-started';window.setupError=false");await click('Check payment status');await waitFor("document.body.innerText.includes('Payment not completed')");
 await evaluate("window.checkMode='approval'");await click('Continue to PayPal');await waitFor("window.location.hash==='#/mock-paypal'");
 assert.equal(approvalNavigations.length,3);
 assert.equal(lastCheckout.requests.filter(r=>r.url===`/api/payments/${failedReceipt.requestId}`).length,1);
 await send('Page.navigate',{url:`${base}?case=reset-after-retry#/order-confirmation`});await waitFor("document.querySelector('h1')?.textContent==='Your pickup order'");
 await evaluate("sessionStorage.removeItem('payment-test-mode');sessionStorage.removeItem('payment-test-setup-error');sessionStorage.removeItem('payment-test-no-verification');sessionStorage.removeItem('payment-test-fast-checks')");
 for(const method of ['PAYID','PAY_AT_RESTAURANT']){
  await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.removeItem('payment-test-order');sessionStorage.removeItem('nakorn-pickup-receipt')`);
  await send('Page.navigate',{url:`${base}?paymentCase=${method}#/checkout`});await waitFor("document.querySelector('input[value=PAYID]')");
  await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');
  await evaluate(`document.querySelector('input[value=${method}]').click()`);
  await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
  await input('[autocomplete=one-time-code]','123456');await click('Verify code');await waitFor("document.body.innerText.includes('Verified')");
  await click('Place pickup order');
  await waitFor(method==='PAYID'?"document.body.innerText.includes('merchant@example.com')":"document.body.innerText.includes('Pay at the restaurant when collecting.')");
  assert.equal(await evaluate("window.requests.find(r=>r.url==='/api/orders'&&r.body).body.paymentMethod"),method);
  if(method==='PAYID'){
   const text=await evaluate('document.body.innerText');
   for(const expected of ['Test Restaurant','$19.90 AUD','Bank reference:','Verify the recipient','awaiting payment confirmation'])assert.ok(text.includes(expected),expected);
   assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).some(b=>/I paid|Confirm bank receipt/i.test(b.textContent))"),false);
   await reload();await waitFor("document.body.innerText.includes('merchant@example.com')");
  }
 }

 // Leaving during a slow order response keeps the original saved submission.
 await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.removeItem('nakorn-pickup-receipt');sessionStorage.removeItem('payment-test-order');sessionStorage.setItem('payment-test-no-verification','true')`);
 await send('Page.navigate',{url:`${base}?case=back-during-submit#/checkout`});await waitFor("document.querySelector('[autocomplete=name]')");
 await input('[autocomplete=name]','Guest');await input('[autocomplete=tel]','0412345678');await evaluate('window.holdOrder=true');
 await click('Place pickup order');await waitFor('window.releaseOrder');
 const interrupted=await evaluate("JSON.parse(sessionStorage.getItem('nakorn-pending-pickup'))");
 await evaluate("window.location.hash='/menu'");await waitFor("document.querySelector('h1')?.textContent==='Our menu'");
 await evaluate('window.releaseOrder()');await new Promise(resolve=>setTimeout(resolve,100));
 assert.equal(await evaluate('window.location.hash'),'#/menu');
 await evaluate("window.location.hash='/checkout'");await waitFor("document.body.innerText.includes('Your submission is saved')");
 await click('Continue saved order');await waitFor("document.body.innerText.includes('Payment due at pickup')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders'&&r.body).length"),1);
 assert.equal(await evaluate("JSON.parse(sessionStorage.getItem('nakorn-pickup-receipt')).requestId"),interrupted.requestId);
 assert.equal(await evaluate("window.requests.some(r=>r.url.startsWith('/api/payments/')&&r.body)"),false);
 await evaluate("sessionStorage.removeItem('payment-test-no-verification')");
 await evaluate(`sessionStorage.setItem('nakorn-pickup-cart',${JSON.stringify(savedCart)});sessionStorage.setItem('payment-test-disabled','true')`);
 await send('Page.navigate',{url:`${base}?paymentCase=disabled#/checkout`});await waitFor("document.querySelector('input[value=PAY_AT_RESTAURANT]')");
 await waitFor("window.requests.some(r=>r.url==='/api/payments/options')");
 assert.equal(await evaluate("!!document.querySelector('input[value=PAYPAL],input[value=PAYID]')"),false);

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
 await click('Review payment and refund obligations');
 await waitFor("document.querySelector('[aria-label=\"Payment and refund review\"]').innerText.includes('REQUIRED')");
 assert.equal(await evaluate("document.querySelector('[aria-label=\"Payment and refund review\"]').innerText.includes('PROVIDER_UNAVAILABLE')"),true);
 assert.deepEqual(errors,[]);console.log('PASS: customer payment UX, intercepted automatic redirect, duplicate submission, callback routing, bounded verification, uncertainty/recovery, captured and reviewed states, missing receipt, PayID/cash, staff safeguards and responsive layouts.');
}finally{await send('Page.close').catch(()=>{});ws.close();}
