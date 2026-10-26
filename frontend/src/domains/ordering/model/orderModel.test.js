import { test } from 'node:test';
import assert from 'node:assert/strict';
import { needsPayment, orderActions } from './orderModel.js';

for (const role of ['FOH','BOH']) test(`${role} performs every non-cancellation transition`, () => {
 for (const [status,next] of Object.entries({NEW:'ACCEPTED',ACCEPTED:'PREPARING',PREPARING:'READY',READY:'COMPLETED'})) {
  assert.deepEqual(orderActions(role,status),[next]);
  assert.ok(!orderActions(role,status).includes('CANCELLED'));
 }
});
test('ADMIN can manage the entire lifecycle; terminal states have no actions', () => {
  for (const [status, next] of Object.entries({ NEW: 'ACCEPTED', ACCEPTED: 'PREPARING', PREPARING: 'READY', READY: 'COMPLETED' })) {
    assert.deepEqual(orderActions('ADMIN', status), [next, 'CANCELLED']);
  }
  for (const role of ['ADMIN', 'FOH', 'BOH', 'UNKNOWN']) {
    assert.deepEqual(orderActions(role, 'COMPLETED'), []);
    assert.deepEqual(orderActions(role, 'CANCELLED'), []);
  }
});
test('online payments must be verified; pay at restaurant can be accepted unpaid', () => {
  assert.equal(needsPayment({ paymentMethod: 'PAY_AT_RESTAURANT' }), false);
  assert.equal(needsPayment({ paymentMethod: 'PAYID' }), true);
  assert.equal(needsPayment({ paymentMethod: 'PAYPAL' }), true);
  assert.equal(needsPayment({ paymentMethod: 'PAYID', paidAt: '2026-09-25T00:00:00Z' }), false);
});
test('historical capture does not permit acceptance after a financial hold or refund', () => {
  const paid = { paymentMethod: 'PAYPAL', paidAt: '2026-10-19T00:00:00Z' };
  assert.equal(needsPayment(paid), false);
  assert.equal(needsPayment({ ...paid, paymentReviewRequired: true }), true);
  assert.equal(needsPayment({ ...paid, refundStatus: 'COMPLETED' }), true);
  assert.equal(needsPayment({ paymentMethod: 'PAY_AT_RESTAURANT', paymentReviewRequired: false }), false);
});
