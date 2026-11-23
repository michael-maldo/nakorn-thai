import { useCallback, useEffect, useRef, useState } from 'react';
import { observeOptions } from '../../restaurant/model/observeOptions';
import { paymentRequest } from '../../payment/api/paymentApi';
import { getOrder } from '../api/orderApi';
import OrderTrackingStatus from '../components/OrderTrackingStatus';
import TrackingLayout from '../components/TrackingLayout';
import useTrackedOrder from '../hooks/useTrackedOrder';
import { normalizeOrderId } from '../model/orderLinks';
import { PENDING_ORDER, RECEIPT } from './CheckoutPage';

const verificationPath = '/api/order-verification';
const fallback = 'Use the private tracking link in your confirmation email, or contact Nakorn Thai with your order reference.';

function savedReceipt(id) {
  try {
    const receipt = JSON.parse(sessionStorage.getItem(RECEIPT));
    return normalizeOrderId(receipt?.requestId) === id && /^[a-f0-9]{64}$/.test(receipt?.trackingToken) ? receipt : null;
  } catch { return null; }
}

export default function OrderTrackingPage({ linked = {} }) {
  const [orderId, setOrderId] = useState(linked.orderId || '');
  const [channel, setChannel] = useState('sms');
  const [code, setCode] = useState('');
  const [challenge, setChallenge] = useState(null);
  const [options, setOptions] = useState(null);
  const [optionsFailed, setOptionsFailed] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [skippedReceipt, setSkippedReceipt] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(linked.invalid ? 'This order link is invalid. Enter the full order ID from your confirmation.' : '');
  const [invalidInput, setInvalidInput] = useState(false);
  const pending = useRef(null);
  const restoreFocus = useRef(false);
  const orderInput = useRef(null);
  const codeInput = useRef(null);
  const activeChannel = options?.[channel] === true ? channel : options?.sms === true ? 'sms' : options?.email === true ? 'email' : '';

  useEffect(() => {
    function updateOptions(data) {
      if (pending.current && data?.[pending.current.channel] !== true) {
        pending.current.controller.abort();
        pending.current = null;
        setBusy(false);
      }
      setOptions(data);
      setOptionsFailed(!data);
    }
    return observeOptions(
      (signal) => paymentRequest(`${verificationPath}/options`, { signal }),
      updateOptions,
      () => updateOptions(null),
    );
  }, []);
  useEffect(() => {
    if (challenge && challenge.channel !== activeChannel) {
      setChallenge(null);
      setCode('');
      setError('');
      setInvalidInput(false);
    }
  }, [activeChannel, challenge]);
  useEffect(() => () => pending.current?.controller.abort(), []);
  useEffect(() => { if (challenge) codeInput.current?.focus(); }, [challenge]);
  useEffect(() => {
    if (!receipt && restoreFocus.current) {
      restoreFocus.current = false;
      orderInput.current?.focus();
    }
  }, [receipt, challenge]);

  function resetChallenge() {
    setChallenge(null);
    setCode('');
    setError('');
    setInvalidInput(false);
  }

  function invalid(message, input) {
    setInvalidInput(true);
    setError(message);
    input.current?.focus();
  }

  async function submit(event) {
    event.preventDefault();
    if (pending.current) return;
    setError('');
    setInvalidInput(false);
    const id = normalizeOrderId(orderId);
    if (!id) {
      invalid(orderId.trim() ? 'Enter the full order ID from your confirmation, including the hyphens.' : 'Enter your order ID.', orderInput);
      return;
    }
    if (!challenge && id !== skippedReceipt) {
      const saved = savedReceipt(id);
      if (saved) { setReceipt(saved); return; }
    }
    if (!activeChannel) {
      setError(options === null && !optionsFailed ? 'Verification is still loading. Please try again shortly.' : 'No verification method is available for this request.');
      return;
    }
    if (challenge && !/^[0-9]{4,10}$/.test(code.trim())) {
      invalid('Enter the 4–10 digit verification code.', codeInput);
      return;
    }

    const controller = new AbortController();
    const request = { channel: activeChannel, controller };
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(20000)]);
    pending.current = request;
    setBusy(true);
    try {
      if (!challenge) {
        const data = await paymentRequest(`${verificationPath}/start`, { body: { orderId: id, channel: activeChannel }, signal });
        signal.throwIfAborted();
        if (!data?.challengeId) throw new Error('Invalid verification response.');
        setChallenge({ id: data.challengeId, channel: activeChannel });
      } else {
        const data = await paymentRequest(`${verificationPath}/check`, { body: { challengeId: challenge.id, code: code.trim() }, signal });
        signal.throwIfAborted();
        if (normalizeOrderId(data?.requestId) !== id || !/^[a-f0-9]{64}$/.test(data?.trackingToken)) throw new Error('Invalid tracking response.');
        setReceipt(data);
        resetChallenge();
      }
    } catch (failure) {
      if (!controller.signal.aborted) {
        setError(failure.status === 429 ? 'Please wait before trying again.'
          : challenge && failure.status === 400 ? 'Code expired or invalid. Try again or request another code.'
            : 'Verification is temporarily unavailable. Please try again.');
      }
    } finally {
      if (pending.current === request) {
        pending.current = null;
        if (!controller.signal.aborted) setBusy(false);
      }
    }
  }

  if (receipt) return <TrackingLayout><VerifiedOrder receipt={receipt} onBack={() => {
    restoreFocus.current = true;
    setSkippedReceipt(normalizeOrderId(receipt.requestId));
    setReceipt(null);
    resetChallenge();
  }} /></TrackingLayout>;

  return <TrackingLayout entry>
    <form className="tracking-form" onSubmit={submit} noValidate>
      <label htmlFor="tracking-order-id">Order number</label>
      <input ref={orderInput} id="tracking-order-id" name="orderId" type="text" required
        value={orderId} placeholder="Enter your order number" autoComplete="off" autoCapitalize="none" spellCheck={false}
        readOnly={busy || !!challenge} aria-invalid={invalidInput && !challenge ? true : undefined}
        aria-describedby={invalidInput && !challenge ? 'tracking-id-help tracking-form-error' : 'tracking-id-help'}
        onChange={(event) => { setOrderId(event.target.value); setInvalidInput(false); setError(''); }} />
      <p className="tracking-help" id="tracking-id-help">Find the full order ID on your confirmation under “Keep your order reference &amp; tracking details”. The short reference is for restaurant support.</p>
      {activeChannel && <>
        <label htmlFor="tracking-channel">Send code by</label>
        <select id="tracking-channel" value={activeChannel} disabled={busy || !!challenge} onChange={(event) => setChannel(event.target.value)}>
          {options.sms === true && <option value="sms">SMS</option>}
          {options.email === true && <option value="email">Email</option>}
        </select>
      </>}
      {challenge && <>
        <p role="status">If that order has a contact for this channel, a verification code has been sent.</p>
        <label htmlFor="tracking-code">Verification code</label>
        <input ref={codeInput} id="tracking-code" value={code} onChange={(event) => {
          setCode(event.target.value); setInvalidInput(false); setError('');
        }} inputMode="numeric" autoComplete="one-time-code" required readOnly={busy}
          aria-invalid={invalidInput || undefined} aria-describedby={invalidInput ? 'tracking-form-error' : undefined} />
      </>}
      {error && <p className="tracking-error" id="tracking-form-error" role="alert">{error}</p>}
      <button className="button button-primary" type="submit" disabled={busy}>{busy ? (challenge ? 'Checking code…' : 'Sending code…') : challenge ? 'Verify and track' : 'Track order'}</button>
      {challenge && <button className="button button-outline" type="button" disabled={busy} onClick={() => {
        restoreFocus.current = true;
        resetChallenge();
      }}>Request another code</button>}
      <p className="tracking-help" role="status">{optionsFailed ? 'Verification availability could not be checked.'
        : !options ? 'Checking verification availability…'
          : activeChannel ? 'Additional verification may be required. Codes go to the contact saved with the order.'
            : 'Code verification is currently unavailable. You can still track an order with its saved receipt in this tab.'}</p>
      {!activeChannel && (options || optionsFailed) && <p className="tracking-help">{fallback}</p>}
    </form>
  </TrackingLayout>;
}

