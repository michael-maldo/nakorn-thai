import test from 'node:test';
import assert from 'node:assert/strict';
import { functionRequest } from './functionApi.js';

test('venue enquiry uses CSRF and retains its retry reference', async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return new Response(JSON.stringify(url.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'csrf' } : { reference: 'same-id' }));
  };
  try {
    const result = await functionRequest('/api/functions', { method: 'POST', body: { requestId: 'same-id' } });
    assert.equal(result.reference, 'same-id');
    assert.equal(calls[0].url, '/api/functions/csrf');
    assert.equal(calls[1].options.headers['X-CSRF-TOKEN'], 'csrf');
    assert.equal(JSON.parse(calls[1].options.body).requestId, 'same-id');
  } finally { globalThis.fetch = original; }
});
test('failed CSRF prevents enquiry submission and displays the error', async () => {
  const original = globalThis.fetch;
  let calls = 0;
  globalThis.fetch = async () => { calls++; return new Response(JSON.stringify({ message: 'Please retry your enquiry' }), { status: 503 }); };
  try {
    await assert.rejects(functionRequest('/api/functions', { method: 'POST', body: {} }), /Please retry your enquiry/);
    assert.equal(calls, 1);
  } finally { globalThis.fetch = original; }
});
