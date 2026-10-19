import CheckoutSummary from '../components/CheckoutSummary';
import PaymentForm from '../../payment/components/PaymentForm';
import { useEffect, useState } from 'react';
import { useCart } from '../model/CartContext';
import Header from '../../../website/components/Header';
import { getOrder } from '../api/orderApi';
import { money } from '../model/cartReducer';
import { PENDING_ORDER, RECEIPT } from './CheckoutPage';
import '../../../styles/checkout.css';
const labels = { NEW: 'Awaiting restaurant confirmation', ACCEPTED: 'Accepted by the restaurant', PREPARING: 'Your order is being prepared', READY: 'Ready for pickup', COMPLETED: 'Collected and paid', CANCELLED: 'Order cancelled' };
export default function OrderConfirmationPage() {
  const { dispatch } = useCart();
  const [receipt, setReceipt] = useState(null);
  const [order, setOrder] = useState(null); const [error, setError] = useState('');
  useEffect(() => {
    let stopped = false, timer;
    async function poll() {
      try {
        let receipt;try{receipt=JSON.parse(sessionStorage.getItem(PENDING_ORDER) || sessionStorage.getItem(RECEIPT) || 'null');}catch{}
        if (!receipt?.requestId||!receipt?.trackingToken) { if (!stopped) setError('No order receipt is available in this browser tab.'); return; }
        if(!stopped)setReceipt(receipt);
        const data = await getOrder(receipt);
        if (stopped) return;
        sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: receipt.requestId, trackingToken: receipt.trackingToken }));
        if (sessionStorage.getItem(PENDING_ORDER)) dispatch({ type: 'clear' });
        sessionStorage.removeItem(PENDING_ORDER); setReceipt(receipt); setOrder(data); setError('');
        if (!['COMPLETED', 'CANCELLED'].includes(data.status)) timer = setTimeout(poll, 5000);
      } catch (failure) {
        if (stopped) return;
        if(failure.status===404&&!sessionStorage.getItem(PENDING_ORDER))setOrder(null);
        setError(failure.status === 404 ? sessionStorage.getItem(PENDING_ORDER)?'No order has been found yet. Return to checkout to retry the saved submission.':'Tracking access expired or is invalid. Recover tracking with a new verification code.' : failure.message);
        if(failure.status!==404||sessionStorage.getItem(PENDING_ORDER))timer = setTimeout(poll, 5000);
      }
    }
    poll(); return () => { stopped = true; clearTimeout(timer); };
  }, [dispatch]);
  return <><Header currentPage="Menu" /><main className="restaurant-menu page-width customer-checkout customer-receipt">
    <header className="checkout-heading"><p className="checkout-eyebrow">Nakorn Thai · Pickup</p><h1>Your pickup order</h1><p>Your receipt and the latest updates, all in one place.</p></header>
    {error && <><p role="alert" className="staff-error">{error}</p><a href={`#/recover-order${receipt?.requestId?'?order='+encodeURIComponent(receipt.requestId):''}`}>Recover order tracking</a></>}
    {!order && !error && <p role="status">Checking your order…</p>}
    {order && <section className="order-panel receipt-panel">
      <div className="receipt-reference"><h2>Order {order.reference}</h2><span>{order.paymentMethod==='PAYPAL'?'PayPal':order.paymentMethod==='PAYID'?'PayID':'Pay at restaurant'}</span></div>
      {receipt && <PaymentForm key={order.id} order={order} receipt={receipt} />}
      <div className="receipt-grid"><section className="receipt-status" aria-label="Restaurant status"><p className="checkout-eyebrow">Restaurant status</p><h3 role="status">{labels[order.status]}</h3>
      {order.status === 'NEW' && <p>Wait for confirmation here. Pickup time has not been confirmed yet.</p>}
      {order.estimatedReadyAt && !['CANCELLED','COMPLETED'].includes(order.status) && <p>Estimated pickup: {new Date(order.estimatedReadyAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>}
      {order.cancellationReason && <p>{order.cancellationReason}</p>}
      <div className="checkout-pickup"><h3>Pickup at Nakorn Thai</h3><p>233 Glenferrie Rd<br />Malvern VIC 3144</p></div></section>
      <section aria-label="Order summary"><h3>Your order</h3>{order.items.map((line,index) => <div className="checkout-item" key={line.id ?? index}><CheckoutSummary line={line} historical compact /></div>)}
      <p className="checkout-total"><span>Total <small>AUD</small></span><strong>{money(order.totalMinor)}</strong></p></section></div>
      <details className="receipt-tracking"><summary>Keep your order reference &amp; tracking details</summary><p>Full order ID: <strong>{order.id}</strong></p><p>Keep this receipt for tracking. <a href={`#/recover-order?order=${encodeURIComponent(order.id)}`}>Recover tracking with an SMS or email verification code</a>.</p></details>
    </section>}
    <div className="checkout-links">{!order&&<a href="#/checkout">Return to checkout</a>}<a href="#/menu">Back to menu</a></div>
  </main></>;
}
