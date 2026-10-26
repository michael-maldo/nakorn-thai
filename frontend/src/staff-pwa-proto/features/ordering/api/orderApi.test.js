import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { submitOrder, getOrder, getStaffOrders, changeOrderStatus, verifyStaffPayment } from './orderApi.js';
import { cartReducer, cartTotal } from '../model/cartReducer.js';
import { login, logout, identityState } from '../../identity/api/identityApi.js';
import { staffSessionFixture } from '../../../../../tests/e2e/staffIdentityFixtures.mjs';
import { configurationKey } from '../model/cartModel.js';
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
async function initializeStaff(session = staffSessionFixture()) {
  const original = globalThis.fetch;
  globalThis.fetch = async url => Response.json(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'csrf' } : session);
  try { await login('synthetic', 'synthetic'); } finally { globalThis.fetch = original; }
  return `Bearer ${session.accessToken}`;
}

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
  const authorization = await initializeStaff();
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    await new Promise((resolve) => setTimeout(resolve, 2));
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    if (options.method === 'PATCH') { assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh'); return new Response(null, { status: 204 }); }
    return Response.json([]);
  };
  await Promise.all([getStaffOrders(authorization, false), changeOrderStatus('id', { status: 'ACCEPTED', version: 0 }, authorization), getStaffOrders(authorization, true)]);
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
  const authorization = await initializeStaff();
  const calls = [];
  const command = { version: 7, bankReference: 'BANK-123' };
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    if (options.method === 'POST') {
      assert.equal(options.headers['X-CSRF-TOKEN'], 'fresh');
      assert.equal(options.headers.Authorization, authorization);
      assert.deepEqual(JSON.parse(options.body), command);
      return Response.json({ paid: true });
    }
    return Response.json([]);
  };
  await Promise.all([verifyStaffPayment('id', command, authorization), getStaffOrders(authorization, false)]);
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

