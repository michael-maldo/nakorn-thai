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
 window.role='ADMIN';window.writes=[];window.audit=[];window.stale=false;window.confirm=()=>true;
 window.configuration={encryptionAvailable:true};
 const fields={SETTINGS:{orderingEnabled:'false',reservationsEnabled:'true',orderPhoneRequired:'true',reservationPhoneRequired:'true',orderSms:'true',orderEmail:'true',reservationSms:'true',reservationEmail:'true',paypalEnabled:'false',payidEnabled:'false',payAtRestaurantEnabled:'true'},PAYPAL:{environment:'sandbox',clientId:''},PAYID:{identifier:'',accountName:''},TWILIO:{accountSid:'',verifyServiceSid:'',smsFrom:'',verifySmsEnabled:'false',verifyEmailEnabled:'false'},SMTP:{host:'',port:'587',username:'',from:'',starttls:'true'}};
 for(const [category,values] of Object.entries(fields))window.configuration[category]={fields:values,version:0,sources:Object.fromEntries(Object.keys(values).map(k=>[k,'ENVIRONMENT_DEFAULT'])),secretsConfigured:category==='PAYPAL'?{clientSecret:false}:category==='TWILIO'?{authToken:false}:category==='SMTP'?{password:false}:{},state:'NOT_CONFIGURED',configured:false,enabled:false,validationStatus:'NOT_TESTED',returnUrl:'https://restaurant.example.test/#/order-confirmation',pendingPayments:2,smsVerificationConfigured:false,smsSendingConfigured:false};
 const realFetch=window.fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return realFetch(url,options);
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});
  if(url==='/api/identity/refresh')return Response.json({accessToken:'test',expiresAt:new Date(Date.now()+3600000).toISOString(),user:{username:'Owner',role:window.role}});
  if(url==='/api/staff/restaurant/configuration/audit')return Response.json(window.audit);
  if(url==='/api/staff/restaurant/configuration')return Response.json(window.configuration);
  if(url.startsWith('/api/staff/restaurant/configuration/')){
   const body=JSON.parse(options.body);window.writes.push({url,body,headers:options.headers});
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
 await category('PayPal');await input('clientId','sandbox-test-merchant');await input('clientSecret','browser-test-secret');await click('Save paypal');await waitFor("document.body.innerText.includes('Configuration saved')");
 assert.equal(await evaluate("document.querySelector('[name=clientSecret]')===null"),true);
 assert.equal(await evaluate("document.body.innerText.includes('browser-test-secret')"),false);
 assert.equal(await evaluate("JSON.stringify(window.configuration).includes('browser-test-secret')"),false);
 await click('Replace client secret');assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'');
 await input('clientSecret','replacement-test-secret');await click('Save paypal');await waitFor("!document.querySelector('[name=clientSecret]')");
 await click('Test connection');await waitFor("document.body.innerText.includes('VALID: Connection validation succeeded')");
 await click('Replace client secret');await input('clientSecret','unsaved-secret');await evaluate('window.stale=true');await click('Save paypal');await waitFor("document.body.innerText.includes('Configuration changed')");assert.equal(await evaluate("document.querySelector('[name=clientSecret]').value"),'');
 await click('Refresh configuration');await waitFor("!document.querySelector('[name=clientSecret]')");
 await category('PayID');await input('identifier','restaurant@example.test');await input('accountName','Restaurant Test');await click('Save payid');await waitFor("document.body.innerText.includes('Configuration saved')");
 assert.equal(await evaluate("window.writes.at(-1).body.acknowledgePendingPayments"),true);
 assert.equal(await evaluate("window.configuration.PAYID.fields.accountName"),'Restaurant Test');
 await click('Business settings');await evaluate("document.querySelector('[name=payidEnabled]').click()");await click('Save business settings');await waitFor("document.body.innerText.includes('Configuration saved')");assert.equal(await evaluate("window.configuration.SETTINGS.fields.payidEnabled"),'true');
 assert.equal(await evaluate("document.body.innerText.includes('PAYID_CONFIGURATION_UPDATED')"),true);
 assert.equal(await evaluate("window.writes.every(r=>r.headers['X-CSRF-TOKEN']==='test')"),true);
 // Same route guards FOH: no settings panel or admin navigation is rendered.
 await evaluate("window.role='FOH';location.hash='#/staff';location.hash='#/staff/settings'");
 // Auth context refresh requires a new document; overriding fixture defaults after installation.
 await send('Page.addScriptToEvaluateOnNewDocument',{source:"window.role='FOH'"});await send('Page.reload');await waitFor("document.body.innerText.includes('Access restricted')");
 assert.equal(await evaluate("document.querySelector('a[href=\"#/staff/settings\"]')===null"),true);
 assert.deepEqual(errors,[]);console.log('Admin settings browser: metadata, dependency warnings, write-only replacement, stale conflict, tests, PayID warning, business toggle, audit and ADMIN access passed.');
}finally{await send('Page.close');ws.close();}
