import test from 'node:test';
import assert from 'node:assert/strict';
import { createOrderDraft, orderDraftDirty, orderDraftConflict, orderDraftCommand, reconcileOrderQueue } from './staffOrderDrafts.js';
const order = { id: 'A', version: 3 };
test('status and payment drafts retain independent original versions and payloads', () => {
  const status = createOrderDraft(order, 'ACCEPTED'); status.values.pickupMinutes = '35';
  const payment = createOrderDraft(order, 'PAYID'); payment.values.bankReference = ' BANK '; payment.values.bankReceiptChecked = true;
  assert.equal(orderDraftDirty(status), true); assert.equal(orderDraftDirty(payment), true);
  assert.deepEqual(orderDraftCommand(status), { version: 3, status: 'ACCEPTED', pickupMinutes: 35, paymentCollected: false });
  assert.deepEqual(orderDraftCommand(payment), { version: 3, bankReference: 'BANK', bankReceiptChecked: true });
  assert.equal(orderDraftConflict(status, { ...order, version: 4 }), true);
  assert.equal(orderDraftCommand(status).version, 3);
  assert.equal(orderDraftConflict({ ...status, conflict: true }, order), true);
});
test('untouched payment fields are clean; cancellation and handover commands preserve contracts', () => {
  const payment = createOrderDraft(order, 'PAYID'); assert.equal(orderDraftDirty(payment), false);
  payment.values.bankReceiptChecked = true; assert.equal(orderDraftDirty(payment), true);
  const cancel = createOrderDraft(order, 'CANCELLED'); cancel.values.reason = ' unavailable ';
  assert.deepEqual(orderDraftCommand(cancel), { version: 3, status: 'CANCELLED', reason: 'unavailable', paymentCollected: false });
  const complete = createOrderDraft(order, 'COMPLETED'); complete.values.paymentCollected = true;
  assert.deepEqual(orderDraftCommand(complete), { version: 3, status: 'COMPLETED', paymentCollected: true });
});
test('authoritative snapshots reorder independently of drafts; missing dirty orders remain unsaveable', () => {
  const other = { id: 'B', version: 1 };
  const drafts = { A: { status: createOrderDraft(order, 'CANCELLED') }, B: { payment: createOrderDraft(other, 'PAYID') } };
  const previous = [{ order, missing: false }, { order: other, missing: false }];
  const next = reconcileOrderQueue(previous, [{ ...other, version: 2 }, { ...order, version: 4 }], drafts);
  assert.deepEqual(next.map(e => e.order.id), ['B', 'A']);
  assert.equal(drafts.A.status.version, 3);
  assert.deepEqual(reconcileOrderQueue(previous, [], drafts), [{ order, missing: true }]);
  assert.deepEqual(reconcileOrderQueue(previous, [], {}), []);
  assert.equal(reconcileOrderQueue([{ order, missing: true }], [order], drafts)[0].missing, false);
});
