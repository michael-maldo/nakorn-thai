import { useEffect, useState } from 'react';
import { paymentRequest } from '../api/paymentApi';
import { money } from '../../ordering/model/cartReducer';
export default function PaymentForm({ order, receipt }) {
  const [payment, setPayment] = useState(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  async function run(action) {
    setBusy(true); setError('');
    try {
      const result = await paymentRequest(`/api/payments/${order.id}${action === 'check' ? '/check' : ''}`, { receipt, body: action === 'check' ? {} : { method: order.paymentMethod } });
      setPayment(result);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  useEffect(() => { setPayment(null); }, [order.id]);
  if (order.paymentMethod === 'PAY_AT_RESTAURANT') return <p>{order.paidAt ? 'Payment recorded.' : order.status === 'CANCELLED' ? 'This order was cancelled.' : 'Pay at the restaurant when collecting.'}</p>;
  return <section aria-label="Order payment">
    <h3>{order.paymentMethod === 'PAYPAL' ? 'PayPal payment' : 'PayID bank transfer'}</h3>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {order.paidAt || payment?.paid ? <p role="status">Payment received: {money(order.totalMinor)}.</p> : <>
      <p>Awaiting payment. Staff will confirm the order after payment is verified.</p>
      {!['CANCELLED', 'COMPLETED'].includes(order.status) && <button disabled={busy} onClick={() => run('start')}>{busy ? 'Please wait…' : order.paymentMethod === 'PAYPAL' ? 'Set up PayPal payment' : 'Show PayID details'}</button>}
      {payment?.approvalUrl && !payment.paid && !['CANCELLED','COMPLETED'].includes(order.status) && <p><a className="button button-primary" href={payment.approvalUrl}>Continue to PayPal</a></p>}
      {payment?.payid && <div><p>PayID: <strong>{payment.payid}</strong></p><p>Account name: {payment.accountName}</p><p>Amount: {money(payment.totalMinor)}</p><p>Bank reference: {payment.reference}</p><p>Check the bank-displayed recipient name before transferring. Staff must verify receipt; pressing a button does not confirm payment.</p></div>}
      {order.paymentMethod === 'PAYPAL' && <button disabled={busy} onClick={() => run('check')}>Confirm / check PayPal payment</button>}
    </>}
    {order.status === 'CANCELLED' && <p>If you paid, contact the restaurant to arrange a refund. Refunds are not automatic.</p>}
  </section>;
}
