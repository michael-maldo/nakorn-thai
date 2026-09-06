import { useEffect, useState } from 'react';
import Header from '../../../website/components/Header';
import { paymentRequest } from '../../payment/api/paymentApi';
import { RECEIPT } from './CheckoutPage';
export default function OrderTrackingPage() {
  const [orderId,setOrderId]=useState(''),[channel,setChannel]=useState('sms'),[code,setCode]=useState(''),[challenge,setChallenge]=useState(null),[options,setOptions]=useState({}),[message,setMessage]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  useEffect(()=>{paymentRequest('/api/order-verification/options').then(data=>{setOptions(data);setChannel(data.sms?'sms':'email');}).catch(e=>setError(e.message));},[]);
  async function submit(event){event.preventDefault();setBusy(true);setError('');try{
    if(!challenge){const data=await paymentRequest('/api/order-verification/start',{body:{orderId,channel}});setChallenge(data.challengeId);setMessage(data.message);}
    else {const receipt=await paymentRequest('/api/order-verification/check',{body:{challengeId:challenge,code}});sessionStorage.setItem(RECEIPT,JSON.stringify(receipt));window.location.hash='/order-confirmation';}
  }catch(e){setError(e.message);}finally{setBusy(false);}}
  return <><Header /><main className="restaurant-menu page-width"><h1>Track your order</h1><p>Enter the full order ID from your receipt. We’ll send a verification code to the phone or email saved with that order.</p>
    {!options.sms&&!options.email?<p>Code verification is not configured. Use your original order receipt or contact the restaurant.</p>:<form className="order-panel" onSubmit={submit}><fieldset disabled={busy}>
      <label>Full order ID<input required value={orderId} pattern="[0-9a-fA-F-]{36}" disabled={!!challenge} onChange={e=>setOrderId(e.target.value)}/></label>
      <label>Send code by<select value={channel} disabled={!!challenge} onChange={e=>setChannel(e.target.value)}>{options.sms&&<option value="sms">SMS</option>}{options.email&&<option value="email">Email</option>}</select></label>
      {challenge&&<label>Verification code<input required inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{4,10}" value={code} onChange={e=>setCode(e.target.value)}/></label>}
      <button>{busy?'Please wait…':challenge?'Verify and track':'Send verification code'}</button>
      {challenge&&<button type="button" onClick={()=>{setChallenge(null);setCode('');setMessage('');}}>Request another code</button>}
    </fieldset></form>}{message&&<p role="status">{message}</p>}{error&&<p role="alert" className="staff-error">{error}</p>}</main></>;
}
