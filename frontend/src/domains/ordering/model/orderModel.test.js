import { test } from 'node:test';
import assert from 'node:assert/strict';
import { needsPayment, orderActions } from './orderModel.js';

test('FOH accepts, cancels and hands over but cannot update preparation', () => {
  assert.deepEqual(orderActions('FOH', 'NEW'), ['ACCEPTED', 'CANCELLED']);
  assert.deepEqual(orderActions('FOH', 'ACCEPTED'), ['CANCELLED']);
  assert.deepEqual(orderActions('FOH', 'PREPARING'), ['CANCELLED']);
  assert.deepEqual(orderActions('FOH', 'READY'), ['COMPLETED', 'CANCELLED']);
});
test('BOH prepares accepted orders and marks them ready without front desk actions', () => {
  assert.deepEqual(orderActions('BOH', 'NEW'), []);
  assert.deepEqual(orderActions('BOH', 'ACCEPTED'), ['PREPARING']);
  assert.deepEqual(orderActions('BOH', 'PREPARING'), ['READY']);
  assert.deepEqual(orderActions('BOH', 'READY'), []);
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
