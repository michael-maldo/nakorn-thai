import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { submitOrder, getOrder, getStaffOrders, changeOrderStatus } from './orderApi.js';
import { cartReducer, cartTotal } from '../model/cartReducer.js';
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('cart merges variations, preserves cents and caps quantities', () => {
  const line = { variationId: 'a', unitPriceMinor: 2490 };
  let cart = cartReducer([], { type: 'add', line });
  cart = cartReducer(cart, { type: 'add', line });
  assert.equal(cart.length, 1); assert.equal(cartTotal(cart), 4980);
  assert.equal(cartReducer(cart, { type: 'quantity', id: 'a', quantity: 30 })[0].quantity, 20);
  assert.equal(cartReducer(cart, { type: 'quantity', id: 'a', quantity: 0 })[0].quantity, 1);
  assert.deepEqual(cartReducer(cart, { type: 'remove', id: 'a' }), []);
});

test('cart limits distinct lines and can refresh prices', () => {
  let cart = [];
  for (let i=0;i<31;i++) cart = cartReducer(cart, { type: 'add', line: { variationId: String(i), unitPriceMinor: 100 } });
  assert.equal(cart.length, 30);
  assert.equal(cartTotal(cartReducer(cart, { type: 'replace', lines: [{ quantity: 2, unitPriceMinor: 2605 }] })), 5210);
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
