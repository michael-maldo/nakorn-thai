import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { submitOrder, getOrder, getStaffOrders, changeOrderStatus, verifyStaffPayment } from './orderApi.js';
import { cartReducer, cartTotal } from '../model/cartReducer.js';
import { configurationKey } from '../model/cartModel.js';
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('cart merges variations, preserves cents and caps quantities', () => {
  const line = { collectionId: 'collection', variationId: 'a', selectedOptions: [], unitPriceMinor: 2490 };
  const key = configurationKey(line);
  let cart = cartReducer([], { type: 'add', line });
  cart = cartReducer(cart, { type: 'add', line });
  assert.equal(cart.length, 1); assert.equal(cartTotal(cart), 4980);
  assert.equal(cartReducer(cart, { type: 'quantity', id: key, quantity: 30 })[0].quantity, 20);
  assert.equal(cartReducer(cart, { type: 'quantity', id: key, quantity: 0 })[0].quantity, 1);
  assert.deepEqual(cartReducer(cart, { type: 'remove', id: key }), []);
});

test('cart limits distinct lines and can refresh prices', () => {
  let cart = [];
  for (let i=0;i<31;i++) cart = cartReducer(cart, { type: 'add', line: { collectionId: 'collection', variationId: String(i), selectedOptions: [], unitPriceMinor: 100 } });
  assert.equal(cart.length, 30);
  assert.equal(cartTotal(cartReducer(cart, { type: 'replace', lines: [{ collectionId: 'collection', variationId: 'a', selectedOptions: [], quantity: 2, unitPriceMinor: 2605 }] })), 5210);
});

test('checkout retries preserve the request key and exact payload', async () => {
  const payload = { requestId: 'fixed-key', trackingToken: 'private-token', items: [] };
  const submitted = [];
  globalThis.fetch = async (url, options) => {
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf');
    assert.equal(options.credentials, 'same-origin');
    submitted.push(options.body); return Response.json({ id: 'fixed-key' }, { status: 201 });
  };
  await submitOrder(payload); await submitOrder(payload);
  assert.equal(submitted[0], submitted[1]);
});

test('private tracking uses a header rather than a URL secret', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/orders/order-id');
    assert.equal(options.headers['X-Order-Token'], 'secret');
    return Response.json({ status: 'NEW' });
  };
  assert.equal((await getOrder({ requestId: 'order-id', trackingToken: 'secret' })).status, 'NEW');
});

test('staff polling cannot intervene between fresh CSRF and a status mutation', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    await new Promise((resolve) => setTimeout(resolve, 2));
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    if (options.method === 'PATCH') { assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh'); return new Response(null, { status: 204 }); }
    return Response.json([]);
  };
  await Promise.all([getStaffOrders('Basic test', false), changeOrderStatus('id', { status: 'ACCEPTED', version: 0 }, 'Basic test'), getStaffOrders('Basic test', true)]);
  assert.deepEqual(calls, ['/api/staff/foh/orders?history=false', '/api/staff/orders/csrf', '/api/staff/orders/id/status', '/api/staff/kitchen/orders']);
});

test('failed CSRF request prevents submitting an order', async () => {
  let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response(null, { status: 503 }); };
  await assert.rejects(submitOrder({}), /unavailable/); assert.equal(calls, 1);
});

test('availability conflicts keep the server explanation and status', async () => {
  globalThis.fetch = async (url) => url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'token' }) : Response.json({ message: 'A price changed; review your cart' }, { status: 409 });
  await assert.rejects(submitOrder({}), (error) => error.status === 409 && error.message.includes('price changed'));
});

test('payment verification preserves version and serializes CSRF with queue refresh', async () => {
  const calls = [];
  const command = { version: 7, bankReference: 'BANK-123' };
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    if (options.method === 'POST') {
      assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh');
      assert.equal(options.headers.Authorization, 'Basic test');
      assert.deepEqual(JSON.parse(options.body), command);
      return Response.json({ paid: true });
    }
    return Response.json([]);
  };
  await Promise.all([verifyStaffPayment('id', command, 'Basic test'), getStaffOrders('Basic test', false)]);
  assert.deepEqual(calls, ['/api/staff/orders/csrf', '/api/staff/payments/id/payid-confirm', '/api/staff/foh/orders?history=false']);
});

test('availability reads forward the observer abort signal to fetch',async()=>{
 const original=globalThis.fetch;const signal=new AbortController().signal;let received;
 globalThis.fetch=async(_url,options)=>{received=options.signal;return Response.json({enabled:true});};
 try{await (await import('./orderApi.js')).getOrderingOptions(signal);assert.equal(received,signal);}finally{globalThis.fetch=original;}
});

import {getReadOnlyOrder} from './orderApi.js';
test('read-only tracking transports its credential in a separate header with no cookies, cache or URL secrets',async()=>{
 const original=globalThis.fetch;let seen;const controller=new AbortController();
 globalThis.fetch=async(url,options)=>{seen={url,options};return Response.json({reference:'ABCD1234',status:'READY'});};
 try{assert.equal((await getReadOnlyOrder('e'.repeat(64),controller.signal)).status,'READY');assert.equal(seen.url,'/api/order-tracking');assert.deepEqual(seen.options.headers,{'X-Order-View-Token':'e'.repeat(64)});assert.equal(seen.options.credentials,'omit');assert.equal(seen.options.cache,'no-store');assert.equal(seen.options.referrerPolicy,'no-referrer');assert.equal(seen.options.signal,controller.signal);}finally{globalThis.fetch=original;}
});
test('read-only tracking has friendly expiry and unavailable errors without reflecting server details',async()=>{
 const original=globalThis.fetch;
 try{for(const status of [404,503]){globalThis.fetch=async()=>Response.json({message:'secret-server-detail'},{status});await assert.rejects(getReadOnlyOrder('e'.repeat(64)),error=>error.status===status&&!error.message.includes('secret-server-detail')&&error.message.includes(status===404?'expired':'temporarily unavailable'));}}finally{globalThis.fetch=original;}
});
