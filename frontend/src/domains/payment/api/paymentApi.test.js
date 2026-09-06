import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { paymentRequest } from './paymentApi.js';
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
