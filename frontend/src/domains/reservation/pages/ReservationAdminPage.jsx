import { StaffPageHeading, StaffNotice, StaffLoading, StaffEmptyState } from '../../staff/components/StaffPresentation';
import { getRestaurantAvailability } from '../../restaurant/api/restaurantApi';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { reservationRequest } from '../api/reservationApi';
const today=(timeZone)=>new Intl.DateTimeFormat('en-CA',{timeZone,year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const transitions={REQUESTED:['CONFIRMED','DECLINED','CANCELLED'],CONFIRMED:['SEATED','NO_SHOW','CANCELLED']};
export default function ReservationAdminPage() {
 const {user,authorization}=useAuth();
 if(!authorization||!user||user.enabled===false||!['ADMIN','FOH'].includes(user.role))return null;
 // A principal/role change remounts the workspace before protected rows can render.
 // Missing stable identity fails closed for retention: every new token gets a fresh workspace.
 const principal=typeof user.id==='string'&&user.id ? `${user.id}:${user.role}` : `${authorization}:${user.role}`;
 return <ReservationWorkspace key={principal} />;
}
function ReservationWorkspace() {
 const {authorization}=useAuth();
 const [date,setDate]=useState(''),[timezone,setTimezone]=useState(''),[rows,setRows]=useState([]),[notes,setNotes]=useState({});
 const [availability,setAvailability]=useState({status:'loading',error:''});
 const [bookings,setBookings]=useState({status:'idle',error:''});
 const [writing,setWriting]=useState(false),[writeError,setWriteError]=useState(''),[writeMessage,setWriteMessage]=useState('');
 // Request generations invalidate late results, including StrictMode cleanup and date changes.
 const mounted=useRef(false),availabilityRequest=useRef(0),availabilityPending=useRef(false),bookingRequest=useRef(0),bookingPending=useRef(false),writePending=useRef(false);
 const currentDate=useRef(date);currentDate.current=date;
 const currentAuthorization=useRef(authorization);currentAuthorization.current=authorization;
 useEffect(()=>{
  mounted.current=true;
  initializeAvailability();
  return()=>{mounted.current=false;availabilityRequest.current++;bookingRequest.current++;availabilityPending.current=false;bookingPending.current=false;};
 },[]);
 async function initializeAvailability(){
  // A ref closes the same-frame gap before React renders the loading state.
  if(availabilityPending.current)return;
  availabilityPending.current=true;
  const request=++availabilityRequest.current;
  setAvailability({status:'loading',error:''});
  try{
   const data=await getRestaurantAvailability();
   const initialDate=today(data.timezone);
   if(!mounted.current||request!==availabilityRequest.current)return;
   setTimezone(data.timezone);
   setDate(selected=>selected||initialDate);
   setAvailability({status:'ready',error:''});
  }catch(e){if(mounted.current&&request===availabilityRequest.current)setAvailability({status:'error',error:e.message});}
  finally{if(request===availabilityRequest.current)availabilityPending.current=false;}
 }
 async function load(selectedDate=date,{force=false}={}){
  if(!selectedDate||availability.status!=='ready'||(bookingPending.current&&!force))return;
  const requestAuthorization=currentAuthorization.current;
  const request=++bookingRequest.current;
  bookingPending.current=true;
  setBookings({status:'loading',error:''});
  try{
   const data=await reservationRequest(`/staff/reservations?date=${selectedDate}`,{authorization:requestAuthorization});
   if(!mounted.current||request!==bookingRequest.current||selectedDate!==currentDate.current||requestAuthorization!==currentAuthorization.current)return;
   setRows(data);setBookings({status:'ready',error:''});
  }catch(e){if(mounted.current&&request===bookingRequest.current&&selectedDate===currentDate.current&&requestAuthorization===currentAuthorization.current)setBookings({status:'error',error:e.message});}
  finally{if(request===bookingRequest.current)bookingPending.current=false;}
 }
 useEffect(()=>{
  if(availability.status!=='ready'||!date)return;
  // Date edits clear rows in the input handler; same-principal token rotation retains them.
  load(date,{force:true});
  return()=>{bookingRequest.current++;bookingPending.current=false;};
 },[date,authorization,availability.status]);
 async function update(row,status){
  if(writePending.current)return;
  writePending.current=true;setWriting(true);setWriteError('');setWriteMessage('');
  const selectedDate=date;
  try{
   await reservationRequest(`/staff/reservations/${row.id}`,{method:'PATCH',authorization,body:{version:row.version,status,staffNote:notes[row.id]??row.staffNote}});
   if(mounted.current){setWriteMessage('Booking update saved.');if(selectedDate===currentDate.current)await load(selectedDate,{force:true});}
  }catch(e){if(mounted.current)setWriteError(e.message);}
  finally{writePending.current=false;if(mounted.current)setWriting(false);}
 }
 const busy=writing;
 return <main className="staff-menu page-width"><StaffPageHeading title="Reservations" /><p>Check table availability before confirming. When enabled, booking updates are queued to a verified mobile and a valid email independently. Queued messages may be delayed or fail; contact the guest directly when needed.</p><div className="staff-toolbar"><label>Restaurant date ({timezone || (availability.status==='error'?'unavailable':'loading…')})<input type="date" value={date} required disabled={writing || availability.status!=='ready'} onChange={e=>{if(e.target.value && e.target.value!==date){currentDate.current=e.target.value;bookingRequest.current++;setRows([]);setWriteMessage('');setWriteError('');setBookings({status:'idle',error:''});setDate(e.target.value);}}} /></label><button disabled={writing || availability.status!=='ready' || bookings.status==='loading' || !date} aria-busy={bookings.status==='loading' || undefined} onClick={()=>load()}>Refresh bookings</button></div>
 {availability.status==='loading'&&<StaffLoading>Loading reservation availability…</StaffLoading>}
 {availability.status==='error'&&<StaffNotice>Reservation availability could not be loaded. {availability.error} <button onClick={initializeAvailability}>Retry availability</button></StaffNotice>}
 {bookings.status==='error'&&<StaffNotice>Booking requests could not be loaded. {bookings.error} {rows.length>0&&'Displayed bookings are from an earlier load and may be out of date. '}Use Refresh bookings to try again.</StaffNotice>}
 {writeMessage&&<StaffNotice variant="info">{writeMessage}</StaffNotice>}
 {writeError&&<StaffNotice>{writeError}</StaffNotice>}
 {writing&&<StaffLoading>Updating booking…</StaffLoading>}
 {bookings.status==='loading'&&<StaffLoading>{rows.length>0?'Refreshing bookings… Displayed bookings are from an earlier load.':'Loading bookings…'}</StaffLoading>}
 {availability.status==='ready'&&bookings.status==='ready'&&!rows.length&&<StaffEmptyState title="No booking requests for this date." />}
 <div className="order-queue">{rows.map(row=><article className="order-panel" key={row.id}><h2>{row.requestedAt.slice(11,16)} · {row.customerName}</h2><p>{row.partySize} guests · {row.status.replaceAll('_',' ')}</p>{row.phone&&<p>Phone: {row.phone} · {row.phoneVerified?'Verified':'Unverified'}</p>}{row.email&&<p>Email for booking updates: {row.email}</p>}{row.notes&&<p>Guest notes: {row.notes}</p>}<small>Reference: {row.id}</small>{transitions[row.status]?<><label>Staff note<textarea disabled={busy} maxLength={500} value={notes[row.id]??row.staffNote} onChange={e=>setNotes({...notes,[row.id]:e.target.value})}/></label><div className="staff-toolbar">{transitions[row.status].map(status=><button disabled={busy} key={status} onClick={()=>update(row,status)}>{status.replaceAll('_',' ')}</button>)}</div></>:<p>{row.staffNote}</p>}{row.updatedBy&&<small>Last updated by {row.updatedBy}</small>}</article>)}</div></main>;
}
