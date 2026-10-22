import { needsPayment } from '../../../domains/ordering/model/orderModel.js';
export const queues = [['NEW', 'Pending'], ['ACCEPTED', 'Accepted'], ['PREPARING', 'Preparing'], ['READY', 'Ready']];
export const orderTime = value => value && Number.isFinite(new Date(value).getTime()) ? new Date(value).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
export const money = minor => new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(minor / 100);
export function paymentText(order) {
  if (order.paymentReviewRequired || order.refundStatus && order.refundStatus !== 'NONE') return `Financial review required${order.refundStatus && order.refundStatus !== 'NONE' ? ` · Refund: ${order.refundStatus}` : ''}`;
  if (order.paidAt && !needsPayment(order)) return 'Paid · server verified';
  return order.paymentMethod === 'PAY_AT_RESTAURANT' ? 'Payment due at handover' : 'Payment pending · not verified';
}
