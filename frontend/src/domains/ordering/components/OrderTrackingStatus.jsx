import { useId } from 'react';
import { orderStates } from '../model/orderModel';

const progress = ['NEW', 'ACCEPTED', 'PREPARING', 'READY', 'COMPLETED'];
const labels = {
  ...orderStates,
  NEW: 'Awaiting restaurant confirmation',
  ACCEPTED: 'Accepted by the restaurant',
  PREPARING: 'Your order is being prepared',
  COMPLETED: 'Order collected',
  CANCELLED: 'Order cancelled',
};
const refunds = {
  COMPLETED: 'Your refund has been recorded as complete.',
  REQUIRED: 'A refund is required. Contact the restaurant for an update.',
  PENDING: 'Your refund is pending.',
};

function formatUpdate(value, timezone) {
  const date = new Date(value);
  if (!value || Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat('en-AU', { dateStyle: 'medium', timeStyle: 'short', timeZone: timezone }).format(date);
}

export default function OrderTrackingStatus({ order }) {
  const titleId = useId();
  const step = progress.indexOf(order.status);
  const cancelled = order.status === 'CANCELLED';
  const refund = order.refundStatus && order.refundStatus !== 'NONE';
  const estimated = step >= 0 && step < 3 ? formatUpdate(order.estimatedReadyAt, order.timezone) : '';
  const updated = formatUpdate(order.updatedAt, order.timezone);
  const placed = !updated && formatUpdate(order.createdAt, order.timezone);
  return <section className="tracking-card" aria-labelledby={titleId}>
    <h2 id={titleId}>Order #{order.reference}</h2>
    <p className="tracking-current" role="status">{labels[order.status] ?? 'Status unavailable'}</p>
    {step >= 0 && <ol className="tracking-steps" aria-label="Order progress">
      {progress.map((status, index) => <li key={status} aria-current={index === step ? 'step' : undefined}
        data-reached={index <= step}>
        <span aria-hidden="true">{index < step ? '✓' : index + 1}</span>{orderStates[status]}
      </li>)}
    </ol>}
    {cancelled && <p>Your order has been cancelled. Contact Nakorn Thai if you need assistance.</p>}
    {order.status === 'NEW' && <p>Wait for the restaurant to confirm your order before coming to collect it.</p>}
    {order.status === 'READY' && <p>Your order is ready for pickup at Nakorn Thai, 233 Glenferrie Rd, Malvern.</p>}
    {estimated && <p>Estimated pickup: {estimated}{!order.timezone && ' (your local time)'}</p>}
    {updated && <p>Last updated: {updated}</p>}
    {placed && <p>Order placed: {placed}{!order.timezone && ' (your local time)'}</p>}
    {refund && <p>{refunds[order.refundStatus] ?? 'Contact the restaurant for a refund update.'}</p>}
    {order.paymentReviewRequired && <p>The restaurant is reviewing your payment. Contact Nakorn Thai if you need help.</p>}
  </section>;
}
