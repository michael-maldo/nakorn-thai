import { paymentRequest } from '../../payment/api/paymentApi';
import { useEffect, useState } from 'react';
import Header from '../../../website/components/Header';
import { useCart } from '../model/CartContext';
import { cartTotal, money } from '../model/cartReducer';
import { getOrderingOptions, submitOrder } from '../api/orderApi';
import { prepareCheckout, resumeOrder, recoverCheckoutFailure, refreshCartPrices } from '../api/checkoutApi';
import CheckoutSummary from '../components/CheckoutSummary';

export const PENDING_ORDER = 'nakorn-pending-pickup';
export const RECEIPT = 'nakorn-pickup-receipt';
function readPending() {
  try { return JSON.parse(sessionStorage.getItem(PENDING_ORDER) || 'null'); } catch { return null; }
}
export default function CheckoutPage() {
  const { cart, dispatch } = useCart();
  const [pending, setPending] = useState(readPending);
  const [name, setName] = useState(pending?.customerName ?? ''); const [phone, setPhone] = useState(pending?.phone ?? ''); const [notes, setNotes] = useState(pending?.notes ?? '');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [email, setEmail] = useState(pending?.email ?? '');
  const [paymentMethod, setPaymentMethod] = useState(pending?.paymentMethod ?? 'PAY_AT_RESTAURANT');
  const [paymentOptions, setPaymentOptions] = useState({});
  useEffect(() => { paymentRequest('/api/payments/options').then(setPaymentOptions).catch(() => {}); }, []);
  useEffect(() => { let active = true; getOrderingOptions().then((options) => { if (active) setEnabled(options.enabled); }).catch((e) => { if (active) setError(e.message); }); return () => { active = false; }; }, []);
  function complete(payload) {
    sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: payload.requestId, trackingToken: payload.trackingToken }));
    sessionStorage.removeItem(PENDING_ORDER); dispatch({ type: 'clear' }); setPending(null); window.location.hash = '/order-confirmation';
  }
  function forgetPending() { sessionStorage.removeItem(PENDING_ORDER); setPending(null); }
  function updateReviewedLines(lines) {
    if (!lines) return;
    // Failed saved offers may only have request fields; retain the existing display snapshot.
    const merged = lines.map((line) => {
      const previous = cart.find((entry) => entry.key === line.key);
      return line.issue && previous ? { ...previous, issue: line.issue } : line;
    });
    if (merged.every((line) => typeof line.basePriceMinor === 'number' && line.collectionName))
      dispatch({ type: 'replace', lines: merged });
  }
  async function refresh() {
    setBusy(true); setError('');
    try {
      const [lines, options] = await Promise.all([refreshCartPrices(cart), getOrderingOptions()]);
      updateReviewedLines(lines); setEnabled(options.enabled === true);
      setError(lines.some((line) => line.issue) ? 'Review the marked cart items before ordering.' : 'Cart refreshed. Review the prices and options before submitting.');
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  async function place(event) {
    event.preventDefault(); if (busy) return; setBusy(true); setError('');
    let payload = pending;
    try {
      if (payload) {
        await resumeOrder(payload, cart);
      } else {
        const reviewed = await prepareCheckout(cart);
        dispatch({ type: 'replace', lines: reviewed.lines });
        payload = { requestId: crypto.randomUUID(), trackingToken: Array.from(crypto.getRandomValues(new Uint8Array(32)), (n) => n.toString(16).padStart(2, '0')).join(''),
          customerName: name, phone, notes, email: email || null, paymentMethod, items: reviewed.items };
        // Persist before submitting, so a lost response cannot duplicate the order.
        sessionStorage.setItem(PENDING_ORDER, JSON.stringify(payload)); setPending(payload);
        await submitOrder(payload);
      }
      complete(payload);
    } catch (failure) {
      setError(failure.message); updateReviewedLines(failure.lines);
      if (failure.clearPending) forgetPending();
      else if ([400, 409, 503].includes(failure.status) && payload) {
        const recovery = await recoverCheckoutFailure(failure, payload, cart);
        if (recovery.order) complete(payload);
        else { if (recovery.clearPending) forgetPending(); updateReviewedLines(recovery.lines); setError(recovery.message); }
      }
    } finally { setBusy(false); }
  }
  return <><Header currentPage="Menu" /><main className="restaurant-menu page-width">
    <h1>Pickup checkout</h1><p>Choose how to pay below. Your order needs staff confirmation before preparation begins.</p>
    <p>Pickup: 233 Glenferrie Rd, Malvern VIC 3144.</p>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {!enabled && !pending && <p>Online ordering is currently closed or unavailable.</p>}
    {!cart.length && !pending ? <a href="#/menu">Choose your dishes</a> : <form className="order-panel" onSubmit={place}>
      {pending ? <><p>Your submission is saved in this browser tab. Retry safely using the same order details.</p><p>{pending.customerName} · {pending.phone}</p><a href="#/order-confirmation">Check whether this order was received</a></> : <>
        <button type="button" disabled={busy} onClick={refresh}>Refresh cart prices and availability</button>
        {cart.map((line) => <div key={line.key}><CheckoutSummary line={line} />
          <button type="button" disabled={busy} onClick={() => dispatch({ type: 'remove', id: line.key })}>Remove {line.dishName}</button></div>)}
        <p className="order-total">Total: {money(cartTotal(cart))}</p>
        <label>Your name<input required maxLength={100} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} disabled={busy} /></label>
        <label>Phone number<input required type="tel" maxLength={30} minLength={6} autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={busy} /></label>
        <label>Email for order verification (optional)<input type="email" maxLength={254} autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} disabled={busy} /></label>
        <p>Keep the full order ID on your receipt. Where enabled, you can request an SMS or email code to recover tracking access.</p>
        <label>Payment method<select value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)} disabled={busy}>
          <option value="PAY_AT_RESTAURANT">Pay at restaurant</option>
          {paymentOptions.paypal && <option value="PAYPAL">PayPal</option>}
          {paymentOptions.payid && <option value="PAYID">PayID bank transfer</option>}
        </select></label>
        <label>Order notes<textarea maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} disabled={busy} /></label>
      </>}
      <button className="button button-primary" disabled={busy || (!pending && (!enabled || cart.some((line) => line.issue)))}>{busy ? 'Submitting…' : pending ? 'Retry submission' : 'Place pickup order'}</button>
    </form>}
    <a href="#/menu">Back to menu and cart</a>
  </main></>;
}
