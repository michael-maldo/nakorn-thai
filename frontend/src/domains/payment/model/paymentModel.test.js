import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizePayPalReturnHash, paypalReturnMode, safeApprovalUrl } from './paymentModel.js';
test('observed sandbox return and cancel hashes resolve to the exact confirmation route', () => {
 for (const outcome of ['return', 'cancel']) {
  const location = new URL(`https://nakorn-thai.tech-labs.dev/?paypal=${outcome}#/order-confirmation&token=4D100105X1957711S&PayerID=3AEHFAN7TUYQ6`);
  assert.equal(normalizePayPalReturnHash(location.hash).split('?')[0], '#/order-confirmation');
  assert.equal(paypalReturnMode(location), outcome);
  assert.equal(location.hash, '#/order-confirmation&token=4D100105X1957711S&PayerID=3AEHFAN7TUYQ6');
 }
 assert.equal(normalizePayPalReturnHash('#/order-confirmation&PayerID=payer&token=provider'), '#/order-confirmation?PayerID=payer&token=provider');
});
test('normal confirmation and both valid query placements remain unchanged', () => {
 for (const url of [
  'https://example.test/#/order-confirmation',
  'https://example.test/?paypal=return&token=provider&PayerID=payer#/order-confirmation',
  'https://example.test/#/order-confirmation?paypal=cancel&token=provider&PayerID=payer',
 ]) {
  const location = new URL(url);
  assert.equal(normalizePayPalReturnHash(location.hash), location.hash);
  assert.equal(normalizePayPalReturnHash(location.hash).split('?')[0], '#/order-confirmation');
 }
});
test('PayPal normalization preserves other routes and rejects confirmation near-matches', () => {
 for (const hash of ['#/menu', '#/menu?collection=lunch', '#/staff/orders?order=id', '#/track-order?t=private',
  '#/order-confirmation-extra&token=provider', '#/order-confirmationevil&PayerID=payer',
  '#/order-confirmation/extra&token=provider', '#/order-confirmation&notToken=provider',
  '#/order-confirmation&tokenized=provider', '#/order-confirmation%26token=provider',
  '#/staff/orders&token=provider', '#menu', '']) {
  assert.equal(normalizePayPalReturnHash(hash), hash);
  assert.notEqual(normalizePayPalReturnHash(hash).split('?')[0], '#/order-confirmation');
 }
});
test('PayPal return and cancel markers work before and after hash',()=>{
 assert.equal(paypalReturnMode({search:'?paypal=return&token=PP',hash:'#/order-confirmation'}),'return');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation?paypal=cancel'}),'cancel');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation'}),'');
});
test('only HTTPS PayPal approval destinations can be presented',()=>{
 assert.equal(safeApprovalUrl('https://www.sandbox.paypal.com/checkoutnow?token=PP'),'https://www.sandbox.paypal.com/checkoutnow?token=PP');
 for(const url of ['javascript:alert(1)','http://www.paypal.com/','https://paypal.com.evil.test/','https://user@paypal.com/','https://evil.test/',undefined])assert.equal(safeApprovalUrl(url),null);
});
