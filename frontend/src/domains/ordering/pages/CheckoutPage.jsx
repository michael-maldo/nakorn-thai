import { useEffect, useState } from 'react';
import Header from '../../../website/components/Header';
import { useCart } from '../model/CartContext';
import { cartTotal, money } from '../model/cartReducer';
import { getOrderingOptions, getOrder, submitOrder } from '../api/orderApi';
import { refreshCartPrices } from '../api/checkoutApi';

export const PENDING_ORDER = 'nakorn-pending-pickup';
export const RECEIPT = 'nakorn-pickup-receipt';
function readPending() {
  try { return JSON.parse(sessionStorage.getItem(PENDING_ORDER) || 'null'); } catch { return null; }
}
export default function CheckoutPage() {
  const { cart, dispatch } = useCart();
  const [pending, setPending] = useState(readPending);
  const [name, setName] = useState(''); const [phone, setPhone] = useState(''); const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [enabled, setEnabled] = useState(false);
  useEffect(() => { let active = true; getOrderingOptions().then((options) => { if (active) setEnabled(options.enabled); }).catch((e) => { if (active) setError(e.message); }); return () => { active = false; }; }, []);
  async function place(event) {
    event.preventDefault(); setBusy(true); setError('');
    let payload = pending;
    try {
      if (!payload) {
        const updated = await refreshCartPrices(cart);
        if (updated.some((line, index) => line.unitPriceMinor !== cart[index].unitPriceMinor)) {
          dispatch({ type: 'replace', lines: updated }); throw new Error('Prices changed. Review the updated total and submit again.');
        }
        payload = { requestId: crypto.randomUUID(), trackingToken: Array.from(crypto.getRandomValues(new Uint8Array(32)), (n) => n.toString(16).padStart(2, '0')).join(''), customerName: name, phone, notes,
          items: cart.map((line) => ({ variationId: line.variationId, quantity: line.quantity, expectedUnitPriceMinor: line.unitPriceMinor })) };
        // Keep the exact attempt across reloads, so a lost response cannot duplicate the order.
        sessionStorage.setItem(PENDING_ORDER, JSON.stringify(payload)); setPending(payload);
      }
      await submitOrder(payload);
      sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: payload.requestId, trackingToken: payload.trackingToken }));
      sessionStorage.removeItem(PENDING_ORDER); dispatch({ type: 'clear' }); window.location.hash = '/order-confirmation';
    } catch (failure) {
      setError(failure.message);
      // Validation/availability failures occur before a new order is committed.
      // Keep conflict attempts recoverable: an existing order may share this key.
      if (failure.status === 409 && payload) {
        try {
          await getOrder(payload);
          sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: payload.requestId, trackingToken: payload.trackingToken }));
          sessionStorage.removeItem(PENDING_ORDER); dispatch({ type: 'clear' }); window.location.hash = '/order-confirmation';
        } catch (lookup) {
          if (lookup.status === 404) { sessionStorage.removeItem(PENDING_ORDER); setPending(null); }
        }
      }
      if (failure.status === 400 || failure.status === 503) { sessionStorage.removeItem(PENDING_ORDER); setPending(null); }
    } finally { setBusy(false); }
  }
  return <><Header currentPage="Menu" /><main className="restaurant-menu page-width">
    <h1>Pickup checkout</h1><p>Pay at the restaurant. Your order needs staff confirmation before preparation begins.</p>
    <p>Pickup: 233 Glenferrie Rd, Malvern VIC 3144.</p>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {!enabled && !pending && <p>Online ordering is currently closed or unavailable.</p>}
    {!cart.length && !pending ? <a href="#/menu">Choose your dishes</a> : <form className="order-panel" onSubmit={place}>
      {pending ? <><p>Your submission is saved in this browser tab. Retry safely using the same order details.</p><p>{pending.customerName} · {pending.phone}</p><a href="#/order-confirmation">Check whether this order was received</a></> : <>
        {cart.map((line) => <p key={line.variationId}>{line.quantity} × {line.dishName} ({line.variationName}) — {money(line.quantity * line.unitPriceMinor)}</p>)}
        <p className="order-total">Total: {money(cartTotal(cart))}</p>
        <label>Your name<input required maxLength={100} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} disabled={busy} /></label>
        <label>Phone number<input required type="tel" maxLength={30} minLength={6} autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={busy} /></label>
        <label>Order notes<textarea maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} disabled={busy} /></label>
      </>}
      <button className="button button-primary" disabled={busy || (!enabled && !pending)}>{busy ? 'Submitting…' : pending ? 'Retry submission' : 'Place pickup order — pay at restaurant'}</button>
    </form>}
    <a href="#/menu">Back to menu and cart</a>
  </main></>;
}
