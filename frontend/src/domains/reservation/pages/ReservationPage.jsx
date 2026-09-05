import { useRef, useState } from 'react';
import Header from '../../../website/components/Header';
import { reservationRequest } from '../api/reservationApi';
export default function ReservationPage() {
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[receipt,setReceipt]=useState(null);
 const attempt=useRef(null);
 async function submit(e) {
  e.preventDefault();const form=new FormData(e.currentTarget);
  const data={customerName:form.get('name'),phone:form.get('phone'),partySize:Number(form.get('party')),requestedAt:form.get('time'),notes:form.get('notes')};
  const signature=JSON.stringify(data);
  if(attempt.current?.signature!==signature)attempt.current={signature,id:crypto.randomUUID()};
  setBusy(true);setError('');
  try{setReceipt(await reservationRequest('',{method:'POST',body:{...data,requestId:attempt.current.id}}));}catch(e){setError(e.message);}finally{setBusy(false);}
 }
 return <><Header currentPage="Reservations" /><main className="staff-menu reservation-page page-width"><h1>Book a table</h1>
 <p>Request a table at Nakorn Thai. Our team will contact you by phone to confirm availability. All times are Melbourne time.</p>
 {receipt?<section className="staff-panel" role="status"><h2>Request received</h2><p>{receipt.message}</p><p>Reference: {receipt.reference}</p><a href="#home">Back to the restaurant</a></section>:
 <form className="staff-panel" onSubmit={submit}>{error&&<p role="alert" className="staff-error">{error}</p>}<fieldset disabled={busy}>
 <label>Your name<input name="name" required maxLength={100} autoComplete="name" /></label>
 <label>Phone number<input name="phone" type="tel" required pattern="[+0-9 ()\-]{6,30}" maxLength={30} autoComplete="tel" /></label>
 <label>Guests<input name="party" type="number" min={1} max={20} defaultValue={2} required /></label>
 <label>Requested date and time<input name="time" type="datetime-local" step={60} required /></label>
 <p>Please choose a future time within 90 days. This is a request, subject to opening hours and table availability.</p>
 <label>Special requests (optional)<textarea name="notes" maxLength={1000} /></label>
 <p>We use your contact details to arrange this booking. For parties larger than 20, please contact the restaurant.</p>
 <button className="button button-primary" disabled={busy}>{busy?'Sending…':'Request booking'}</button>
 </fieldset></form>}</main></>;
}
