import { getRestaurantAvailability } from '../../restaurant/api/restaurantApi';
import { useEffect, useRef, useState } from 'react';
import Header from '../../../website/components/Header';
import { reservationRequest } from '../api/reservationApi';
import ContactVerification from '../components/ContactVerification.jsx';
import { emptyVerification,canSubmit,verificationIds } from '../model/contactVerification.js';
export default function ReservationPage() {
 const [phone,setPhone]=useState(emptyVerification),[email,setEmail]=useState(''),[now,setNow]=useState(Date.now());
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[receipt,setReceipt]=useState(null);
 const attempt=useRef(null);
 const [timezone,setTimezone]=useState(null);
 useEffect(()=>{let active=true;getRestaurantAvailability().then(data=>{if(active)setTimezone(data.timezone);}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[]);
 async function submit(e) {
  e.preventDefault();if(!canSubmit(phone)) {setError('Verify your current mobile by SMS.');return;}const form=new FormData(e.currentTarget);
  const data={customerName:form.get('name'),phone:phone.destination||null,email:email||null,...verificationIds(phone),partySize:Number(form.get('party')),requestedAt:form.get('time'),notes:form.get('notes')};
  // Renewing an OTP after a lost response must not create a second booking.
  const {phoneVerificationId,...details}=data;
  const signature=JSON.stringify(details);
  if(attempt.current?.signature!==signature)attempt.current={signature,id:crypto.randomUUID()};
  setBusy(true);setError('');
  try{setReceipt(await reservationRequest('',{method:'POST',body:{...data,requestId:attempt.current.id}}));}catch(e){setError(e.message);}finally{setBusy(false);}
 }
 return <><Header currentPage="Reservations" /><main className="staff-menu reservation-page page-width"><h1>Book a table</h1>
 <p>Request a table at Nakorn Thai. Verify your current mobile by SMS. Our team will review your request before confirming availability. All times use {timezone || 'the restaurant timezone (loading…)'}.</p>
 {receipt?<section className="staff-panel" role="status"><h2>Request received</h2><p>{receipt.message}</p><p>Reference: {receipt.reference}</p><a href="#home">Back to the restaurant</a></section>:
 <form className="staff-panel" onSubmit={submit}>{error&&<p role="alert" className="staff-error">{error}</p>}<fieldset disabled={busy || !timezone}>
 <label>Your name<input name="name" required maxLength={100} autoComplete="name" /></label>
 <ContactVerification channel="SMS" label="Mobile number (required)" required state={phone} onChange={setPhone} now={now} disabled={busy} />
 <label>Email for booking updates (optional)<input name="email" type="email" maxLength={254} autoComplete="email" value={email} onChange={e=>setEmail(e.target.value)} /></label>
 <label>Guests<input name="party" type="number" min={1} max={20} defaultValue={2} required /></label>
 <label>Requested date and time<input name="time" type="datetime-local" step={60} required /></label>
 <p>Please choose a future time within 90 days. This is a request, subject to opening hours and table availability.</p>
 <label>Special requests (optional)<textarea name="notes" maxLength={1000} /></label>
 <p>We use your contact details to arrange this booking. For parties larger than 20, please contact the restaurant.</p>
 <button className="button button-primary" disabled={busy || !canSubmit(phone,now)}>{busy?'Sending…':'Request booking'}</button>
 </fieldset></form>}</main></>;
}
