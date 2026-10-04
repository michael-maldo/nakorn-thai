import {useEffect,useState} from 'react';
import {restaurantRequest} from '../api/restaurantApi';
import {definitions,booleanFields,dependencyWarnings,replacementBody} from '../model/integrationSettings';
export default function IntegrationSettingsPage(){
 const [configuration,setConfiguration]=useState(null),[category,setCategory]=useState('SETTINGS'),[fields,setFields]=useState({}),[secrets,setSecrets]=useState({}),[replace,setReplace]=useState({}),[clear,setClear]=useState([]),[audit,setAudit]=useState([]),[recipient,setRecipient]=useState('');
 const [busy,setBusy]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState('');
 function loaded(data,key=category){setConfiguration(data);setFields({...data[key].fields});setSecrets({});setReplace({});setClear([]);}
 async function refresh(){setBusy(true);setError('');try{const data=await restaurantRequest('/configuration');loaded(data);setAudit(await restaurantRequest('/configuration/audit'));}catch(e){setError(e.message);}finally{setBusy(false);}}
 useEffect(()=>{refresh();},[]);
 function choose(key){setCategory(key);setFields({...configuration[key].fields});setSecrets({});setReplace({});setClear([]);setError('');setNotice('');}
 async function save(e){e.preventDefault();let acknowledge=false;
  if(category==='PAYPAL'&&fields.environment==='live'&&configuration.PAYPAL.fields.environment!=='live'&&!window.confirm('Switch to live PayPal? Checkout will remain disabled until live credentials are tested and explicitly enabled.'))return;
  if(category==='PAYID'&&configuration.PAYID.pendingPayments>0){acknowledge=window.confirm('Unpaid PayID orders may have previous payment instructions. Coordinate with staff before changing PayID. Continue?');if(!acknowledge)return;}
  if(category==='SETTINGS'&&configuration.SETTINGS.fields.payAtRestaurantEnabled==='true'&&fields.payAtRestaurantEnabled==='false'&&!window.confirm('Disable pay at restaurant? Online ordering must retain another configured payment method.'))return;
  if(category==='SETTINGS'&&((configuration.SETTINGS.fields.orderPhoneRequired==='true'&&fields.orderPhoneRequired==='false')||(configuration.SETTINGS.fields.reservationPhoneRequired==='true'&&fields.reservationPhoneRequired==='false'))&&!window.confirm('Disable mandatory mobile verification? SMS updates for that flow must also be disabled.'))return;
  if(clear.length&&!window.confirm('Clear the selected credentials? Required features must be disabled first.'))return;
  setBusy(true);setError('');setNotice('');try{loaded(await restaurantRequest('/configuration/'+category,{method:'PUT',body:replacementBody(configuration[category].version,fields,secrets,clear,acknowledge)}));setNotice('Configuration saved. Configured does not mean tested or enabled.');setAudit(await restaurantRequest('/configuration/audit'));}catch(e){setError(e.message);}finally{setSecrets({});setBusy(false);}
 }
 async function test(sendEmail=false){if(sendEmail&&!window.confirm(`Send one explicit test email to ${recipient}?`))return;setBusy(true);setError('');setNotice('');try{const result=await restaurantRequest('/configuration/'+category+(sendEmail?'/test-email':'/test'),{method:'POST',body:{version:configuration[category].version,...(sendEmail?{recipient}:{})}});loaded(result.configuration);setNotice(result.status+': '+result.message);setAudit(await restaurantRequest('/configuration/audit'));}catch(e){setError(e.message);}finally{setSecrets({});setBusy(false);}}
 const row=configuration?.[category],definition=definitions[category];
 const warnings=configuration&&category==='SETTINGS'?dependencyWarnings(fields,configuration):[];
 return <main className="staff-menu page-width"><header className="staff-heading"><h1>Settings and integrations</h1><button disabled={busy} onClick={refresh}>Refresh configuration</button></header>
 <p>ADMIN controls restaurant operations and integrations. Configured, enabled and tested are separate states. Secrets are write-only.</p>
 {error&&<p className="staff-error" role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
 {configuration&&<><section className="staff-panel"><h2>Integrations overview</h2><div className="staff-toolbar">{Object.entries(definitions).map(([key,d])=><button disabled={busy} aria-pressed={key===category} key={key} onClick={()=>choose(key)}>{d.label}{key==='SETTINGS'?'':` · ${configuration[key].state} · ${configuration[key].validationStatus}`}</button>)}</div></section>
 <form className="staff-panel" onSubmit={save}><fieldset disabled={busy}><legend>{definition.label}</legend>
 {category==='SETTINGS'&&<p>Ordering, reservations, notifications and payment methods. Opening hours and restaurant details remain in Restaurant settings. All changes are validated on the server.</p>}
 {category==='PAYPAL'&&<p>Return URL is deployment-managed: {row.returnUrl}. Saving PayPal changes disables its checkout offer until deliberately re-enabled. Live credentials require successful testing.</p>}
 {category==='PAYID'&&<p>PayID requires manual bank receipt reconciliation. {row.pendingPayments} unpaid PayID orders may have previous instructions.</p>}
 {category==='TWILIO'&&<p>Tests check account/service access without sending SMS. SMS sender capability remains not delivery-tested.</p>}
 {!configuration.encryptionAvailable&&definition.secrets&&<p role="alert">A deployment credential master key is required to store new secrets.</p>}
 {Object.entries(definition.fields).map(([key,label])=><label key={key}>{label} {booleanFields.has(key)?<input name={key} type="checkbox" checked={fields[key]==='true'} onChange={e=>setFields({...fields,[key]:String(e.target.checked)})}/>:key==='environment'?<select name={key} value={fields[key]} onChange={e=>setFields({...fields,[key]:e.target.value})}><option value="sandbox">Sandbox</option><option value="live">Live</option></select>:<input name={key} type={key==='port'?'number':'text'} min={key==='port'?1:undefined} max={key==='port'?65535:undefined} maxLength={500} value={fields[key]??''} onChange={e=>setFields({...fields,[key]:e.target.value})}/>}<small>Source: {row.sources[key]==='DASHBOARD'?'Dashboard managed':'Environment/default supplied'}</small></label>)}
 {Object.entries(definition.secrets||{}).map(([key,label])=><section key={key}><p>{label}: {row.secretsConfigured[key]?'Configured':'Not configured'} · {row.sources[key]==='DASHBOARD'?'Dashboard managed':'Environment/default supplied'}</p>
 {row.secretsConfigured[key]&&!replace[key]?<button type="button" onClick={()=>setReplace({...replace,[key]:true})}>Replace {label.toLowerCase()}</button>:<label>{label}<input name={key} type="password" autoComplete="new-password" maxLength={4096} value={secrets[key]??''} onChange={e=>setSecrets({...secrets,[key]:e.target.value})}/></label>}
 <label><input type="checkbox" checked={clear.includes(key)} onChange={e=>setClear(e.target.checked?[...clear,key]:clear.filter(k=>k!==key))}/>Explicitly clear {label.toLowerCase()} (also suppresses environment fallback)</label></section>)}
 {warnings.map(message=><p role="alert" key={message}>{message}</p>)}
 <button type="submit">Save {definition.label.toLowerCase()}</button>
 {category!=='SETTINGS'&&<button type="button" disabled={!row.configured} onClick={()=>test()}>Test connection</button>}
 {category==='SMTP'&&<><label>Test email recipient<input type="email" name="testRecipient" maxLength={254} value={recipient} onChange={e=>setRecipient(e.target.value)}/></label><button type="button" disabled={!row.configured||!recipient} onClick={()=>test(true)}>Send test email</button></>}
 </fieldset></form>
 <section className="staff-panel"><h2>Configuration audit history</h2><p>Most recent 100 events. Secrets and credential material are never recorded.</p><ul>{audit.map((entry,i)=><li key={i}>{String(entry.timestamp)} · {entry.actor} · {entry.category} · {entry.action} · {String(entry.fields)}</li>)}</ul></section></>}
 </main>;
}
