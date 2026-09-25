export const orderStates = {
  NEW: 'New', ACCEPTED: 'Accepted', PREPARING: 'Preparing', READY: 'Ready for pickup',
  COMPLETED: 'Completed', CANCELLED: 'Cancelled',
};
export function orderActions(role, status) {
  const front = ['ADMIN', 'FOH'].includes(role);
  const kitchen = ['ADMIN', 'BOH'].includes(role);
  const actions = [];
  if (front && status === 'NEW') actions.push('ACCEPTED');
  if (kitchen && status === 'ACCEPTED') actions.push('PREPARING');
  if (kitchen && status === 'PREPARING') actions.push('READY');
  if (front && status === 'READY') actions.push('COMPLETED');
  if (front && ['NEW', 'ACCEPTED', 'PREPARING', 'READY'].includes(status)) actions.push('CANCELLED');
  return actions;
}
export const needsPayment = order => !order.paidAt && order.paymentMethod !== 'PAY_AT_RESTAURANT';
