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
  throw new Error(`Timed out: ${expression}\n${await evaluate('JSON.stringify({hash:location.hash,position:history.state?.menuPosition,rapidChoices:window.rapidChoices,traversals:window.traversals})')}\n${await evaluate('document.body.innerText')}`);
};
const click = async text => {
  const button = `Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`;
  await waitFor(button); await evaluate(`${button}.focus();${button}.click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
};
function fixture(){
 window.role='ADMIN';window.writes=[];window.audit=[];window.stale=false;window.reads=0;window.holdWrite=false;window.rejectMessage='';window.auditFailure=false;window.confirm=()=>true;window.alerts=[];window.alert=message=>window.alerts.push(message);window.storageWrites=[];const setItem=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){window.storageWrites.push({key,value});return setItem.call(this,key,value);};
 window.configuration={encryptionAvailable:true};
 const fields={SETTINGS:{businessWebsiteUrl:'',orderingEnabled:'false',reservationsEnabled:'true',orderPhoneRequired:'true',reservationPhoneRequired:'true',orderSms:'true',orderEmail:'false',staffOrderEmail:'false',staffOrderEmailAddress:'hello@nakorn.com.au',reservationSms:'true',reservationEmail:'true',staffReservationEmail:'false',staffReservationEmailAddress:'hello@nakorn.com.au',paypalEnabled:'false',payidEnabled:'false',payAtRestaurantEnabled:'true'},PAYPAL:{environment:'sandbox',clientId:''},PAYID:{identifier:'',accountName:''},TWILIO:{accountSid:'',verifyServiceSid:'',smsFrom:'',verifySmsEnabled:'false',verifyEmailEnabled:'false'},SMTP:{host:'',port:'587',username:'',from:'',starttls:'true'}};
 for(const [category,values] of Object.entries(fields))window.configuration[category]={fields:values,version:category==='SETTINGS'?6:0,sources:Object.fromEntries(Object.keys(values).map(k=>[k,'ENVIRONMENT_DEFAULT'])),secretsConfigured:category==='PAYPAL'?{clientSecret:false}:category==='TWILIO'?{authToken:false}:category==='SMTP'?{password:false}:{},state:'NOT_CONFIGURED',configured:false,enabled:false,validationStatus:'NOT_TESTED',returnUrl:'https://restaurant.example.test/#/order-confirmation',pendingPayments:2,smsVerificationConfigured:false,smsSendingConfigured:false};
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/logout'){window.logouts=(window.logouts||0)+1;return new Response(null,{status:204});}
  if(url==='/api/identity/refresh')return Response.json({accessToken:'test',expiresAt:new Date(Date.now()+3600000).toISOString(),user:{username:'Owner',role:window.role}});
  if(url==='/api/identity/users')return Response.json([]);
  if(url==='/api/staff/restaurant/schedule')return Response.json({settings:{timezone:'Australia/Melbourne',version:1},hours:[],closedDates:[]});
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
   if(window.testDrift){row.fields.clientId='server-confirmed-client';window.configuration.SETTINGS.fields.businessWebsiteUrl='https://saved-other.example.test';window.testDrift=false;}
   row.validationStatus='VALID';return Response.json({status:'VALID',message:'Connection validation succeeded',configuration:window.configuration});
  }
  throw new Error('Unexpected API '+url);
 };
}
const input=async(name,value)=>evaluate(`(()=>{const el=document.querySelector('[name="${name}"]');Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el),'value').set.call(el,${JSON.stringify(value)});el.dispatchEvent(new Event(el.tagName==='SELECT'?'change':'input',{bubbles:true}));})()`);
const category=async(name)=>{const button=`Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()===${JSON.stringify(name)}&&!b.disabled)`;await waitFor(button);await evaluate(`${button}.focus();${button}.click()`);};
try{
 await send('Page.enable');await send('Runtime.enable');await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await send('Page.navigate',{url:`${base}#/staff/settings`});await waitFor("document.body.innerText.includes('Integrations overview')");
 assert.equal(await evaluate("document.querySelector('a[href=\"#/staff/settings\"]')!==null"),true);
 assert.equal(await evaluate("document.body.innerText.includes('Configure Twilio SMS Verify')"),true);
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').type"),'url');
 await input('businessWebsiteUrl','https://restaurant.example.test');
 // Save applies returned metadata/version without a GET; pending submits cannot overlap.
 await waitFor("!document.querySelector('fieldset').disabled");
 assert.deepEqual(await evaluate("Array.from(document.querySelectorAll('.business-settings-group')).map(group=>[group.querySelector('h3').textContent,Array.from(group.querySelectorAll('input')).map(input=>input.name)])"),[
  ['Business identity',['businessWebsiteUrl']],['Online ordering',['orderingEnabled']],['Reservations',['reservationsEnabled']],
  ['Customer verification',['orderPhoneRequired','reservationPhoneRequired']],
  ['Order notifications',['orderSms','orderEmail','staffOrderEmail','staffOrderEmailAddress']],['Reservation notifications',['reservationSms','reservationEmail','staffReservationEmail','staffReservationEmailAddress']],
  ['Payment methods',['paypalEnabled','payidEnabled','payAtRestaurantEnabled']]
 ]);
 assert.equal(await evaluate("document.querySelectorAll('form').length===1&&document.querySelectorAll('button[type=submit]').length===1"),true);
 assert.equal(await evaluate("Array.from(document.querySelectorAll('.business-settings-group label')).filter(label=>label.querySelector('input').type==='checkbox').every(label=>label.firstElementChild.firstElementChild.type==='checkbox'&&label.lastElementChild.tagName==='SMALL')"),true);
 assert.equal(await evaluate("document.querySelector('[name=staffOrderEmailAddress]').type"),'email');
 assert.equal(await evaluate("document.querySelector('[name=staffOrderEmailAddress]').value"),'hello@nakorn.com.au');
 await evaluate("document.querySelector('[name=staffOrderEmail]').click()");
 assert.equal(await evaluate("document.querySelector('[name=staffOrderEmailAddress]').required"),true);
 await input('staffOrderEmailAddress','operations@example.test');
 assert.equal(await evaluate("document.querySelector('[name=staffReservationEmailAddress]').type"),'email');
 assert.equal(await evaluate("document.querySelector('[name=staffReservationEmailAddress]').value"),'hello@nakorn.com.au');
 await evaluate("document.querySelector('[name=staffReservationEmail]').click()");
 assert.equal(await evaluate("document.querySelector('[name=staffReservationEmailAddress]').required"),true);
 await input('staffReservationEmailAddress','reservations@example.test');
 assert.equal(await evaluate("document.querySelector('[name=reservationEmail]').checked"),true);
 await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
 assert.equal(await evaluate("Array.from(document.querySelectorAll('.configuration-checkbox')).every(row=>{const box=row.querySelector('input').getBoundingClientRect(),text=row.querySelector('span').getBoundingClientRect();return box.right<=text.left&&text.right<=innerWidth;})"),true);
 await send('Emulation.clearDeviceMetricsOverride');
 const reads=await evaluate('window.reads');
 await evaluate("document.querySelector('[name=orderingEnabled]').click();window.holdWrite=true");
 await click('Save business settings');await waitFor('window.releaseWrite');
 assert.equal(await evaluate("document.querySelector('button[type=submit]').disabled"),true);
 await evaluate("document.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}))");
 assert.equal(await evaluate('window.writes.length'),1);
 await evaluate("document.querySelector('a[href=\"#/staff\"]').click()");assert.equal(await evaluate('location.hash'),'#/staff/settings');assert.match(await evaluate('window.alerts.at(-1)'),/save to finish/);
 assert.equal(await evaluate("(()=>{const event=new Event('beforeunload',{cancelable:true});window.dispatchEvent(event);return event.defaultPrevented;})()"),true);
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
 assert.match(await evaluate("document.querySelector('[role=alert]').textContent"),/Configure SMTP before enabling email notifications.*edits are retained/);
 assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'Unsaved changes');
 assert.equal(await evaluate("document.querySelector('[name=orderEmail]').checked"),true);
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
 await click('Refresh configuration');await click('Discard and continue');await waitFor("!document.querySelector('[name=clientSecret]')");
 // Shared secret-clear markup keeps the checkbox associated with its label at both widths.
 for(const [name,label] of [['PayPal','Explicitly clear client secret'],['Twilio','Explicitly clear auth token'],['Email / SMTP','Explicitly clear SMTP password']]){
  await category(name);
  assert.equal(await evaluate("document.querySelector('.configuration-secret-clear .configuration-checkbox span').textContent"),label);
  assert.equal(await evaluate("document.querySelector('.configuration-secret-clear small').textContent"),'Also suppresses environment fallback.');
  assert.equal(await evaluate("document.querySelector('.configuration-secret-clear input').labels[0]===document.querySelector('.configuration-secret-clear')"),true);
  for(const mobile of [false,true]){
   if(mobile)await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
   assert.equal(await evaluate("(()=>{const label=document.querySelector('.configuration-secret-clear'),box=label.querySelector('input').getBoundingClientRect(),text=label.querySelector('.configuration-checkbox span').getBoundingClientRect(),helper=label.querySelector('small');return box.right<=text.left&&text.right<=innerWidth&&helper.getBoundingClientRect().top>=text.bottom&&parseFloat(getComputedStyle(helper).fontSize)<parseFloat(getComputedStyle(label).fontSize);})()"),true);
   if(mobile)await send('Emulation.clearDeviceMetricsOverride');
  }
  await evaluate("document.querySelector('.configuration-secret-clear .configuration-checkbox span').click()");
  assert.equal(await evaluate("document.querySelector('.configuration-secret-clear input').checked"),true);
  await evaluate("document.querySelector('.configuration-secret-clear .configuration-checkbox span').click()");
  assert.equal(await evaluate("document.querySelector('.configuration-secret-clear input').checked"),false);
 }
 await category('PayID');await input('identifier','restaurant@example.test');await input('accountName','Restaurant Test');await click('Save payid');await waitFor("document.body.innerText.includes('configuration saved')");
 assert.equal(await evaluate("window.writes.at(-1).body.acknowledgePendingPayments"),true);
 assert.equal(await evaluate("window.configuration.PAYID.fields.accountName"),'Restaurant Test');
 await click('Business settings');await evaluate("document.querySelector('[name=payidEnabled]').click()");await click('Save business settings');await waitFor("document.body.innerText.includes('Business settings saved.')");assert.equal(await evaluate("window.configuration.SETTINGS.fields.payidEnabled"),'true');
 assert.equal(await evaluate("document.body.innerText.includes('PAYID_CONFIGURATION_UPDATED')"),true);
 assert.equal(await evaluate("window.writes.every(r=>r.headers['X-CSRF-TOKEN']==='test')"),true);
 // UX-02/03: preserve section drafts, test saved values only, and guard all exits.
 await category('PayPal');await input('clientId','section-draft');await click('Replace client secret');await input('clientSecret','memory-only-secret');
 assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'Unsaved changes');
 assert.equal(await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Test connection').disabled"),true);
 assert.equal(await evaluate("document.body.innerText.includes('saved configuration only')"),true);
 const beforeBlockedTest=await evaluate('window.writes.length');
 await evaluate("Array.from(document.querySelectorAll('button')).find(b=>b.textContent==='Test connection').click()");assert.equal(await evaluate('window.writes.length'),beforeBlockedTest);
 await category('PayID');await input('accountName','second-section-draft');await category('PayPal');
 assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),'section-draft');assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'memory-only-secret');
 assert.equal(await evaluate("(()=>{const event=new Event('beforeunload',{cancelable:true});window.dispatchEvent(event);return event.defaultPrevented;})()"),true);
 assert.equal(await evaluate("[localStorage,sessionStorage].some(storage=>Object.values(storage).some(value=>/memory-only-secret|section-draft|second-section-draft/.test(value)))"),false);
 await click('Refresh configuration');await waitFor("document.querySelector('dialog').open");assert.equal(await evaluate("document.activeElement.textContent"),'Stay');
 await evaluate("document.querySelector('dialog').dispatchEvent(new Event('cancel',{cancelable:true}))");assert.equal(await evaluate("document.activeElement.textContent"),'Refresh configuration');
 await click('Refresh configuration');await click('Stay');assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'memory-only-secret');
 await click('Cancel changes');assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),await evaluate('window.configuration.PAYPAL.fields.clientId'));
 assert.equal(await evaluate("document.querySelector('[name=clientSecret]')===null"),true);assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'Unsaved changes');
 await click('Test connection');await waitFor("document.body.innerText.includes('VALID: Connection validation succeeded')&&!document.querySelector('fieldset').disabled");
 assert.deepEqual(await evaluate('Object.keys(window.writes.at(-1).body)'),['version']);assert.equal(await evaluate('window.writes.at(-1).body.version'),await evaluate('window.configuration.PAYPAL.version-1'));
 await evaluate("window.rejectMessage='Test service unavailable'");await click('Test connection');await waitFor("document.body.innerText.includes('Test service unavailable')&&!document.querySelector('fieldset').disabled");await evaluate("window.rejectMessage=''");
 await category('PayID');assert.equal(await evaluate("document.querySelector('[name=accountName]').value"),'second-section-draft');await click('Cancel changes');await category('PayPal');
 // A response with new server values updates the baseline but cannot overwrite visible inputs.
 const visibleBeforeTest=await evaluate("document.querySelector('[name=clientId]').value");await evaluate('window.testDrift=true');await click('Test connection');await waitFor("document.body.innerText.includes('VALID: Connection validation succeeded')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),visibleBeforeTest);assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'Unsaved changes');await category('Business settings');assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'https://saved-other.example.test');await category('PayPal');assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),visibleBeforeTest);await click('Cancel changes');assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),'server-confirmed-client');
 await evaluate("window.stale=true");await click('Test connection');await waitFor("document.body.innerText.includes('Configuration changed')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),await evaluate('window.configuration.PAYPAL.fields.clientId'));
 assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'No unsaved changes');
 await click('Replace client secret');await input('clientSecret','failed-private-secret');await input('clientId','failed-nonsensitive-draft');
 await evaluate("window.rejectMessage='Correct credentials';document.querySelector('.configuration-secret-clear input').click()");await click('Save paypal');await waitFor("document.body.innerText.includes('Correct credentials')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'');assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),'failed-nonsensitive-draft');assert.equal(await evaluate("document.querySelector('.configuration-secret-clear input').checked"),true);
 await evaluate("window.rejectMessage=''");await click('Cancel changes');await input('clientId','navigation-draft');
 await evaluate("document.querySelector('a[href=\"#/staff\"]').click()");await waitFor("document.querySelector('dialog').open");await click('Stay');assert.equal(await evaluate('location.hash'),'#/staff/settings');assert.equal(await evaluate("document.querySelector('[name=clientId]').value"),'navigation-draft');
 await evaluate("document.querySelector('a[href=\"#/staff\"]').click()");await click('Discard and continue');await waitFor("!document.body.innerText.includes('Integrations overview')");
 await evaluate("location.hash='#/staff/settings'");await waitFor("document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 // Known three-entry application history: Restaurant -> Accounts -> Settings.
 assert.equal(await evaluate("window.storageWrites.some(({value})=>/memory-only-secret|failed-private-secret|browser-test-secret|section-draft/.test(value))"),false);
 await send('Page.navigate',{url:`${base}?history-review=1#/staff/restaurant`});await waitFor("document.body.innerText.includes('Restaurant scheduling')");
 await evaluate("location.hash='#/staff/users'");await waitFor("document.body.innerText.includes('Staff accounts')");
 await evaluate("location.hash='#/staff/settings'");await waitFor("document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 await evaluate("window.historyLength=history.length;window.traversals=[];const originalGo=history.go.bind(history);history.go=delta=>{traversals.push({delta,hash:location.hash});if(window.holdNextTraversal){window.holdNextTraversal=false;window.releaseTraversal=()=>originalGo(delta);}else originalGo(delta);};");
 // Resolve from the open mutation, without waiting for restoration or using a sleep.
 const immediateChoice=async(direction,choice,extra='')=>evaluate(`new Promise(resolve=>{
   const dialog=document.querySelector('dialog');
   const observer=new MutationObserver(()=>{if(!dialog.open)return;observer.disconnect();
     window.choiceHash=location.hash;${extra}
     [...dialog.querySelectorAll('button')].find(button=>button.textContent===${JSON.stringify(choice)}).click();resolve();
   });observer.observe(dialog,{attributes:true,attributeFilter:['open']});history.${direction}();
 })`);
 await input('businessWebsiteUrl','https://back-draft.example.test');
 // An explicit barrier holds the native restoration until the immediate choice has run.
 await evaluate('window.holdNextTraversal=true');
 await immediateChoice('back','Discard and continue');
 assert.equal(await evaluate('choiceHash'),'#/staff/users');
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1]);
 assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'No unsaved changes');
 await evaluate('window.releaseTraversal()');
 await waitFor("location.hash==='#/staff/users'&&document.body.innerText.includes('Staff accounts')");
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1,-1]);
 assert.equal(await evaluate('history.length'),await evaluate('historyLength'));
 // Subsequent Back/Forward traverse the original entries; Settings remounts from saved values.
 await evaluate('history.back()');await waitFor("location.hash==='#/staff/restaurant'&&document.body.innerText.includes('Restaurant scheduling')");
 await evaluate('history.forward()');await waitFor("location.hash==='#/staff/users'&&document.body.innerText.includes('Staff accounts')");
 await evaluate('history.forward()');await waitFor("location.hash==='#/staff/settings'&&document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'');
 assert.equal(await evaluate("document.querySelector('.staff-unsaved').textContent"),'No unsaved changes');
 assert.equal(await evaluate('history.length'),await evaluate('historyLength'));
 // Back -> Stay preserves both the draft and the stack.
 await input('businessWebsiteUrl','https://stay-draft.example.test');await evaluate('window.holdNextTraversal=true');await immediateChoice('back','Stay');await evaluate('window.releaseTraversal()');
 await waitFor("location.hash==='#/staff/settings'&&!document.querySelector('[aria-labelledby=settings-discard-title]').open");
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'https://stay-draft.example.test');
 // Rapid native attempts cannot accept an obsolete continuation after Stay.
 await evaluate(`window.rapidChoices=0;window.rapidObserver=new MutationObserver(()=>{const dialog=document.querySelector('dialog');if(!dialog.open)return;rapidChoices++;dialog.querySelector('button').click();});rapidObserver.observe(document.querySelector('dialog'),{attributes:true,attributeFilter:['open']});history.back();history.back();`);
 await waitFor("rapidChoices>0&&location.hash==='#/staff/settings'&&!document.querySelector('[aria-labelledby=settings-discard-title]').open");await evaluate('rapidObserver.disconnect()');
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'https://stay-draft.example.test');
 await click('Cancel changes');
 // Add a forward destination, return to Settings, then exercise Forward -> Stay/Discard.
 await evaluate("location.hash='#/staff/users'");await waitFor("document.body.innerText.includes('Staff accounts')");
 await evaluate('history.back()');await waitFor("location.hash==='#/staff/settings'&&document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 await input('businessWebsiteUrl','https://forward-draft.example.test');await evaluate('window.traversals=[];window.historyLength=history.length;window.holdNextTraversal=true');
 await immediateChoice('forward','Stay');assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[-1]);await evaluate('window.releaseTraversal()');await waitFor("location.hash==='#/staff/settings'&&!document.querySelector('[aria-labelledby=settings-discard-title]').open");
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'https://forward-draft.example.test');
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[-1]);
 await evaluate('window.traversals=[];window.holdNextTraversal=true');await immediateChoice('forward','Discard and continue');assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[-1]);await evaluate('window.releaseTraversal()');await waitFor("location.hash==='#/staff/users'&&document.body.innerText.includes('Staff accounts')");
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[-1,1]);assert.equal(await evaluate('history.length'),await evaluate('historyLength'));
 await evaluate('history.back()');await waitFor("location.hash==='#/staff/settings'&&document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("document.querySelector('[name=businessWebsiteUrl]').value"),'');
 // New attempts replace queued approvals before restoration: only the latest destination runs.
 await input('businessWebsiteUrl','https://newer-draft.example.test');await evaluate('window.traversals=[];window.holdNextTraversal=true');
 await immediateChoice('back','Discard and continue',`const oldButton=[...dialog.querySelectorAll('button')].find(button=>button.textContent==='Discard and continue');oldButton.click();document.querySelector('a[href="#/staff/restaurant"]').click();`);
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1]);await evaluate('window.releaseTraversal()');
 await waitFor("location.hash==='#/staff/restaurant'&&document.body.innerText.includes('Restaurant scheduling')");
 assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1]); // The obsolete -1 was cancelled.
 await evaluate("location.hash='#/staff/settings'");await waitFor("document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 // Rapid confirmations and guard cleanup are checked through the same live guard module.
 await input('businessWebsiteUrl','https://replacement-draft.example.test');
 await evaluate(`(async()=>{window.reviewGuard=await import('/src/domains/menu/model/menuAdminNavigation.js');window.continuationCalls=[];
   reviewGuard.allowMenuNavigation(undefined,undefined,()=>{continuationCalls.push('older');});
   reviewGuard.allowMenuNavigation(undefined,undefined,()=>{continuationCalls.push('latest');});})()`);
 await click('Discard and continue');assert.deepEqual(await evaluate('continuationCalls'),['latest']);
 await input('businessWebsiteUrl','https://cancelled-draft.example.test');
 await evaluate("reviewGuard.allowMenuNavigation(undefined,undefined,()=>{continuationCalls.push('cancelled');})");await click('Stay');
 await evaluate("reviewGuard.allowMenuNavigation(undefined,undefined,()=>{continuationCalls.push('after-stay');})");await click('Discard and continue');assert.deepEqual(await evaluate('continuationCalls'),['latest','after-stay']);
 // Queued token invalidation on actual Settings unmount (session expiry).
 await input('businessWebsiteUrl','https://cleanup-draft.example.test');
 await evaluate('window.traversals=[];window.holdNextTraversal=true');await immediateChoice('back','Discard and continue');assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1]);
 await evaluate("(async()=>{window.identityReview=await import('/src/domains/identity/api/identityApi.js');const fetchBefore=window.fetch;window.fetch=(url,options)=>url==='/api/identity/refresh'?Promise.resolve(Response.json({message:'Expired'},{status:401})):fetchBefore(url,options);await identityReview.refreshAccess().catch(()=>{});window.fetch=fetchBefore;})()");
 await waitFor("!document.body.innerText.includes('Integrations overview')");await evaluate('window.releaseTraversal()');await waitFor("location.hash==='#/staff/settings'&&!document.querySelector('[aria-labelledby=settings-discard-title]')");assert.deepEqual(await evaluate('traversals.map(call=>call.delta)'),[1]);
 await evaluate('identityReview.refreshAccess()');await waitFor("document.body.innerText.includes('Integrations overview')&&!document.querySelector('fieldset').disabled");
 assert.equal(await evaluate("(()=>{const event=new Event('beforeunload',{cancelable:true});window.dispatchEvent(event);return event.defaultPrevented;})()"),false);
 // Sign out also resumes only after explicit discard.
 await input('businessWebsiteUrl','https://signout-draft.example.test');await click('Sign out');await click('Stay');assert.equal(await evaluate('window.logouts||0'),0);
 await click('Sign out');await click('Discard and continue');await waitFor('window.logouts===1');await waitFor("!document.body.innerText.includes('Integrations overview')");
 // Same route guards FOH: no settings panel or admin navigation is rendered.
 await evaluate("window.role='FOH';location.hash='#/staff';location.hash='#/staff/settings'");
 // Auth context refresh requires a new document; overriding fixture defaults after installation.
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"window.role='FOH'"});await send('Page.reload');await waitFor("document.body.innerText.includes('Access restricted')");
 assert.equal(await evaluate("document.querySelector('a[href=\"#/staff/settings\"]')===null"),true);
 assert.equal(await evaluate("window.storageWrites.some(({value})=>/memory-only-secret|failed-private-secret|browser-test-secret|section-draft/.test(value))"),false);
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"window.role='BOH'"});await send('Page.reload');await waitFor("document.body.innerText.includes('Access restricted')");assert.equal(await evaluate("document.querySelector('form')===null"),true);
 assert.deepEqual(errors,[]);console.log('Admin settings browser: metadata, dependency warnings, write-only replacement, stale conflict, tests, PayID warning, authoritative save/source/version, failed SETTINGS draft preservation without GET, integration text preservation, validation errors, duplicate protection, stale response protection, section drafts, immediate Back/Forward Discard, Stay, history stack integrity, superseded approvals, unmount cleanup, sign-out, in-flight blocking, beforeunload, saved-only tests, test response isolation, cancel baselines, no secret storage, audit failure isolation and ADMIN/FOH/BOH access passed.');
}finally{await send('Page.close');ws.close();}
