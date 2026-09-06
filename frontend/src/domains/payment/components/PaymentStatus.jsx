import { useState } from 'react';
import { paymentRequest } from '../api/paymentApi';
export default function PaymentStatus({ order, authorization }) {
  const [reference, setReference] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  async function check(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const bank = order.paymentMethod === 'PAYID';
      const result = await paymentRequest(`/api/staff/payments/${order.id}/${bank ? 'payid-confirm' : 'check'}`, { authorization, body: bank ? { version: order.version, bankReference: reference } : {} });
      setNotice(result.paid ? 'Payment verified. The order queue will refresh shortly.' : 'Payment is still pending.');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  if (!order.paymentMethod || order.paymentMethod === 'PAY_AT_RESTAURANT') return null;
  return <section><p>Method: {order.paymentMethod} · {order.paidAt ? 'Paid' : 'Awaiting verification'}</p>
    {!order.paidAt && <form onSubmit={check}><fieldset disabled={busy}>
      {order.paymentMethod === 'PAYID' && <><label>Bank transaction reference<input required maxLength={150} value={reference} onChange={e => setReference(e.target.value)} /></label><label><input type="checkbox" required />I checked the bank account and received the exact order amount.</label></>}
      <button>{busy ? 'Checking…' : order.paymentMethod === 'PAYID' ? 'Confirm bank receipt' : 'Check PayPal receipt'}</button>
    </fieldset></form>}
    {order.status === 'CANCELLED' && order.paidAt && <p>Refund requires manual processing in PayPal or the bank. Cancellation does not issue a refund.</p>}
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  </section>;
}
