import { useRef } from 'react';
import { changeDestination,isVerified } from '../model/contactVerification.js';
export default function ContactVerification({channel,state,onChange,now,startVerification,verifyCode,label,required=false,disabled=false}) {
 const generation=useRef(0);
 const busy=state.status==='sending'||state.status==='verifying';
 const verified=isVerified(state,now);
 const cooldown=Math.max(0,Math.ceil((Date.parse(state.resendAt)-now)/1000))||0;
 async function send() {
  const current=++generation.current;
  onChange({...state,status:'sending',error:''});
  try { const data=await startVerification(channel,state.destination);if(current===generation.current)onChange({...state,...data,status:'codeSent',code:'',error:''}); }
  catch(e) {if(current===generation.current)onChange({...state,status:'error',error:e.message,resendAt:new Date(Date.now()+60000).toISOString()});}
 }
 async function check() {
  const current=++generation.current;onChange({...state,status:'verifying',error:''});
  try {const data=await verifyCode(state.id,state.code);if(current===generation.current)onChange({...state,...data,status:'verified',error:''});}
  catch(e) {if(current===generation.current)onChange({...state,status:'codeSent',error:e.message});}
 }
 return <section aria-label={`${channel} verification`}>
 <label>{label||(channel==='SMS'?'Phone number (optional)':'Email (optional)')}<input required={required} disabled={disabled||busy} type={channel==='SMS'?'tel':'email'} name={channel==='SMS'?'phone':'email'} autoComplete={channel==='SMS'?'tel':'email'} maxLength={channel==='SMS'?30:254} value={state.destination} onChange={e=>{generation.current++;onChange(changeDestination(state,e.target.value));}} /></label>
 <button type="button" className="button" disabled={disabled||busy||!state.destination||cooldown>0||verified} onClick={send}>{state.status==='sending'?'Sending code…':cooldown>0?`Resend in ${cooldown}s`:'Send verification code'}</button>
 {state.id&&!verified&&<><p role="status">Code sent. Codes expire after 10 minutes.</p><label>Verification code<input inputMode="numeric" autoComplete="one-time-code" maxLength={10} value={state.code} onChange={e=>onChange({...state,code:e.target.value})} /></label><button type="button" className="button" disabled={disabled||busy||!/^\d{4,10}$/.test(state.code)} onClick={check}>{state.status==='verifying'?'Verifying…':'Verify code'}</button></>}
 {verified&&<p role="status">Verified</p>}
 {state.status==='verified'&&!verified&&<p role="alert">Verification expired. Request a new code.</p>}
 {state.error&&<p role="alert" className="staff-error">{state.error}</p>}
 </section>;
}
