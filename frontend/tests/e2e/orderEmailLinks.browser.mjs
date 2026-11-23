/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const base = process.env.ORDER_LINKS_TEST_URL || 'http://127.0.0.1:5174/';
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
  } else if (message.method === 'Runtime.exceptionThrown') {
    const details = message.params.exceptionDetails;
    errors.push(details.exception?.description ?? details.text);
  }
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
function fixture(expectedToken){
 if (location.protocol === 'about:') return;
 window.requests=[];window.readOnlyExpired=false;window.staff=false;window.liveStatus='NEW';window.expired=false;
 sessionStorage.clear();
 const id='abcd1234-0000-4000-8000-000000000001';
 const order=()=>({id,reference:'ABCD1234',status:window.liveStatus,version:0,totalMinor:9920,currency:'AUD',customerName:'Alex Sample',phone:'+61400000000',notes:'Sample notes',paymentMethod:'PAY_AT_RESTAURANT',paidAt:null,createdAt:'2030-10-07T07:05:00Z',refundStatus:window.liveStatus==='CANCELLED'?'COMPLETED':'NONE',items:[]});
 const identity=()=>({accessToken:'staff-token',expiresAt:new Date(Date.now()+900000).toISOString(),user:{username:'review',role:'FOH'}});
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  window.requests.push({url,headers:options.headers,body:options.body?JSON.parse(options.body):null});
  if(url==='/api/order-tracking')return window.readOnlyExpired||options.headers?.['X-Order-View-Token']!==expectedToken?new Response(null,{status:404}):Response.json({reference:'ABCD1234',status:window.liveStatus,totalMinor:9920,currency:'AUD',fulfilment:'PICKUP',timezone:'Australia/Melbourne',estimatedReadyAt:'2030-10-07T07:45:00Z',updatedAt:'2030-10-07T07:05:00Z',customerInstructions:'Please pack sauces separately.',items:[{name:'Green Curry',variation:'Chicken',quantity:2,unitPriceMinor:2640,lineTotalMinor:5280,options:[{groupName:'Extras',name:'Extra vegetables',quantity:1,priceDeltaMinor:250}]},{name:'Pad Thai',variation:'Prawns',quantity:1,unitPriceMinor:3290,lineTotalMinor:3290,options:[{groupName:'Extras',name:'Extra prawns',quantity:2,priceDeltaMinor:300}]},{name:'Jasmine Rice',variation:'Large',quantity:3,unitPriceMinor:450,lineTotalMinor:1350,options:[]}]});
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return window.staff?Response.json(identity()):new Response(null,{status:401});
  if(url==='/api/identity/login'){window.staff=true;return Response.json(identity());}
  if(url==='/api/menu/homepage')return Response.json([]);
  if(url.startsWith('/api/orders/'))return window.expired||options.headers?.['X-Order-Token']!=='c'.repeat(64)?new Response(null,{status:404}):Response.json(order());
  if(url.startsWith('/api/staff/orders/'))return !window.staff?new Response(null,{status:401}):Response.json({...order(),id:url.split('/').at(-1),reference:url.endsWith(id)?'ABCD1234':'SECOND01'});
  if(url==='/api/payments/options')return Response.json({paypal:false,payid:false,payAtRestaurant:true});
  if(url.startsWith('/api/payments/'))return Response.json({method:'PAY_AT_RESTAURANT',status:'PENDING',paid:false});
  throw new Error('Unexpected API: '+url);
 };
}
const input=async(selector,value)=>evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('input',{bubbles:true}));})()`);
try{
 const email = await readFile(new URL('../../../docs/ordering/email-samples/customer-order-received.txt', import.meta.url), 'utf8');
 const link = new URL(email.match(/^https:\/\/[^\s]+#\/track-order\?t=[a-f0-9]{64}$/m)?.[0]);
 const token = new URLSearchParams(link.hash.split('?')[1]).get('t');
 await send('Page.enable');await send('Runtime.enable');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})(${JSON.stringify(token)})`});
 const id='abcd1234-0000-4000-8000-000000000001';
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await send('Page.navigate',{url:`${base}${link.hash}`});
 await waitFor("document.body.innerText.includes('Order total: AUD 99.20')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/order-verification/')).length"),0);
 assert.equal(await evaluate(`window.requests.filter(r=>r.url==='/api/order-tracking').every(r=>r.headers['X-Order-View-Token']===${JSON.stringify(token)}&&!r.headers['X-Order-Token']&&!r.headers.Authorization)`),true);
 assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);
 assert.equal(await evaluate("document.body.innerText.includes('Extra prawns × 2') && document.body.innerText.includes('AUD 6.00') && document.body.innerText.includes('Please pack sauces separately.')"),true);
 await evaluate("window.liveStatus='READY'");await waitFor("document.body.innerText.includes('ready for pickup at Nakorn Thai')");
 await evaluate("window.readOnlyExpired=true");await waitFor("document.body.innerText.includes('has expired or is invalid')");assert.equal(await evaluate("document.body.innerText.includes('Your order')"),false);
 await evaluate("window.readOnlyExpired=false");await send('Page.reload');await waitFor("document.body.innerText.includes('Order total: AUD 99.20')");
 assert.equal(await evaluate(`Object.values(sessionStorage).some(v=>v.includes(${JSON.stringify(token)}))`),false);
 assert.equal(await evaluate(`Object.values(localStorage).some(v=>v.includes(${JSON.stringify(token)}))`),false);
 await evaluate("location.hash='/track-order?t=bad'");await waitFor("document.body.innerText.includes('missing or invalid')");
 const reads = await evaluate("requests.filter(r=>r.url==='/api/order-tracking').length");
 // Old email fallback links carry only the order ID and show contact guidance.
 await evaluate(`location.hash='/track-order?order=${id}'`);
 await waitFor("document.querySelector('.tracking-page [role=alert]')?.textContent.includes('missing or invalid')");
 assert.equal(await evaluate("document.querySelector('.tracking-page form')"),null);
 assert.equal(await evaluate("requests.filter(r=>r.url==='/api/order-tracking').length"),reads);
 await evaluate(`location.hash=${JSON.stringify(link.hash)};window.liveStatus='CANCELLED'`);
 await waitFor("document.body.innerText.includes('Your order has been cancelled.')");
 assert.equal(await evaluate("document.querySelector('.tracking-steps')"),null);
 await evaluate(`document.querySelector('.tracking-page a[href="#contact"]').click()`);
 await waitFor("location.hash==='#contact' && document.querySelector('#contact .contact-panel')");

 for (const retired of ['#/recover-order', `#/recover-order?order=${id}`]) {
  await send('Page.navigate',{url:'about:blank'});
  await send('Page.navigate',{url:`${base}${retired}`});
  await waitFor("location.hash==='#home' && document.querySelector('.hero') && document.querySelector('.site-footer')");
  assert.equal(await evaluate(`document.querySelector('a[href*="recover-order"]') || document.querySelector('.tracking-page')`),null);
  assert.equal(await evaluate("document.querySelector('.site-footer').textContent.includes('Track an order')"),false);
 }
 await evaluate(`window.liveStatus='CANCELLED';sessionStorage.setItem('nakorn-pickup-receipt',JSON.stringify({requestId:${JSON.stringify(id)},trackingToken:'c'.repeat(64)}));location.hash='/order-confirmation'`);
 await waitFor("document.body.innerText.includes('Order ABCD1234') && document.body.innerText.includes('Refund status: COMPLETED')");
 assert.equal(await evaluate("requests.filter(r=>r.url==='/api/orders/abcd1234-0000-4000-8000-000000000001').every(r=>r.headers['X-Order-Token']==='c'.repeat(64))"),true);
 assert.equal(await evaluate("document.querySelector('.receipt-tracking a').getAttribute('href')"),'#contact');
 assert.equal(await evaluate(`document.querySelector('a[href*="recover-order"]')`),null);
 // An inaccessible receipt still offers a way to contact the restaurant.
 await evaluate("window.expired=true;location.hash='#home'");
 await waitFor("document.querySelector('.hero')");
 await evaluate("location.hash='/order-confirmation'");
 await waitFor("document.body.innerText.includes('Tracking access expired or is invalid')");
 assert.equal(await evaluate(`document.querySelector(".customer-receipt a[href='#contact']")!==null`),true);
 assert.equal(await evaluate("requests.some(r=>r.url.startsWith('/api/order-verification/'))"),false);
 await evaluate("location.hash='/staff/orders?order="+id+"'");await waitFor("document.querySelector('[name=username]')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/staff/orders/')).length"),0);
 await input('[name=username]','review');await input('[name=password]','fictional-test-password');
 await evaluate("[...document.querySelectorAll('button')].find(button=>button.textContent.trim()==='Sign in').click()");
 await waitFor("document.body.innerText.includes('#ABCD1234')");
 assert.equal(await evaluate('location.hash'),`#/staff/orders?order=${id}`);
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/staff/orders/')).every(r=>r.headers.Authorization==='Bearer staff-token'&&!r.headers['X-Order-Token'])"),true);
 await evaluate("location.hash='/staff/orders?order=abcd1234-0000-4000-8000-000000000002'");await waitFor("document.body.innerText.includes('#SECOND01')");
 assert.deepEqual(errors,[]);
 console.log('Order email links browser: sample email link, private tracking, status polling, pickup, cancellation, receipt refund, retired-route redirects, legacy links, staff sign-in and deep links passed.');
}finally{await send('Page.close');ws.close();}