function VerifiedOrder({ receipt, onBack }) {
  const heading = useRef(null);
  const load = useCallback(async (signal) => {
    const order = await getOrder(receipt, signal);
    if (normalizeOrderId(order?.id) !== normalizeOrderId(receipt.requestId) || !order.reference) {
      const error = new Error('Invalid order response.');
      error.status = 404;
      throw error;
    }
    return order;
  }, [receipt]);
  const { order, error, expired, retry } = useTrackedOrder(load);
  const [receiptError, setReceiptError] = useState('');
  const hasOrder = !!order;
  useEffect(() => { if (hasOrder) heading.current?.focus(); }, [hasOrder]);

  function openReceipt() {
    try {
      if (sessionStorage.getItem(PENDING_ORDER)) {
        setReceiptError('Finish or review your saved submission at checkout before opening another receipt.');
        return;
      }
      sessionStorage.setItem(RECEIPT, JSON.stringify({ requestId: receipt.requestId, trackingToken: receipt.trackingToken }));
      window.location.hash = '/order-confirmation';
    } catch { setReceiptError('The full receipt could not be opened in this tab.'); }
  }

  return <>
    {expired && <p className="tracking-error" role="alert">Tracking access expired or is invalid. Recover tracking with a new verification code. {fallback}</p>}
    {error && <div className="tracking-card"><p role="alert">{error}</p><button className="button button-outline" type="button" onClick={retry}>Try again</button></div>}
    {!order && !error && !expired && <p role="status">Loading your order…</p>}
    {order && <section ref={heading} tabIndex={-1} className="tracking-result" aria-label="Order tracking result">
      <OrderTrackingStatus order={order} />
      {order.notes && <section className="tracking-card"><h2>Your instructions</h2><p className="tracking-instructions">{order.notes}</p></section>}
      <button type="button" className="button button-outline" onClick={openReceipt}>Open full receipt</button>
      {receiptError && <p className="tracking-error" role="alert">{receiptError}</p>}
    </section>}
    <button className="button button-outline" type="button" onClick={onBack}>{expired ? 'Recover order tracking' : 'Back to tracking form'}</button>
  </>;
}
