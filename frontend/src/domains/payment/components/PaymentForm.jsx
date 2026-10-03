import { useEffect, useRef, useState } from 'react';
import { paymentRequest } from '../api/paymentApi';
import { money } from '../../ordering/model/cartReducer';
import { paypalReturnMode,safeApprovalUrl } from '../model/paymentModel';
export default function PaymentForm({ order, receipt }) {
  const [payment, setPayment] = useState(null), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  const [returnMode]=useState(()=>paypalReturnMode(window.location));
  const running=useRef(false), live=useRef(false), initialized=useRef(null);
  const closed=['CANCELLED','COMPLETED'].includes(order.status);
  async function run(action) {
    if(running.current)return;
    running.current=true;setBusy(true);setError('');
    try {
      let result;
      if(action!=='check'&&!closed) {
        result=await paymentRequest(`/api/payments/${order.id}`,{receipt,body:{method:order.paymentMethod}});
        if(live.current)setPayment(result);
      }
      if(order.paymentMethod==='PAYPAL'&&(action==='check'||action==='recover')&&!result?.paid) {
        result=await paymentRequest(`/api/payments/${order.id}/check`,{receipt,body:{}});
      }
      if(live.current&&result)setPayment(result);
    } catch(e) {if(live.current)setError(e.message);}finally{running.current=false;if(live.current)setBusy(false);}
  }
  useEffect(()=>{
    live.current=true;
    const key=`${order.id}:${receipt.trackingToken}`;
    if(initialized.current!==key) {
      initialized.current=key;setPayment(null);
      if(order.paymentMethod!=='PAY_AT_RESTAURANT'&&!order.paidAt)run('recover');
    }
    return()=>{live.current=false;};
  },[order.id,receipt.trackingToken]);
  const approval=safeApprovalUrl(payment?.approvalUrl);
  if (order.paymentMethod === 'PAY_AT_RESTAURANT') return <p>{order.paidAt ? 'Payment recorded.' : order.status === 'CANCELLED' ? 'This order was cancelled.' : 'Pay at the restaurant when collecting.'}</p>;
  return <section aria-label="Order payment">
    <h3>{order.paymentMethod === 'PAYPAL' ? 'PayPal payment' : 'PayID bank transfer'}</h3>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {order.paidAt || payment?.paid ? <p role="status">Payment received: {money(order.totalMinor)}.</p> : <>
      {returnMode==='cancel'&&<p role="status">PayPal approval was cancelled. Your food order is saved and remains unpaid unless payment is verified.</p>}
      <p>Payment pending. Staff cannot accept this online-paid order until payment is verified.</p>
      {!closed&&<button type="button" disabled={busy} onClick={()=>run('recover')}>{busy?'Checking payment…':order.paymentMethod==='PAYPAL'?'Retry PayPal setup / check':'Refresh PayID instructions'}</button>}
      {approval&&!payment?.paid&&!closed&&<p><a className="button button-primary" href={approval}>Continue to PayPal</a></p>}
      {payment?.payid&&!closed&&<div><p>PayID: <strong>{payment.payid}</strong></p><p>Account name: {payment.accountName}</p><p>Amount: {money(payment.totalMinor)} AUD</p><p>Bank reference: {payment.reference}</p><p>Verify the recipient name shown in your banking app before transferring. Include this reference. Your order remains awaiting payment confirmation until staff checks the restaurant bank account.</p></div>}
      {order.paymentMethod==='PAYPAL'&&<button type="button" disabled={busy} onClick={()=>run('check')}>Confirm / check PayPal payment</button>}
    </>}
    {order.status === 'CANCELLED' && <p>If you paid, contact the restaurant to arrange a refund. Refunds are not automatic.</p>}
  </section>;
}
