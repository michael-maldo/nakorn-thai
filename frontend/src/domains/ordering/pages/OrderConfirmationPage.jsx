import CheckoutSummary from '../components/CheckoutSummary';
import PaymentForm from '../../payment/components/PaymentForm';
import { useEffect, useState } from 'react';
import { useCart } from '../model/CartContext';
import Header from '../../../website/components/Header';
import { getOrder } from '../api/orderApi';
import { money } from '../model/cartReducer';
import { PENDING_ORDER, RECEIPT } from './CheckoutPage';
const labels = { NEW: 'Awaiting restaurant confirmation', ACCEPTED: 'Accepted by the restaurant', PREPARING: 'Your order is being prepared', READY: 'Ready for pickup', COMPLETED: 'Collected and paid', CANCELLED: 'Order cancelled' };
export default function OrderConfirmationPage() {
  const { dispatch } = useCart();
  const [receipt, setReceipt] = useState(null);
  const [order, setOrder] = useState(null); const [error, setError] = useState('');
  useEffect(() => {
    let stopped = false, timer;
    async function poll() {
      try {
        const receipt = JSON.parse(sessionStorage.getItem(PENDING_ORDER) || sessionStorage.getItem(RECEIPT) || 'null');
        if (!receipt) { if (!stopped) setError('No order receipt is available in this browser tab.'); return; }
        const data = await getOrder(receipt);
        if (stopped) return;
        sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: receipt.requestId, trackingToken: receipt.trackingToken }));
        if (sessionStorage.getItem(PENDING_ORDER)) dispatch({ type: 'clear' });
        sessionStorage.removeItem(PENDING_ORDER); setReceipt(receipt); setOrder(data); setError('');
        if (!['COMPLETED', 'CANCELLED'].includes(data.status)) timer = setTimeout(poll, 5000);
      } catch (failure) {
        if (stopped) return;
        setError(failure.status === 404 ? 'No order has been found yet. Return to checkout to retry the saved submission.' : failure.message);
        timer = setTimeout(poll, 5000);
      }
    }
    poll(); return () => { stopped = true; clearTimeout(timer); };
  }, [dispatch]);
  return <><Header currentPage="Menu" /><main className="restaurant-menu page-width">
    <h1>Your pickup order</h1>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {!order && !error && <p role="status">Checking your order…</p>}
    {order && <section className="order-panel">
      <h2>Order {order.reference}</h2><p role="status"><strong>{labels[order.status]}</strong></p>
      {order.status === 'NEW' && <p>Wait for confirmation here. Pickup time has not been confirmed yet.</p>}
      {order.estimatedReadyAt && !['CANCELLED','COMPLETED'].includes(order.status) && <p>Estimated pickup: {new Date(order.estimatedReadyAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>}
      {order.cancellationReason && <p>{order.cancellationReason}</p>}
      {order.items.map((line,index) => <CheckoutSummary key={line.id ?? index} line={line} historical />)}
      <p>Total: <strong>{money(order.totalMinor)}</strong></p>
      {receipt && <PaymentForm order={order} receipt={receipt} />}
      <p>Pickup: 233 Glenferrie Rd, Malvern VIC 3144.</p>
      <p>Full order ID: <strong>{order.id}</strong></p><p>Keep this receipt for tracking. <a href="#/track-order">Recover tracking with an SMS or email verification code</a>.</p>
    </section>}
    <div className="staff-toolbar"><a href="#/checkout">Return to checkout</a><a href="#/menu">Back to menu</a></div>
  </main></>;
}
