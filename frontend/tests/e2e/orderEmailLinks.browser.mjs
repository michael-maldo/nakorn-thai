/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
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
function fixture(){
 window.requests=[];window.readOnlyExpired=false;window.staff=false;window.liveStatus='NEW';window.expired=false;window.verifyError=true;
 sessionStorage.clear();
 const id='abcd1234-0000-4000-8000-000000000001';
 const order=()=>({id,reference:'ABCD1234',status:window.liveStatus,version:0,totalMinor:9920,currency:'AUD',customerName:'Alex Sample',phone:'+61400000000',notes:'Sample notes',paymentMethod:'PAY_AT_RESTAURANT',paidAt:null,createdAt:'2030-10-07T07:05:00Z',items:[]});
 const identity=()=>({accessToken:'staff-token',expiresAt:new Date(Date.now()+900000).toISOString(),user:{username:'review',role:'FOH'}});
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  window.requests.push({url,headers:options.headers,body:options.body?JSON.parse(options.body):null});
  if(url==='/api/order-tracking')return window.readOnlyExpired?new Response(null,{status:404}):Response.json({reference:'ABCD1234',status:window.liveStatus,totalMinor:9920,currency:'AUD',fulfilment:'PICKUP',timezone:'Australia/Melbourne',estimatedReadyAt:'2030-10-07T07:45:00Z',updatedAt:'2030-10-07T07:05:00Z',customerInstructions:'Please pack sauces separately.',items:[{name:'Green Curry',variation:'Chicken',quantity:2,unitPriceMinor:2640,lineTotalMinor:5280,options:[{groupName:'Extras',name:'Extra vegetables',quantity:1,priceDeltaMinor:250}]},{name:'Pad Thai',variation:'Prawns',quantity:1,unitPriceMinor:3290,lineTotalMinor:3290,options:[{groupName:'Extras',name:'Extra prawns',quantity:2,priceDeltaMinor:300}]},{name:'Jasmine Rice',variation:'Large',quantity:3,unitPriceMinor:450,lineTotalMinor:1350,options:[]}]});
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return window.staff?Response.json(identity()):new Response(null,{status:401});
  if(url==='/api/identity/login'){window.staff=true;return Response.json(identity());}
  if(url==='/api/order-verification/options')return Response.json({sms:true,email:true});
  if(url==='/api/order-verification/start')return Response.json({challengeId:'challenge',message:'If that order has a contact, a code has been sent.'});
  if(url==='/api/order-verification/check')return window.verifyError?Response.json({message:'Code expired or invalid; request a new code'},{status:400}):Response.json({requestId:id,trackingToken:'c'.repeat(64),expiresAt:new Date(Date.now()+86400000).toISOString()});
  if(url.startsWith('/api/orders/'))return window.expired||options.headers?.['X-Order-Token']!=='c'.repeat(64)?new Response(null,{status:404}):Response.json(order());
  if(url.startsWith('/api/staff/orders/'))return !window.staff?new Response(null,{status:401}):Response.json({...order(),id:url.split('/').at(-1),reference:url.endsWith(id)?'ABCD1234':'SECOND01'});
  if(url==='/api/payments/options')return Response.json({paypal:false,payid:false,payAtRestaurant:true});
  if(url.startsWith('/api/payments/'))return Response.json({method:'PAY_AT_RESTAURANT',status:'PENDING',paid:false});
  throw new Error('Unexpected API: '+url);
 };
}
const input=async(selector,value)=>evaluate(`(()=>{const el=document.querySelector(${JSON.stringify(selector)});Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event('input',{bubbles:true}));})()`);
try{
 await send('Page.enable');await send('Runtime.enable');await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 const id='abcd1234-0000-4000-8000-000000000001';
 const token='e'.repeat(64);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 await send('Page.navigate',{url:`${base}#/track-order?t=${token}`});
 await waitFor("document.body.innerText.includes('Order total: AUD 99.20')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/order-verification/')).length"),0);
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/order-tracking').every(r=>r.headers['X-Order-View-Token']==='e'.repeat(64)&&!r.headers['X-Order-Token']&&!r.headers.Authorization)"),true);
 assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true);
 assert.equal(await evaluate("document.body.innerText.includes('Extra prawns × 2') && document.body.innerText.includes('AUD 6.00') && document.body.innerText.includes('Please pack sauces separately.')"),true);
 await evaluate("window.liveStatus='READY'");await waitFor("document.body.innerText.includes('ready for pickup at Nakorn Thai')");
 await evaluate("window.readOnlyExpired=true");await waitFor("document.body.innerText.includes('has expired or is invalid')");assert.equal(await evaluate("document.body.innerText.includes('Your order')"),false);
 await evaluate("window.readOnlyExpired=false");await send('Page.reload');await waitFor("document.body.innerText.includes('Order total: AUD 99.20')");
 assert.equal(await evaluate("Object.values(sessionStorage).some(v=>v.includes('e'.repeat(64)))"),false);
 assert.equal(await evaluate("Object.values(localStorage).some(v=>v.includes('e'.repeat(64)))"),false);
 await evaluate("location.hash='/track-order?t=bad'");await waitFor("document.body.innerText.includes('missing or invalid')");
 await send('Page.navigate',{url:`${base}#/track-order?order=${id}`});
 await waitFor("document.querySelector('input')?.value==='"+id+"'");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders/abcd1234-0000-4000-8000-000000000001').length"),0);
 await click('Send verification code');await waitFor("document.querySelector('[autocomplete=one-time-code]')");
 assert.equal(await evaluate("window.requests.find(r=>r.url.endsWith('/start')).body.orderId"),id);
 await input('[autocomplete=one-time-code]','123456');await click('Verify and track');await waitFor("document.body.innerText.includes('Code expired or invalid')");
 assert.equal(await evaluate("!!Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Request another code')"),true);
 await evaluate("window.verifyError=false;window.liveStatus='READY'");await click('Verify and track');await waitFor("document.body.innerText.includes('Ready for pickup')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url==='/api/orders/abcd1234-0000-4000-8000-000000000001').every(r=>r.headers['X-Order-Token']==='c'.repeat(64))"),true);
 assert.equal(await evaluate("location.href.includes('c'.repeat(64))"),false);
 await evaluate("window.expired=true");await waitFor("document.body.innerText.includes('Tracking access expired or is invalid')");
 assert.equal(await evaluate("document.body.innerText.includes('Ready for pickup')"),false);
 await evaluate("Array.from(document.querySelectorAll('a')).find(a=>a.textContent==='Recover order tracking').click()");await waitFor("document.querySelector('input')?.value==='"+id+"'");
 await evaluate("location.hash='/track-order?order=bad'");await waitFor("document.body.innerText.includes('This order link is invalid')");assert.equal(await evaluate("document.querySelector('input').value"),'');
 await evaluate("location.hash='/staff/orders?order="+id+"'");await waitFor("document.querySelector('[name=username]')");
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/staff/orders/')).length"),0);
 await input('[name=username]','review');await input('[name=password]','fictional-test-password');await click('Sign in');await waitFor("document.body.innerText.includes('#ABCD1234')");
 assert.equal(await evaluate('location.hash'),`#/staff/orders?order=${id}`);
 assert.equal(await evaluate("window.requests.filter(r=>r.url.startsWith('/api/staff/orders/')).every(r=>r.headers.Authorization==='Bearer staff-token'&&!r.headers['X-Order-Token'])"),true);
 await evaluate("location.hash='/staff/orders?order=abcd1234-0000-4000-8000-000000000002'");await waitFor("document.body.innerText.includes('#SECOND01')");
 assert.deepEqual(errors,[]);
 console.log('Order email links browser: one-click read-only tracking, live polling, itemization, mobile overflow, expiry, reusable link without storage, malformed tokens;  UUID locator prefill, verification required, current READY status, invalid/expired recovery, staff sign-in, retained deep link, bearer-only staff access and same-route order changes passed.');
}finally{await send('Page.close');ws.close();}
