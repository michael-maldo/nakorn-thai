export const orderStates = {
  NEW: 'New', ACCEPTED: 'Accepted', PREPARING: 'Preparing', READY: 'Ready for pickup',
  COMPLETED: 'Completed', CANCELLED: 'Cancelled',
};
export function orderActions(role, status) {
  const staff = ['ADMIN', 'FOH', 'BOH'].includes(role);
  const actions = [];
  if (staff && status === 'NEW') actions.push('ACCEPTED');
  if (staff && status === 'ACCEPTED') actions.push('PREPARING');
  if (staff && status === 'PREPARING') actions.push('READY');
  if (staff && status === 'READY') actions.push('COMPLETED');
  if (role === 'ADMIN' && ['NEW', 'ACCEPTED', 'PREPARING', 'READY'].includes(status)) actions.push('CANCELLED');
  return actions;
}
export const needsPayment = order => order.paymentMethod !== 'PAY_AT_RESTAURANT' && (!order.paidAt || order.paymentReviewRequired || !!order.refundStatus && order.refundStatus !== 'NONE');