test('queued staff writes stop before CSRF if the principal/page is no longer current', async () => {
  const authorization = await initializeStaff();
  const calls = []; let releaseRead; let current = true;
  globalThis.fetch = async url => {
    calls.push(url);
    return new Promise(resolve => { releaseRead = () => resolve(Response.json([])); });
  };
  const read = getStaffOrders(authorization, false);
  const status = assert.rejects(changeOrderStatus('id', { version: 1, status: 'CANCELLED' }, authorization, () => current), /cancelled/);
  const payment = assert.rejects(verifyStaffPayment('id', { version: 1 }, authorization, () => current), /cancelled/);
  await Promise.resolve(); current = false; releaseRead();
  await Promise.all([read, status, payment]);
  assert.deepEqual(calls, ['/api/staff/foh/orders?history=false']);
});
test('staff identity changing during CSRF acquisition prevents the mutation', async () => {
  const authorization = await initializeStaff();
  let current = true; const calls = [];
  globalThis.fetch = async url => { calls.push(url); current = false; return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' }); };
  await assert.rejects(changeOrderStatus('id', { version: 1 }, authorization, () => current), /cancelled/);
  current = true;
  await assert.rejects(verifyStaffPayment('id', { version: 1 }, authorization, () => current), /cancelled/);
  assert.deepEqual(calls, ['/api/staff/orders/csrf', '/api/staff/orders/csrf']);
});

const statusCommand = { version: 7, status: 'CANCELLED', reason: 'Synthetic reason', paymentCollected: false };
const paymentCommand = { version: 7, bankReference: 'BANK-123', bankReceiptChecked: true };
const operations = [
  { name: 'status', send: (authorization, guard) => changeOrderStatus('id', statusCommand, authorization, guard), command: statusCommand },
  { name: 'PayID', send: (authorization, guard) => verifyStaffPayment('id', paymentCommand, authorization, guard), command: paymentCommand },
  { name: 'PayPal', send: (authorization, guard) => verifyStaffPayment('id', undefined, authorization, guard), command: {} },
];
for (const operation of operations) {
  for (const phase of ['pre-dispatch refresh', '401 retry']) {
    test(`${operation.name} never dispatches another principal's token during ${phase}, even with a permissive guard`, async () => {
      const first = staffSessionFixture(), next = staffSessionFixture({ id: '22222222-2222-4222-8222-222222222222', sessionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', tokenId: 'other' });
      const authorization = await initializeStaff(first); const writes = [], csrf = [];
      globalThis.fetch = async (url, options = {}) => {
        if (url === '/api/staff/orders/csrf') {
          csrf.push(options.headers.Authorization);
          if (phase === 'pre-dispatch refresh') identityState().expiresAt = new Date(Date.now()+1000).toISOString();
          return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
        }
        if (url === '/api/identity/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'identity' });
        if (url === '/api/identity/refresh') return Response.json(next);
        writes.push({ url, options }); return new Response(null, { status: 401 });
      };
      await assert.rejects(operation.send(authorization, () => true), /cancelled/);
      assert.deepEqual(csrf, [`Bearer ${first.accessToken}`]);
      assert.equal(writes.length, phase === '401 retry' ? 1 : 0);
      assert.ok(writes.every(write => write.options.headers.Authorization === `Bearer ${first.accessToken}`));
      assert.equal(identityState().user.id, next.user.id);
    });
    test(`${operation.name} permits same-principal/session token rotation during ${phase} with exact payload and dispatch count`, async () => {
      const first = staffSessionFixture(), next = staffSessionFixture({ tokenId: 'rotated' });
      const authorization = await initializeStaff(first); const writes = []; let csrfCount = 0, refreshes = 0;
      globalThis.fetch = async (url, options = {}) => {
        assert.equal('beforeDispatch' in options, false);
        if (url === '/api/staff/orders/csrf') {
          csrfCount++;
          if (phase === 'pre-dispatch refresh') identityState().expiresAt = new Date(Date.now()+1000).toISOString();
          return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
        }
        if (url === '/api/identity/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'identity' });
        if (url === '/api/identity/refresh') { refreshes++; return Response.json(next); }
        writes.push(options);
        if (phase === '401 retry' && writes.length === 1) return new Response(null, { status: 401 });
        return operation.name === 'status' ? new Response(null, { status: 204 }) : Response.json({ paid: true });
      };
      await operation.send(authorization); // No optional guard: mandatory principal binding still applies.
      assert.equal(refreshes, 1); assert.equal(csrfCount, 1);
      assert.deepEqual(writes.map(write => write.headers.Authorization), phase === '401 retry'
        ? [`Bearer ${first.accessToken}`, `Bearer ${next.accessToken}`] : [`Bearer ${next.accessToken}`]);
      for (const write of writes) {
        assert.deepEqual(JSON.parse(write.body), operation.command);
        assert.equal(write.headers['X-CSRF-TOKEN'], 'fresh');
        assert.equal(write.credentials, 'same-origin');
      }
    });
  }
}
for (const change of [{ role: 'FOH' }, { sessionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb' }]) {
  test(`same UUID cannot carry a mutation across ${change.role ? 'role' : 'session'} changes`, async () => {
    const authorization = await initializeStaff(); let dispatches = 0;
    globalThis.fetch = async (url, options = {}) => {
      if (url === '/api/staff/orders/csrf') { identityState().expiresAt = new Date(Date.now()+1000).toISOString(); return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' }); }
      if (url === '/api/identity/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'identity' });
      if (url === '/api/identity/refresh') return Response.json(staffSessionFixture({ ...change, tokenId: 'changed' }));
      dispatches++; return new Response(null, { status: 204 });
    };
    await assert.rejects(changeOrderStatus('id', statusCommand, authorization, () => true), /cancelled/);
    assert.equal(dispatches, 0);
  });
}
test('sign-out cancels queued mutations without an optional caller guard', async () => {
  const authorization = await initializeStaff(); let releaseRead; const calls = [];
  globalThis.fetch = async (url, options = {}) => {
    calls.push(url);
    if (url.includes('/foh/orders')) return new Promise(resolve => { releaseRead = () => resolve(Response.json([])); });
    if (url === '/api/identity/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'identity' });
    if (url === '/api/identity/logout') return new Response(null, { status: 204 });
    throw new Error('A cancelled mutation reached the network');
  };
  const read = getStaffOrders(authorization, false);
  const status = assert.rejects(changeOrderStatus('id', statusCommand, authorization), /cancelled/);
  const payment = assert.rejects(verifyStaffPayment('id', paymentCommand, authorization), /cancelled/);
  await Promise.resolve(); await logout(); releaseRead();
  await Promise.all([read, status, payment]);
  assert.ok(!calls.includes('/api/staff/orders/csrf'));
});
test('missing/mismatched identity or credentials fail closed regardless of caller guard', async () => {
  const valid = staffSessionFixture();
  const fixtures = [
    { ...valid, user: null }, { ...valid, user: { ...valid.user, id: undefined } },
    { ...valid, user: { ...valid.user, enabled: false } }, { ...valid, user: { ...valid.user, role: 'UNKNOWN' } },
    { ...valid, accessToken: 'opaque' }, staffSessionFixture({ sessionId: 'not-a-session' }),
    { ...valid, user: { ...valid.user, id: '22222222-2222-4222-8222-222222222222' } },
  ];
  for (const session of fixtures) {
    const authorization = await initializeStaff(session); let calls = 0;
    globalThis.fetch = async () => { calls++; throw new Error('Must not dispatch'); };
    for (const guard of [undefined, () => true]) {
      await assert.rejects(changeOrderStatus('id', statusCommand, authorization, guard), /cancelled/);
      await assert.rejects(verifyStaffPayment('id', paymentCommand, authorization, guard), /cancelled/);
    }
    assert.equal(calls, 0);
  }
  await initializeStaff(valid); let calls = 0;
  globalThis.fetch = async () => { calls++; throw new Error('Must not dispatch'); };
  for (const authorization of [undefined, '', 'Basic test', `Bearer ${staffSessionFixture({ id: '22222222-2222-4222-8222-222222222222' }).accessToken}`]) {
    await assert.rejects(changeOrderStatus('id', statusCommand, authorization, () => true), /cancelled/);
  }
  assert.equal(calls, 0);
});
test('non-authentication failures never automatically retry mutations', async () => {
  for (const status of [403, 409, 500, 503]) {
    const authorization = await initializeStaff(); let writes = 0;
    globalThis.fetch = async url => url.endsWith('/csrf') ? Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' })
      : (writes++, Response.json({ message: 'Synthetic failure' }, { status }));
    await assert.rejects(changeOrderStatus('id', statusCommand, authorization)); assert.equal(writes, 1);
  }
});

test('principal switch during refresh before CSRF blocks both CSRF and mutation dispatch', async () => {
  const authorization = await initializeStaff(); identityState().expiresAt = new Date(Date.now()+1000).toISOString();
  const calls = [];
  globalThis.fetch = async url => {
    calls.push(url);
    if (url === '/api/identity/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'identity' });
    if (url === '/api/identity/refresh') return Response.json(staffSessionFixture({ id: '22222222-2222-4222-8222-222222222222' }));
    throw new Error('A bound staff request should not be sent');
  };
  await assert.rejects(changeOrderStatus('id', statusCommand, authorization, () => true), /cancelled/);
  assert.deepEqual(calls, ['/api/identity/csrf', '/api/identity/refresh']);
});
test('a second 401 ends the existing authentication retry instead of replaying repeatedly', async () => {
  const authorization = await initializeStaff(); let writes = 0, refreshes = 0;
  globalThis.fetch = async url => {
    if (url.endsWith('/csrf')) return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    if (url === '/api/identity/refresh') { refreshes++; return Response.json(staffSessionFixture({ tokenId: 'rotated' })); }
    writes++; return new Response(null, { status: 401 });
  };
  await assert.rejects(changeOrderStatus('id', statusCommand, authorization), error => error.status === 401);
  assert.equal(writes, 2); assert.equal(refreshes, 1);
});
