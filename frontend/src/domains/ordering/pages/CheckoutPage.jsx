import AustralianMobileInput from '../../../shared/components/AustralianMobileInput.jsx';
import { observeOptions } from '../../restaurant/model/observeOptions.js';
import ContactVerification from '../../notification/components/ContactVerification.jsx';
import { emptyVerification,isVerified,changeDestination } from '../../notification/model/contactVerification.js';
import { startContactVerification,verifyContactCode } from '../../notification/api/contactVerificationApi.js';
import { beginPayPalCheckout, paymentRequest } from '../../payment/api/paymentApi';
import { resumableApproval } from '../../payment/model/paymentModel';
import { useEffect, useRef, useState } from 'react';
import '../../../styles/checkout.css';
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
  const lastCart=useRef(cart);if(cart.length)lastCart.current=cart;
  const submitting=useRef(false),mounted=useRef(false),paymentController=useRef(null);
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;paymentController.current?.abort();};},[]);
  const [pending, setPending] = useState(readPending);
  const [name, setName] = useState(pending?.customerName ?? ''); const [phone, setPhone] = useState(()=>emptyVerification(pending?.phone ?? '')); const [notes, setNotes] = useState(pending?.notes ?? '');
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [phoneRequired,setPhoneRequired]=useState(true);
  const [enabled, setEnabled] = useState(false);
  const [orderingMessage, setOrderingMessage] = useState('Online ordering is currently closed or unavailable.');
  const [email, setEmail] = useState(pending?.email ?? '');
  const [paymentMethod, setPaymentMethod] = useState(pending?.paymentMethod ?? 'PAY_AT_RESTAURANT');
  const [paymentOptionsError,setPaymentOptionsError]=useState(''),[orderingOptionsError,setOrderingOptionsError]=useState('');
  const [paymentOptions, setPaymentOptions] = useState({});
  const [now,setNow]=useState(Date.now());
  const summaryCart=busy&&!cart.length?lastCart.current:cart;
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
  useEffect(() => observeOptions(signal => paymentRequest('/api/payments/options',{signal}), options=>{setPaymentOptions(options);setPaymentOptionsError('');},
    e => { setPaymentOptions({}); setPaymentOptionsError(e.message); }), []);
  useEffect(() => {
    if (pending) return;
    setPaymentMethod(current => (current === 'PAYPAL' ? paymentOptions.paypal === true : current === 'PAYID' ? paymentOptions.payid === true : current === 'PAY_AT_RESTAURANT' && paymentOptions.payAtRestaurant === true)
      ? current : paymentOptions.payAtRestaurant === true ? 'PAY_AT_RESTAURANT' : paymentOptions.paypal === true ? 'PAYPAL' : paymentOptions.payid === true ? 'PAYID' : '');
  }, [paymentOptions, pending]);
  useEffect(() => observeOptions(getOrderingOptions, options => {
    setOrderingOptionsError('');setPhoneRequired(options.phoneRequired !== false); setEnabled(options.enabled === true);
    setOrderingMessage(options.message || 'Online ordering is currently closed or unavailable.');
  }, e => { setEnabled(false); setOrderingOptionsError(e.message); }), []);
  async function complete(payload) {
    sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: payload.requestId, trackingToken: payload.trackingToken }));
    sessionStorage.removeItem(PENDING_ORDER); dispatch({ type: 'clear' }); setPending(null);
    if(payload.paymentMethod==='PAYPAL'){
      const controller=new AbortController();paymentController.current=controller;
      try{
        const payment=await beginPayPalCheckout(payload,controller.signal);
        if(!mounted.current)return;
        const approval=resumableApproval(payment);
        if(approval){window.location.replace(approval);return;}
      }catch{if(!mounted.current)return;/* The saved receipt recovers uncertain outcomes through checks only. */}
    }
    if(mounted.current)window.location.hash = '/order-confirmation';
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
      const lines = await refreshCartPrices(cart);
      updateReviewedLines(lines);
      setError(lines.some((line) => line.issue) ? 'Review the marked cart items before ordering.' : 'Cart refreshed. Review the prices and options before submitting.');
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  async function place(event) {
    event.preventDefault(); if (submitting.current||busy) return; if(!pending&&(!enabled||!(paymentMethod==='PAYPAL'?paymentOptions.paypal===true:paymentMethod==='PAYID'?paymentOptions.payid===true:paymentMethod==='PAY_AT_RESTAURANT'&&paymentOptions.payAtRestaurant===true))){setError('Ordering or the selected payment method is currently unavailable. Please try again later.');return;} if(!pending&&phoneRequired&&!isVerified(phone)){setError('Verify your current mobile by SMS before placing an order.');return;} submitting.current=true;setBusy(true); setError('');
    let payload = pending;
    try {
      if (payload) {
        await resumeOrder(payload, cart);
      } else {
        const reviewed = await prepareCheckout(cart);
        if(!mounted.current)return;
        dispatch({ type: 'replace', lines: reviewed.lines });
        payload = { requestId: crypto.randomUUID(), trackingToken: Array.from(crypto.getRandomValues(new Uint8Array(32)), (n) => n.toString(16).padStart(2, '0')).join(''),
          customerName: name, phone:phone.destination, phoneVerificationId:isVerified(phone)?phone.id:null, notes, email: email || null, paymentMethod, items: reviewed.items };
        // Persist before submitting, so a lost response cannot duplicate the order.
        sessionStorage.setItem(PENDING_ORDER, JSON.stringify(payload)); setPending(payload);
        await submitOrder(payload);
      }
      if(mounted.current)await complete(payload);
    } catch (failure) {
      setError(failure.message); updateReviewedLines(failure.lines);
      if (failure.clearPending) forgetPending();
      else if ([400, 409, 503].includes(failure.status) && payload) {
        const recovery = await recoverCheckoutFailure(failure, payload, cart);
        if (recovery.order&&mounted.current) await complete(payload);
        else { if (recovery.clearPending) forgetPending(); updateReviewedLines(recovery.lines); setError(recovery.message); }
      }
    } finally { submitting.current=false;if(mounted.current)setBusy(false); }
  }
  return <><Header currentPage="Menu" /><main className="restaurant-menu page-width customer-checkout">
    <header className="checkout-heading"><p className="checkout-eyebrow">Freshly prepared · Pickup only</p><h1>Pickup checkout</h1><p>Good food, ready for you. Review your order and choose how to pay.</p></header>
    {paymentOptionsError&&<p role="alert" className="staff-error">{paymentOptionsError}</p>}
    {orderingOptionsError&&<p role="alert" className="staff-error">{orderingOptionsError}</p>}
    {error && <p role="alert" className="staff-error">{error}</p>}
    {!enabled && !pending && <p>{orderingMessage}</p>}
    {!cart.length && !pending && !busy ? <a href="#/menu">Choose your dishes</a> : <form className="checkout-layout" onSubmit={place} aria-busy={busy}>
      <aside className="order-panel checkout-summary" aria-label="Order summary"><h2>Your order</h2>
        {summaryCart.map((line) => <div className="checkout-item" key={line.key}><CheckoutSummary line={line} compact />
          {!pending&&<button className="checkout-text-button" type="button" disabled={busy} onClick={() => dispatch({ type: 'remove', id: line.key })}>Remove {line.dishName}</button>}</div>)}
        <p className="checkout-total"><span>Total <small>AUD</small></span><strong>{money(cartTotal(summaryCart))}</strong></p>
        {!pending&&<button className="checkout-text-button" type="button" disabled={busy} onClick={refresh}>Refresh cart prices and availability</button>}
        <div className="checkout-pickup"><h3>Pickup at Nakorn Thai</h3><p>233 Glenferrie Rd<br />Malvern VIC 3144</p><p>We’ll confirm your order and pickup time before preparation begins.</p></div>
      </aside><div className="order-panel checkout-details">
      {pending ? <><p>Your submission is saved in this browser tab. Retry safely using the same order details.</p><p>{pending.customerName} · {pending.phone}</p><a href="#/order-confirmation">Check whether this order was received</a></> : <>
        <h2>Your details</h2><p className="checkout-muted">So we can keep you updated about your pickup.</p>
        <label>Your name<input required maxLength={100} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} disabled={busy} /></label>
        {phoneRequired?<ContactVerification australianMobile channel="SMS" label="Mobile number (required)" required state={phone} onChange={setPhone} now={now} disabled={busy} startVerification={startContactVerification} verifyCode={verifyContactCode} />:<label>Mobile number<AustralianMobileInput name="phone" required value={phone.destination} disabled={busy} onChange={value=>setPhone(changeDestination(phone,value))}/></label>}
        <label>Email for order updates (optional)<input type="email" maxLength={254} autoComplete="email" value={email} onChange={e => setEmail(e.target.value)} disabled={busy} /></label>
        <p className="checkout-muted">We use your contact details for order updates and tracking recovery.</p>
        <label>Order notes (optional)<textarea maxLength={1000} value={notes} onChange={(e) => setNotes(e.target.value)} disabled={busy} placeholder="Anything we should know about your order?" /></label>
        <fieldset className="checkout-payment"><legend>How would you like to pay?</legend>
          {[['PAY_AT_RESTAURANT','Pay at restaurant','Pay when you collect your order.',paymentOptions.payAtRestaurant],['PAYPAL','PayPal','Approve your payment securely on PayPal’s website.',paymentOptions.paypal],['PAYID','PayID bank transfer','Transfer using the instructions on your receipt.',paymentOptions.payid]].filter(option=>option[3]===true).map(([value,label,description])=><label className="checkout-payment-choice" key={value}><input type="radio" name="paymentMethod" value={value} checked={paymentMethod===value} onChange={()=>setPaymentMethod(value)} disabled={busy} /><span><strong>{label}</strong><small>{description}</small></span></label>)}
        </fieldset>
      </>}
      <button className="button button-primary checkout-submit" disabled={busy || (!pending && (!enabled || !paymentMethod || (phoneRequired&&!isVerified(phone,now)) || cart.some((line) => line.issue)))}>{busy ? paymentMethod==='PAYPAL'?'Continuing to PayPal…':'Placing your order…' : pending ? 'Continue saved order' : paymentMethod==='PAYPAL'?'Continue to PayPal':'Place pickup order'}</button>
      <p className="checkout-muted checkout-submit-note">{paymentMethod==='PAYPAL'?'You’ll return here after PayPal approval. Payment is confirmed securely before the restaurant can accept your order.':'Your order needs restaurant confirmation before preparation begins.'}</p>
      </div>
    </form>}
    <a href="#/menu">Back to menu and cart</a>
  </main></>;
}
