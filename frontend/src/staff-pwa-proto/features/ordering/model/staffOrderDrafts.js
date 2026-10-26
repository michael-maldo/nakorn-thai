/** Drafts capture the version at the start of an operation, never at submission. */
export function createOrderDraft(order, operation) {
  return { version: order.version, operation, values: operation === 'PAYID'
    ? { bankReference: '', bankReceiptChecked: false }
    : { pickupMinutes: '20', reason: '', paymentCollected: false }, conflict: false };
}
export function orderDraftDirty(draft) {
  return !!draft && (draft.operation !== 'PAYID' || !!draft.values.bankReference || draft.values.bankReceiptChecked);
}
export function orderDraftConflict(draft, order) {
  return !!draft && (draft.conflict || draft.version !== order.version);
}
export function orderDraftCommand(draft) {
  const { version, operation, values } = draft;
  if (operation === 'PAYID') return { version, bankReference: values.bankReference.trim(), bankReceiptChecked: values.bankReceiptChecked };
  return { version, status: operation, ...(operation === 'ACCEPTED' ? { pickupMinutes: Number(values.pickupMinutes) } : {}),
    ...(operation === 'CANCELLED' ? { reason: values.reason.trim() } : {}), paymentCollected: values.paymentCollected };
}
export function reconcileOrderQueue(previous, orders, drafts) {
  const ids = new Set(orders.map(order => order.id));
  return [...orders.map(order => ({ order, missing: false })), ...previous.filter(({ order }) => !ids.has(order.id)
    && Object.values(drafts[order.id] || {}).some(orderDraftDirty)).map(entry => ({ ...entry, missing: true }))];
}
