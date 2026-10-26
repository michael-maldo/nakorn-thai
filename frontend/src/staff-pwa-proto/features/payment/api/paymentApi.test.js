import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { beginPayPalCheckout, paymentRequest } from './paymentApi.js';
const original = globalThis.fetch;
afterEach(() => { globalThis.fetch = original; });
test('payment writes keep private tracking token in header and fetch CSRF first', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return Response.json(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'csrf' } : { paid: false });
  };
  const result = await paymentRequest('/api/payments/order-id', { receipt: { trackingToken: 'private' }, body: { method: 'PAYPAL' } });
  assert.equal(result.paid, false);
  assert.equal(calls[0].url, '/api/orders/csrf');
  assert.equal(calls[1].options.headers['X-Order-Token'], 'private');
  assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'csrf');
  assert.equal(calls[1].url.includes('private'), false);
});
test('checkout starts the saved order once then checks authoritative continuation permission',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push({url,options});return Response.json(url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'csrf'}:url.endsWith('/check')?{paid:false,canResumeApproval:true}:{paid:false,approvalUrl:'https://www.paypal.com/'});};
 assert.equal((await beginPayPalCheckout({requestId:'saved-id',trackingToken:'private'})).canResumeApproval,true);
 const writes=calls.filter(c=>c.options.method==='POST');
 assert.deepEqual(writes.map(c=>c.url),['/api/payments/saved-id','/api/payments/saved-id/check']);
 assert.ok(writes.every(c=>c.options.headers['X-Order-Token']==='private'));
});
test('a failed or uncertain setup is never automatically retried',async()=>{
 let writes=0;globalThis.fetch=async(url)=>url.endsWith('/csrf')?Response.json({headerName:'X-CSRF-TOKEN',token:'csrf'}):(writes++,Response.json({message:'Unavailable'},{status:502}));
 await assert.rejects(beginPayPalCheckout({requestId:'same-order',trackingToken:'private'}),/Unavailable/);assert.equal(writes,1);
});
test('navigation abort during CSRF prevents a delayed payment write',async()=>{
 const controller=new AbortController();let writes=0;
 globalThis.fetch=async(url)=>{if(url.endsWith('/csrf')){controller.abort();return Response.json({headerName:'X-CSRF-TOKEN',token:'csrf'});}writes++;return Response.json({});};
 await assert.rejects(beginPayPalCheckout({requestId:'saved',trackingToken:'private'},controller.signal),{name:'AbortError'});assert.equal(writes,0);
});
test('failed CSRF stops payment requests', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response('', { status: 403 }); };
  await assert.rejects(paymentRequest('/api/payments/id/check', { body: {} }), /failed/);
  assert.equal(calls, 1);
});
test('provider failure is shown without treating it as a successful payment', async () => {
  globalThis.fetch = async () => Response.json({ message: 'PayPal status unavailable' }, { status: 502 });
  await assert.rejects(paymentRequest('/api/payments/options'), /PayPal status unavailable/);
});

test('availability reads forward the observer abort signal to fetch',async()=>{
 const original=globalThis.fetch;const signal=new AbortController().signal;let received;
 globalThis.fetch=async(_url,options)=>{received=options.signal;return Response.json({enabled:true});};
 try{await paymentRequest('/api/payments/options',{signal});assert.equal(received,signal);}finally{globalThis.fetch=original;}
});
