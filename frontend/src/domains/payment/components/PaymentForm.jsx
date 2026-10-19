import { observeOptions } from '../../restaurant/model/observeOptions.js';
import { useEffect, useRef, useState } from 'react';
import { beginPayPalCheckout, paymentRequest } from '../api/paymentApi';
import { money } from '../../ordering/model/cartReducer';
import { PAYPAL_CHECK_DELAYS, paypalReturnMode, paypalView, resumableApproval } from '../model/paymentModel';

export default function PaymentForm({ order, receipt }) {
  const [payment,setPayment]=useState(null),[busy,setBusy]=useState(false),[finished,setFinished]=useState(false),[revision,setRevision]=useState(0);
  const [error,setError]=useState(''),[optionsError,setOptionsError]=useState(''),[options,setOptions]=useState({});
  const action=useRef(false),mounted=useRef(false),actionController=useRef(null);
  const [returnMode]=useState(()=>paypalReturnMode(window.location));
  const closed=['CANCELLED','COMPLETED'].includes(order.status);
  const unpaid=!order.paidAt&&!closed;
  useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;actionController.current?.abort();};},[]);
  useEffect(()=>{
    // Receipt polling may discover a later webhook/refund outcome than our last check.
    setPayment(current=>current?{...current,paid:!!order.paidAt,paymentReviewRequired:order.paymentReviewRequired,refundStatus:order.refundStatus}:current);
  },[order.paidAt,order.paymentReviewRequired,order.refundStatus]);
  useEffect(()=>{
    if(order.paymentMethod==='PAY_AT_RESTAURANT'||!unpaid)return;
    return observeOptions(signal=>paymentRequest('/api/payments/options',{signal}),data=>{setOptions(data);setOptionsError('');},e=>{setOptions({});setOptionsError(e.message);});
  },[order.paymentMethod,unpaid]);
  useEffect(()=>{
    if(order.paymentMethod==='PAY_AT_RESTAURANT'||!unpaid){setBusy(false);setFinished(true);return;}
    const controller=new AbortController();let timer,stopped=false,attempt=0;
    async function check(){
      setBusy(true);setError('');
      try{
        const result=await paymentRequest(`/api/payments/${order.id}${order.paymentMethod==='PAYPAL'?'/check':''}`,{receipt,body:order.paymentMethod==='PAYPAL'?{}:{method:'PAYID'},signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])});
        if(stopped)return;setPayment(result);
        if(order.paymentMethod!=='PAYPAL'||result.paid||result.paymentReviewRequired||result.refundStatus&&result.refundStatus!=='NONE'||resumableApproval(result)||result.canStartPayment===true){setFinished(true);return;}
      }catch(e){if(stopped)return;setError(e.status===404?'Tracking access is unavailable. Recover your order tracking below.':'We could not reach the payment service. Your order is saved.');if([401,403,404].includes(e.status)){setFinished(true);return;}}
      finally{if(!stopped)setBusy(false);}
      attempt++;
      if(attempt<PAYPAL_CHECK_DELAYS.length)timer=setTimeout(check,PAYPAL_CHECK_DELAYS[attempt]);
      else setFinished(true);
    }
    setFinished(false);check();
    return()=>{stopped=true;clearTimeout(timer);controller.abort();};
  },[order.id,receipt.trackingToken,order.paymentMethod,unpaid,revision]);
  async function continuePayment(){
    if(action.current||busy||options.paypal!==true)return;
    action.current=true;setBusy(true);setError('');
    const controller=new AbortController();actionController.current=controller;
    try{
      // Recheck continuation permission; never navigate using a stale saved approval URL.
      const result=payment?.canStartPayment===true
        ?await beginPayPalCheckout(receipt,controller.signal)
        :await paymentRequest(`/api/payments/${order.id}/check`,{receipt,body:{},signal:AbortSignal.any([controller.signal,AbortSignal.timeout(15000)])});
      if(!mounted.current)return;setPayment(result);
      const approval=resumableApproval(result);
      if(approval){window.location.replace(approval);return;}
      setFinished(true);
    }catch(e){if(mounted.current&&!controller.signal.aborted){setPayment(null);setFinished(true);setError('We could not continue to PayPal. Check your payment status before trying again.');}}
    finally{action.current=false;if(mounted.current)setBusy(false);}
  }
  if(order.paymentMethod==='PAY_AT_RESTAURANT')return <section className="payment-status" aria-label="Order payment">
    <h3>{order.paidAt?'Payment recorded':closed?'Order closed':'Payment due at pickup'}</h3>
    <p>{order.paidAt?'Your payment has been recorded.':closed?'Contact the restaurant if you have a payment question.':'Pay at the restaurant when collecting.'}</p>
    {order.refundStatus&&order.refundStatus!=='NONE'&&<p>Refund status: {order.refundStatus.replaceAll('_',' ')}. Contact the restaurant for help.</p>}
  </section>;
  if(order.paymentMethod==='PAYID')return <section className="payment-status" aria-label="Order payment"><h3>PayID bank transfer</h3>
    {order.paidAt?<p role="status">Payment received: {money(order.totalMinor)}.</p>:<>
      {error&&<p role="alert">{error}</p>}
      {optionsError&&<p role="alert">{optionsError}</p>}
      {order.paymentReviewRequired&&<p>Contact the restaurant before making another transfer.</p>}
      {options.payid&&payment?.payid&&!closed&&!order.paymentReviewRequired&&<div><p>PayID: <strong>{payment.payid}</strong></p><p>Account name: {payment.accountName}</p><p>Amount: {money(payment.totalMinor)} AUD</p><p>Bank reference: {payment.reference}</p><p>Verify the recipient name shown in your banking app before transferring. Include this reference. Your order remains awaiting payment confirmation until staff checks the restaurant bank account.</p></div>}
      {!closed&&<button type="button" disabled={busy||!options.payid} onClick={()=>setRevision(n=>n+1)}>Refresh PayID instructions</button>}
    </>}
    {order.refundStatus&&order.refundStatus!=='NONE'&&<p>Refund status: {order.refundStatus.replaceAll('_',' ')}. Contact the restaurant for help.</p>}
  </section>;
  const view=paypalView(order,options.paypal===true?payment:{...payment,canResumeApproval:false,canStartPayment:false},{busy,finished});
  const headings={paid:'Payment successful',checking:'Confirming your payment…',approval:'Payment not completed',delayed:"We're still checking your payment",review:'Your payment needs a review',refund:'Payment update',closed:'Order closed'};
  return <section className={`payment-status payment-status-${view}`} aria-label="Order payment" aria-busy={busy}>
    <p className="checkout-eyebrow">PayPal</p><h3 role="status">{headings[view]}</h3>
    {view==='paid'&&<p>{order.status==='NEW'?'Your payment has been confirmed. Your order is awaiting restaurant confirmation.':'Your payment has been confirmed. See your current pickup status below.'}</p>}
    {view==='checking'&&<p>We’re confirming the result securely. Please don’t pay again.</p>}
    {view==='approval'&&<><p>{returnMode==='cancel'?'You cancelled PayPal approval. Your order is saved. You can continue when you’re ready.':'Your order is saved. Continue to PayPal to approve your payment.'}</p><button type="button" className="button button-primary" disabled={busy} onClick={continuePayment}>Continue to PayPal</button></>}
    {view==='delayed'&&<p>Your order is saved. Please don’t pay again while we check. You can check the status here or contact the restaurant with your order reference.</p>}
    {view==='review'&&<p>Please contact the restaurant with your order reference. Don’t make another payment while we review this one.</p>}
    {view==='refund'&&<p>Refund status: {(payment?.refundStatus&&payment.refundStatus!=='NONE'?payment.refundStatus:order.refundStatus).replaceAll('_',' ')}. Your payment history is preserved. Contact the restaurant for help.</p>}
    {view==='closed'&&<p>This order is closed. If you paid, contact the restaurant for help. Refunds are not automatic.</p>}
    {error&&<p role="alert">{error}</p>}
    {optionsError&&<p role="alert">{optionsError}</p>}
    {!closed&&!order.paidAt&&options.paypal===false&&<p>This payment method is currently unavailable. You can still check an existing payment.</p>}
    {['delayed','review'].includes(view)&&unpaid&&<button type="button" className="button button-outline" disabled={busy} onClick={()=>setRevision(n=>n+1)}>Check payment status</button>}
  </section>;
}
