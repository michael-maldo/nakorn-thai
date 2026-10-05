/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.SETTINGS_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0;
const pending = new Map(), errors = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); pending.delete(message.id);
    message.error ? waiter.reject(message.error) : waiter.resolve(message.result);
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails.text);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence; pending.set(id, { resolve, reject }); ws.send(JSON.stringify({ id, method, params }));
});
const evaluate = async expression => {
  const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 150; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 40));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};
const click = async text => {
  const button = `Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`;
  await waitFor(button); await evaluate(`${button}.click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
};
function fixture(){
 window.role='ADMIN';window.writes=[];window.audit=[];window.stale=false;window.reads=0;window.holdWrite=false;window.rejectMessage='';window.auditFailure=false;window.confirm=()=>true;
 window.configuration={encryptionAvailable:true};
 const fields={SETTINGS:{orderingEnabled:'false',reservationsEnabled:'true',orderPhoneRequired:'true',reservationPhoneRequired:'true',orderSms:'true',orderEmail:'false',reservationSms:'true',reservationEmail:'true',paypalEnabled:'false',payidEnabled:'false',payAtRestaurantEnabled:'true'},PAYPAL:{environment:'sandbox',clientId:''},PAYID:{identifier:'',accountName:''},TWILIO:{accountSid:'',verifyServiceSid:'',smsFrom:'',verifySmsEnabled:'false',verifyEmailEnabled:'false'},SMTP:{host:'',port:'587',username:'',from:'',starttls:'true'}};
 for(const [category,values] of Object.entries(fields))window.configuration[category]={fields:values,version:category==='SETTINGS'?6:0,sources:Object.fromEntries(Object.keys(values).map(k=>[k,'ENVIRONMENT_DEFAULT'])),secretsConfigured:category==='PAYPAL'?{clientSecret:false}:category==='TWILIO'?{authToken:false}:category==='SMTP'?{password:false}:{},state:'NOT_CONFIGURED',configured:false,enabled:false,validationStatus:'NOT_TESTED',returnUrl:'https://restaurant.example.test/#/order-confirmation',pendingPayments:2,smsVerificationConfigured:false,smsSendingConfigured:false};
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return Response.json({accessToken:'test',expiresAt:new Date(Date.now()+3600000).toISOString(),user:{username:'Owner',role:window.role}});
  if(url==='/api/staff/restaurant/configuration/audit'){if(window.auditFailure)return Response.json({message:'Audit unavailable'},{status:500});return Response.json(window.audit);}
  if(url==='/api/staff/restaurant/configuration'){
   window.reads++;
   // StrictMode cleans up the first mount effect before starting the current read.
   if(window.reads===1){const stale=structuredClone(window.configuration);stale.SETTINGS.version=1;return new Promise(resolve=>{window.releaseInitialRead=()=>resolve(Response.json(stale));});}
   return Response.json(window.configuration);
  }
  if(url.startsWith('/api/staff/restaurant/configuration/')){
   const body=JSON.parse(options.body);window.writes.push({url,body,headers:options.headers});
   if(window.holdWrite)await new Promise(resolve=>{window.releaseWrite=resolve;});
   if(window.rejectMessage)return Response.json({message:window.rejectMessage},{status:400});
   if(window.stale){window.stale=false;return Response.json({message:'Configuration changed; refresh before saving or testing'},{status:409});}
   const category=url.split('/')[5],row=window.configuration[category];row.version++;
   if(options.method==='PUT'){
    Object.assign(row.fields,body.fields);for(const [key,value] of Object.entries(body.secrets||{}))if(value)row.secretsConfigured[key]=true;
    for(const key of body.clearSecrets||[])row.secretsConfigured[key]=false;
    // API metadata never contains supplied credential material.
    row.configured=true;row.state='CONFIGURED';row.validationStatus='NOT_TESTED';
    for(const key of Object.keys(body.fields))row.sources[key]='DASHBOARD';
    window.audit.push({timestamp:new Date().toISOString(),actor:'Owner',category,action:category+'_CONFIGURATION_UPDATED',fields:Object.keys(body.fields)});
    return Response.json(window.configuration);
   }
   row.validationStatus='VALID';return Response.json({status:'VALID',message:'Connection validation succeeded',configuration:window.configuration});
  }
  throw new Error('Unexpected API '+url);
 };
}
const input=async(name,value)=>evaluate(`(()=>{const el=document.querySelector('[name="${name}"]');Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`);
const category=async(name)=>{const button=`Array.from(document.querySelectorAll('button')).find(b=>b.textContent.startsWith(${JSON.stringify(name+' ·')})&&!b.disabled)`;await waitFor(button);await evaluate(`${button}.click()`);};
try{
 await send('Page.enable');await send('Runtime.enable');await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await send('Page.navigate',{url:`${base}#/staff/settings`});await waitFor("document.body.innerText.includes('Integrations overview')");
 assert.equal(await evaluate("document.querySelector('a[href=\"#/staff/settings\"]')!==null"),true);
 assert.equal(await evaluate("document.body.innerText.includes('Configure Twilio SMS Verify')"),true);
 // Save applies returned metadata/version without a GET; pending submits cannot overlap.
 await waitFor("!document.querySelector('fieldset').disabled");
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.business-settings-group')).map(group=>[group.querySelector('h3').textContent,Array.from(group.querySelectorAll('input')).map(input=>input.name)])"),[
  ['Online ordering',['orderingEnabled']],['Reservations',['reservationsEnabled']],
  ['Customer verification',['orderPhoneRequired','reservationPhoneRequired']],
  ['Order notifications',['orderSms','orderEmail']],['Reservation notifications',['reservationSms','reservationEmail']],
  ['Payment methods',['paypalEnabled','payidEnabled','payAtRestaurantEnabled']]
 ]);
 assert.equal(await evaluate("document.querySelectorAll('form').length===1&&document.querySelectorAll('button[type=submit]').length===1"),true);
 assert.equal(await evaluate("Array.from(document.querySelectorAll('.business-settings-group label')).every(label=>label.firstElementChild.firstElementChild.type==='checkbox'&&label.lastElementChild.tagName==='SMALL')"),true);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 assert.equal(await evaluate("Array.from(document.querySelectorAll('.configuration-checkbox')).every(row=>{const box=row.querySelector('input').getBoundingClientRect(),text=row.querySelector('span').getBoundingClientRect();return box.right<=text.left&&text.right<=innerWidth;})"),true);
 await send('Emulation.clearDeviceMetricsOverride');
 const reads=await evaluate('window.reads');
 await evaluate("document.querySelector('[name=orderingEnabled]').click();window.holdWrite=true");
 await click('Save business settings');await waitFor('window.releaseWrite');
 assert.equal(await evaluate("document.querySelector('button[type=submit]').disabled"),true);
 await evaluate("document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");
 assert.equal(await evaluate('window.writes.length'),1);
 await evaluate('window.holdWrite=false;window.releaseWrite()');await waitFor("document.body.innerText.includes('Business settings saved.')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').checked"),true);
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').closest('label').textContent.includes('Dashboard managed')"),true);
 assert.equal(await evaluate('window.writes.at(-1).body.version'),6);
 assert.equal(await evaluate('window.reads'),reads);
 // The abandoned initial request resolves after the save and must not overwrite it.
 await evaluate('window.releaseInitialRead();new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').checked"),true);
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').closest('label').textContent.includes('Dashboard managed')"),true);
 // Audit failure must not turn a persisted save into an apparent save failure.
 await evaluate('window.auditFailure=true');await click('Save business settings');
 await waitFor("document.body.innerText.includes('audit history is unavailable')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate('window.writes.at(-1).body.version'),7);
 assert.equal(await evaluate("document.body.innerText.includes('Business settings saved.')"),true);
 const readsBeforeFailure=await evaluate('window.reads');
 const sourceBeforeFailure=await evaluate("document.querySelector('[name=orderEmail]').closest('label').querySelector('small').textContent");
 await evaluate("window.auditFailure=false;window.rejectMessage='Configure SMTP before enabling email notifications';document.querySelector('[name=orderEmail]').click()");
 assert.equal(await evaluate("document.querySelector('[name=orderEmail]').checked"),true);
 await click('Save business settings');await waitFor("document.body.innerText.includes('Configure SMTP before enabling email notifications')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[role=alert]').textContent"),'Configure SMTP before enabling email notifications');
 assert.equal(await evaluate("document.querySelector('[role=status]')"),null);
 assert.equal(await evaluate("document.querySelector('[name=orderEmail]').checked"),false);
 assert.equal(await evaluate("window.configuration.SETTINGS.fields.orderEmail"),'false');
 assert.equal(await evaluate("document.querySelector('[name=orderEmail]').closest('label').querySelector('small').textContent"),sourceBeforeFailure);
 assert.equal(await evaluate('window.reads'),readsBeforeFailure);
 // A subsequent valid save uses the unchanged authoritative version.
 await evaluate("window.rejectMessage='';document.querySelector('[name=orderingEnabled]').click()");
 await click('Save business settings');await waitFor("document.body.innerText.includes('Business settings saved.')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate('window.writes.at(-1).body.version'),8);
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').checked"),false);
 assert.equal(await evaluate("document.querySelector('.staff-error[role=alert]')===null"),true);
 assert.equal(await evaluate("window.configuration.SETTINGS.fields.orderingEnabled"),'false');
 assert.equal(await evaluate('window.configuration.SETTINGS.version'),9);
 // Explicit refresh discards edits in favor of a fresh authoritative GET.
 await evaluate("window.rejectMessage='';window.configuration.SETTINGS.version=10;window.configuration.SETTINGS.fields.orderingEnabled='true';window.configuration.SETTINGS.sources.orderingEnabled='ENVIRONMENT_DEFAULT'");
 await click('Refresh configuration');await waitFor("!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').checked"),true);
 assert.equal(await evaluate("document.querySelector('[name=orderingEnabled]').closest('label').textContent.includes('Environment/default supplied')"),true);
 await click('Save business settings');await waitFor("document.body.innerText.includes('Business settings saved.')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate('window.writes.at(-1).body.version'),10);
 await category('PayPal');await input('clientId','sandbox-test-merchant');await input('clientSecret','browser-test-secret');await click('Save paypal');await waitFor("document.body.innerText.includes('configuration saved')");
 assert.equal(await evaluate("document.querySelector('[name=clientSecret]')===null"),true);
 assert.equal(await evaluate("document.body.innerText.includes('browser-test-secret')"),false);
 assert.equal(await evaluate("JSON.stringify(window.configuration).includes('browser-test-secret')"),false);
 await click('Replace client secret');assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'');
 await input('clientSecret','replacement-test-secret');await click('Save paypal');await waitFor("!document.querySelector('[name=clientSecret]')");
 await click('Test connection');await waitFor("document.body.innerText.includes('VALID: Connection validation succeeded')");
 await click('Replace client secret');await input('clientSecret','unsaved-secret');await input('clientId','correctable-merchant');await evaluate('window.stale=true');await click('Save paypal');await waitFor("document.body.innerText.includes('Configuration changed')");assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'');
 assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),'correctable-merchant');
 await click('Refresh configuration');await waitFor("!document.querySelector('[name=clientSecret]')");
 await category('PayID');await input('identifier','restaurant@example.test');await input('accountName','Restaurant Test');await click('Save payid');await waitFor("document.body.innerText.includes('configuration saved')");
 assert.equal(await evaluate("window.writes.at(-1).body.acknowledgePendingPayments"),true);
 assert.equal(await evaluate("window.configuration.PAYID.fields.accountName"),'Restaurant Test');
 await click('Business settings');await evaluate("document.querySelector('[name=payidEnabled]').click()");await click('Save business settings');await waitFor("document.body.innerText.includes('Business settings saved.')");assert.equal(await evaluate("window.configuration.SETTINGS.fields.payidEnabled"),'true');
 assert.equal(await evaluate("document.body.innerText.includes('PAYID_CONFIGURATION_UPDATED')"),true);
 assert.equal(await evaluate("window.writes.every(r=>r.headers['X-CSRF-TOKEN']==='test')"),true);
 // Same route guards FOH: no settings panel or admin navigation is rendered.
 await evaluate("window.role='FOH';location.hash='#/staff';location.hash='#/staff/settings'");
 // Auth context refresh requires a new document; overriding fixture defaults after installation.
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"window.role='FOH'"});await send('Page.reload');await waitFor("document.body.innerText.includes('Access restricted')");
 assert.equal(await evaluate("document.querySelector('a[href=\"#/staff/settings\"]')===null"),true);
 assert.deepEqual(errors,[]);console.log('Admin settings browser: metadata, dependency warnings, write-only replacement, stale conflict, tests, PayID warning, authoritative save/source/version, failed SETTINGS rollback without GET, integration text preservation, validation errors, duplicate protection, stale response protection, refresh, audit failure isolation and ADMIN access passed.');
}finally{await send('Page.close');ws.close();}
