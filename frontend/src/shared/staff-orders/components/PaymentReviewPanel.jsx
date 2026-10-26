import { useState } from 'react';
export default function PaymentReviewPanel({ authorization, request:paymentRequest, orderHref, onOrder }) {
  const [data, setData] = useState(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  async function load() {
    if (busy) return;
    setBusy(true); setError('');
    try { setData(await paymentRequest('/api/staff/payments/review', { authorization })); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  return <section aria-label="Payment and refund review">
    <button type="button" disabled={busy} onClick={load}>{busy ? 'Loading payment review…' : 'Review payment and refund obligations'}</button>
    {error && <p role="alert">{error}</p>}
    {data && <><p>Financial review includes older orders. Refunds must be arranged separately; this screen does not issue refunds. Refresh this list after resolving an issue.</p>
      {!data.orders.length && !data.webhookEvents.length && <p>No unresolved financial reviews.</p>}
      {data.orders.map(order => <p key={order.id}>{onOrder?<button onClick={()=>onOrder(order.id)}>{order.id}</button>:<a href={orderHref(order.id)}>{order.id}</a>} · {order.payment_method} · Refund: {order.refund_status} · {order.review_reason || (order.payment_review_required ? 'Payment review required' : 'Check payment status')}</p>)}
      {data.webhookEvents.map(event => <p key={event.event_id}>Provider event {event.event_id} · {event.event_type} · Manual reconciliation required</p>)}
      {(data.orders.length === 200 || data.webhookEvents.length === 100) && <p>Review limit reached. Contact the administrator for the complete financial review report.</p>}
    </>}
  </section>;
}
