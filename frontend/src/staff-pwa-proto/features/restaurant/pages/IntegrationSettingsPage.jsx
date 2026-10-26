import PrinterSettings from '../../../../shared/staff-orders/printing/PrinterSettings';
import PageAction from '../../../components/PageAction';
import { StaffPageHeading, StaffUnsavedIndicator } from '../../staff/components/StaffPresentation';
import {useEffect,useRef,useState} from 'react';
import {restaurantRequest} from '../api/restaurantApi';
import {definitions,booleanFields,dependencyWarnings,replacementBody} from '../model/integrationSettings';
import { useMenuNavigationGuard } from '../../menu/hooks/useMenuAdminForm';
import { savedDraft, settingsDraftDirty } from '../model/settingsDraft';
const businessSettingGroups=[
 ['Business identity',['businessWebsiteUrl']],
 ['Online ordering',['orderingEnabled']],
 ['Reservations',['reservationsEnabled']],
 ['Customer verification',['orderPhoneRequired','reservationPhoneRequired']],
 ['Order notifications',['orderSms','orderEmail','staffOrderEmail','staffOrderEmailAddress']],
 ['Reservation notifications',['reservationSms','reservationEmail','staffReservationEmail','staffReservationEmailAddress']],
 ['Payment methods',['paypalEnabled','payidEnabled','payAtRestaurantEnabled']]
];
export default function IntegrationSettingsPage({orders}){
 const [printingOpen,setPrintingOpen]=useState(false);
 const [configuration,setConfiguration]=useState(null),[category,setCategory]=useState('SETTINGS'),[fields,setFields]=useState({}),[secrets,setSecrets]=useState({}),[replace,setReplace]=useState({}),[clear,setClear]=useState([]),[audit,setAudit]=useState([]),[recipient,setRecipient]=useState('');
 const [busy,setBusy]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(''),[auditError,setAuditError]=useState(''),[activity,setActivity]=useState('refresh');
 const request=useRef(null), drafts=useRef({}), dialog=useRef(null), previousFocus=useRef(null), pendingConfirmation=useRef(null);
 const [confirmation,setConfirmation]=useState(null);
 if(configuration)drafts.current[category]={fields,secrets,replace,clear};
 const sectionDirty=!!configuration&&settingsDraftDirty({fields,secrets,clear},configuration[category]);
 const dirty=!!configuration&&Object.entries(drafts.current).some(([key,draft])=>settingsDraftDirty(draft,configuration[key]));
 function askDiscard(action,cancel){if(request.current)return;pendingConfirmation.current?.cancel?.();if(!dialog.current?.open)previousFocus.current=document.activeElement;pendingConfirmation.current={action,cancel};setConfirmation(()=>action);}
 const guard=useMenuNavigationGuard(dirty,busy,resume=>askDiscard(()=>{if(!resume.active())return;drafts.current={};applyDraft(savedDraft(configuration[category]));guard.current.dirty=false;resume();},resume.cancel));
 useEffect(()=>{if(confirmation)dialog.current?.showModal();},[confirmation]);
 function closeConfirmation(discard=false){const pending=pendingConfirmation.current;pendingConfirmation.current=null;if(!discard)pending?.cancel?.();dialog.current?.close();setConfirmation(null);previousFocus.current?.focus();if(discard)pending?.action();}
 useEffect(()=>()=>{pendingConfirmation.current?.cancel?.();pendingConfirmation.current=null;},[]);
 function applyDraft(draft){setFields(draft.fields);setSecrets(draft.secrets);setReplace(draft.replace);setClear(draft.clear);}
 function discardSection(){const draft=savedDraft(configuration[category]);drafts.current[category]=draft;applyDraft(draft);setError('');setNotice('Changes discarded. Last saved values restored.');}
 // Preserve existing security handling after every completed write/test attempt.
 function clearSecrets(){setSecrets({});}

 function begin(action){if(request.current)return null;const ticket={};request.current=ticket;guard.current.busy=true;setBusy(true);setActivity(action);setError('');setNotice('');setAuditError('');return ticket;}
 function current(ticket){return request.current===ticket;}
 function finish(ticket){if(current(ticket)){request.current=null;guard.current.busy=false;setBusy(false);setActivity('');}}
 async function loadAudit(ticket){try{const data=await restaurantRequest('/configuration/audit');if(current(ticket))setAudit(data);}catch{if(current(ticket))setAuditError('Configuration audit history is unavailable. The configuration result above is unchanged.');}}
 // Server operations can also change other sections (for example checkout availability).
 // Refresh clean cached sections; keep genuinely edited sections intact.
 function confirmedConfiguration(data){
  if(configuration)for(const [key,draft] of Object.entries(drafts.current)){
   if(key!==category&&!settingsDraftDirty(draft,configuration[key]))drafts.current[key]=savedDraft(data[key]);
  }
  setConfiguration(data);
 }
 function loaded(data,key=category){confirmedConfiguration(data);const draft=savedDraft(data[key]);drafts.current[key]=draft;applyDraft(draft);}
 async function refresh(approved=false){if(dirty&&!approved){askDiscard(()=>refresh(true));return;}const ticket=begin('refresh');if(!ticket)return;try{const data=await restaurantRequest('/configuration');if(!current(ticket))return;drafts.current={};loaded(data);await loadAudit(ticket);}catch(e){if(current(ticket))setError(e.message);}finally{finish(ticket);}}
 useEffect(()=>{refresh();return()=>{request.current=null;};},[]);
 function choose(key){if(request.current)return;setCategory(key);applyDraft(drafts.current[key]||savedDraft(configuration[key]));setError('');setNotice('');}
 async function save(e){e.preventDefault();if(request.current)return;let acknowledge=false;
  if(category==='PAYPAL'&&fields.environment==='live'&&configuration.PAYPAL.fields.environment!=='live'&&!window.confirm('Switch to live PayPal? Checkout will remain disabled until live credentials are tested and explicitly enabled.'))return;
  if(category==='PAYID'&&configuration.PAYID.pendingPayments>0){acknowledge=window.confirm('Unpaid PayID orders may have previous payment instructions. Coordinate with staff before changing PayID. Continue?');if(!acknowledge)return;}
  if(category==='SETTINGS'&&configuration.SETTINGS.fields.payAtRestaurantEnabled==='true'&&fields.payAtRestaurantEnabled==='false'&&!window.confirm('Disable pay at restaurant? Online ordering must retain another configured payment method.'))return;
  if(category==='SETTINGS'&&((configuration.SETTINGS.fields.orderPhoneRequired==='true'&&fields.orderPhoneRequired==='false')||(configuration.SETTINGS.fields.reservationPhoneRequired==='true'&&fields.reservationPhoneRequired==='false'))&&!window.confirm('Disable mandatory mobile verification? SMS updates for that flow must also be disabled.'))return;
  if(clear.length&&!window.confirm('Clear the selected credentials? Required features must be disabled first.'))return;
  const key=category,ticket=begin('save');if(!ticket)return;
  try{
   const data=await restaurantRequest('/configuration/'+key,{method:'PUT',body:replacementBody(configuration[key].version,fields,secrets,clear,acknowledge)});
   if(!current(ticket))return;
   loaded(data,key);setNotice(key==='SETTINGS'?'Business settings saved.':definitions[key].label+' configuration saved. Configured does not mean tested or enabled.');
   await loadAudit(ticket);
  }catch(e){if(current(ticket)){
   setNotice('');setError(e.message+' Your non-sensitive edits are retained.'+(definitions[key].secrets?' Write-only replacements were cleared; re-enter them before retrying.':'')+' Correct the values and retry, or refresh to load current saved values.');
  }}
  finally{if(current(ticket))clearSecrets();finish(ticket);}
 }
 async function test(sendEmail=false){
  if(request.current||sectionDirty)return;
  if(sendEmail&&!window.confirm(`Send one explicit test email to ${recipient}?`))return;
  const key=category,ticket=begin('test');if(!ticket)return;
  try{
   const result=await restaurantRequest('/configuration/'+key+(sendEmail?'/test-email':'/test'),{method:'POST',body:{version:configuration[key].version,...(sendEmail?{recipient}:{})}});
   if(!current(ticket))return;
   confirmedConfiguration(result.configuration);setNotice(result.status+': '+result.message);await loadAudit(ticket);
  }catch(e){if(current(ticket))setError(e.message);}
  finally{if(current(ticket))clearSecrets();finish(ticket);}
 }
 const row=configuration?.[category],definition=definitions[category];
 const warnings=configuration&&category==='SETTINGS'?dependencyWarnings(fields,configuration):[];
 function renderField(key,label){return <label key={key}>{booleanFields.has(key)?<span className="configuration-checkbox"><input name={key} type="checkbox" checked={fields[key]==='true'} onChange={e=>setFields({...fields,[key]:String(e.target.checked)})}/><span>{label}</span></span>:<>{label}{key==='environment'?<select name={key} value={fields[key]} onChange={e=>setFields({...fields,[key]:e.target.value})}><option value="sandbox">Sandbox</option><option value="live">Live</option></select>:<input name={key} type={key==='businessWebsiteUrl'?'url':['staffOrderEmailAddress','staffReservationEmailAddress'].includes(key)?'email':key==='port'?'number':'text'} required={(key==='staffOrderEmailAddress'&&fields.staffOrderEmail==='true')||(key==='staffReservationEmailAddress'&&fields.staffReservationEmail==='true')} min={key==='port'?1:undefined} max={key==='port'?65535:undefined} maxLength={500} value={fields[key]??''} onChange={e=>setFields({...fields,[key]:e.target.value})}/>}</>}{key==='businessWebsiteUrl'&&<small>Use your trusted application website, such as https://nakorn.com.au. Leave blank to use PUBLIC_APP_URL. HTTPS is required in production.</small>}<small>Source: {row.sources[key]==='DASHBOARD'?'Dashboard managed':'Environment/default supplied'}</small></label>;}
 return <main className="staff-menu page-width"><StaffPageHeading title="Settings and integrations" actions={<PageAction><button disabled={busy} aria-busy={busy || undefined} onClick={()=>refresh()}>Refresh configuration</button></PageAction>} />
 <p>ADMIN controls restaurant operations and integrations. Configured, enabled and tested are separate states. Secrets are write-only.</p>
 <details className="staff-panel shared-staff-orders" onToggle={event=>setPrintingOpen(event.currentTarget.open)}><summary>Printing</summary><p>This printer profile belongs to this application on this browser/device. It is the same profile used by Device settings, not restaurant-wide backend configuration.</p>{printingOpen&&<PrinterSettings config={orders.config} printer={orders.printer} onSave={orders.savePrinter}/>}</details>
 <StaffUnsavedIndicator dirty={dirty} />
 {error&&<p className="staff-error" role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 {configuration&&<><section className="staff-panel"><h2>Integrations overview</h2><nav className="staff-section-navigation" aria-label="Configuration sections">{Object.entries(definitions).map(([key,d])=><button type="button" disabled={busy} aria-pressed={key===category} key={key} onClick={()=>choose(key)}>{d.label}</button>)}</nav><dl className="staff-integration-summary">{Object.entries(definitions).filter(([key])=>key!=='SETTINGS').map(([key,d])=><div key={key}><dt>{d.label}</dt><dd>{configuration[key].state.replaceAll('_',' ')} · {configuration[key].validationStatus.replaceAll('_',' ')}</dd></div>)}</dl></section>
 <form className="staff-panel" onSubmit={save}><fieldset disabled={busy}><legend className="configuration-heading"><h2>{definition.label}</h2></legend>
 {category==='SETTINGS'&&configuration.encryptionAvailable===false&&<p>One-click private order tracking requires the deployment credential master key. Customer emails use verified recovery until that key is configured.</p>}
 {category==='SETTINGS'&&<p>Ordering, reservations, notifications and payment methods. Opening hours and restaurant details remain in Restaurant settings. All changes are validated on the server.</p>}
 {category==='PAYPAL'&&<p>Return URL is deployment-managed: {row.returnUrl}. Saving PayPal changes disables its checkout offer until deliberately re-enabled. Live credentials require successful testing.</p>}
 {category==='PAYID'&&<p>PayID requires manual bank receipt reconciliation. {row.pendingPayments} unpaid PayID orders may have previous instructions.</p>}
 {category==='TWILIO'&&<p>Tests check account/service access without sending SMS. SMS sender capability remains not delivery-tested.</p>}
 {!configuration.encryptionAvailable&&definition.secrets&&<p role="alert">A deployment credential master key is required to store new secrets.</p>}
 {category==='SETTINGS'?businessSettingGroups.map(([heading,keys])=><section className="business-settings-group" key={heading}><h3>{heading}</h3>{keys.map(key=>renderField(key,definition.fields[key]))}</section>):Object.entries(definition.fields).map(([key,label])=>renderField(key,label))}
 {Object.entries(definition.secrets||{}).map(([key,label])=><section key={key}><p>{label}: {row.secretsConfigured[key]?'Configured':'Not configured'} · {row.sources[key]==='DASHBOARD'?'Dashboard managed':'Environment/default supplied'}</p>
 {row.secretsConfigured[key]&&!replace[key]?<button type="button" onClick={()=>setReplace({...replace,[key]:true})}>Replace {label.toLowerCase()}</button>:<label>{label}<input name={key} type="password" autoComplete="new-password" maxLength={4096} value={secrets[key]??''} onChange={e=>setSecrets({...secrets,[key]:e.target.value})}/></label>}
 <label className="configuration-secret-clear"><span className="configuration-checkbox"><input type="checkbox" checked={clear.includes(key)} onChange={e=>setClear(e.target.checked?[...clear,key]:clear.filter(k=>k!==key))}/><span>Explicitly clear {category==='SMTP'?'SMTP password':label.toLowerCase()}</span></span><small>Also suppresses environment fallback.</small></label></section>)}
 {warnings.map(message=><p role="alert" key={message}>{message}</p>)}
 <div className="staff-action-group"><button type="submit" disabled={busy} aria-busy={activity==='save' || undefined}>{activity==='save'?'Saving…':'Save '+definition.label.toLowerCase()}</button>
 <button type="button" disabled={!sectionDirty} onClick={discardSection}>Cancel changes</button>
 {category!=='SETTINGS'&&<button type="button" disabled={!row.configured||sectionDirty} onClick={()=>test()}>Test connection</button>}</div>
 {category!=='SETTINGS'&&<p>Connection tests use saved configuration only. {sectionDirty?'Save or cancel this section’s changes before testing.':'Unsaved values are never sent to a connection test.'}</p>}
 {category==='SMTP'&&<><label>Test email recipient<input type="email" name="testRecipient" maxLength={254} value={recipient} onChange={e=>setRecipient(e.target.value)}/></label><button type="button" disabled={!row.configured||!recipient||sectionDirty} onClick={()=>test(true)}>Send test email</button></>}
 </fieldset></form>
 <section className="staff-panel"><h2>Configuration audit history</h2>{auditError&&<p className="staff-error" role="alert">{auditError}</p>}<p>Most recent 100 events. Secrets and credential material are never recorded.</p><ul>{audit.map((entry,i)=><li key={i}>{String(entry.timestamp)} · {entry.actor} · {entry.category} · {entry.action} · {String(entry.fields)}</li>)}</ul></section></>}
 <dialog ref={dialog} aria-labelledby="settings-discard-title" aria-describedby="settings-discard-description" onCancel={e=>{e.preventDefault();closeConfirmation();}}>
 <h2 id="settings-discard-title">Discard unsaved changes?</h2><p id="settings-discard-description">Your unsaved Settings changes will be lost. Stay to keep editing, or discard and continue.</p>
 <div className="staff-action-group"><button type="button" autoFocus onClick={()=>closeConfirmation()}>Stay</button><button type="button" onClick={()=>closeConfirmation(true)}>Discard and continue</button></div>
 </dialog>
 </main>;
}
