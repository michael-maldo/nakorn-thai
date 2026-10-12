import {useEffect,useId,useRef,useState} from 'react';
import {australianMobilePayload,australianMobileDisplay,validAustralianMobile} from '../utils/australianPhone.js';
export default function AustralianMobileInput({value,onChange,...props}) {
 const [draft,setDraft]=useState(()=>australianMobileDisplay(value)),[focused,setFocused]=useState(false),[touched,setTouched]=useState(false);
 const hint=useId(),input=useRef(null);
 useEffect(()=>{input.current?.setCustomValidity(value&&!validAustralianMobile(value)?'Enter an Australian mobile number, for example 0434 778 435.':'');},[value]);
 useEffect(()=>{if(!focused)setDraft(australianMobileDisplay(value));},[value,focused]);
 const invalid=touched&&Boolean(draft)&&!validAustralianMobile(draft);
 return <><input ref={input} {...props} type="tel" inputMode="tel" autoComplete="tel" maxLength={30} placeholder="+61 434 778 435" value={draft}
 aria-describedby={hint} aria-invalid={invalid||undefined}
 onFocus={()=>setFocused(true)} onBlur={()=>{setFocused(false);setTouched(true);setDraft(australianMobileDisplay(draft));}}
 onChange={e=>{const raw=e.target.value;setDraft(raw);e.target.setCustomValidity(raw&&!validAustralianMobile(raw)?'Enter an Australian mobile number, for example 0434 778 435.':'');onChange(australianMobilePayload(raw));}} />
 <small id={hint} role={invalid?'alert':undefined}>{invalid?'Enter an Australian mobile number, for example 0434 778 435.':'Australia (+61). Enter 04…, 4… or +61…. Spacing is applied when you leave this field.'}</small></>;
}
