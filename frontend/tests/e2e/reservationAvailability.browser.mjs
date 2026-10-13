/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
const base = process.env.RESERVATION_TEST_URL || 'http://127.0.0.1:5174/';
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
 const role=new URLSearchParams(location.search).get('role')||'ADMIN';
 window.availabilityCalls=[];window.bookingCalls=[];window.patchCalls=[];
 window.mockSession={accessToken:'test',expiresAt:new Date(Date.now()+3600000).toISOString(),user:{id:'staff-one',username:'Staff',role,enabled:true}};
 const original=fetch;
 window.fetch=async(url,options={})=>{
  if(!String(url).startsWith('/api'))return original(url,options);
  if(url==='/api/identity/refresh')return role==='ANON'||!window.mockSession?new Response(null,{status:401}):Response.json(window.mockSession);
  if(url==='/api/identity/logout')return new Response(null,{status:204});
  if(url==='/api/restaurant/availability')return new Promise(resolve=>availabilityCalls.push({resolve}));
  if(String(url).startsWith('/api/staff/reservations?'))return new Promise(resolve=>bookingCalls.push({resolve,url,options}));
  if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'csrf'});
  if(options.method==='PATCH'){patchCalls.push(options);return new Response(null,{status:204});}
  throw new Error('Unexpected API '+url);
 };
 window.respond=(kind,index,status,body)=>window[kind+'Calls'][index].resolve(Response.json(body,{status}));
 window.row=name=>({id:'booking',version:1,requestedAt:'2026-10-13T19:00:00',customerName:name,partySize:2,status:'REQUESTED',staffNote:''});
}
const frame=()=>evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
const respond=async(kind,index,status,body)=>{await evaluate(`respond('${kind}',${index},${status},${JSON.stringify(body)})`);await frame();};
const empty=()=>evaluate("document.body.innerText.includes('No booking requests')");
const navigate=async(role='ADMIN')=>{
 await send('Page.navigate',{url:'about:blank'});await send('Page.navigate',{url:`${base}?role=${role}#/staff/reservations`});await waitFor("document.querySelector('h1')");await frame();
};
const choose=async(date)=>{
 await evaluate(`(()=>{const e=document.querySelector('input[type=date]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,'${date}');e.dispatchEvent(new Event('input',{bubbles:true}));})()`);await frame();
};
try{
 await send('Page.enable');await send('Runtime.enable');await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${fixture.toString()})()`});
 await navigate();await waitFor('availabilityCalls.length===2'); // StrictMode starts a discarded mount request.
 assert.equal(await empty(),false);assert.equal(await evaluate('bookingCalls.length'),0);
 assert.match(await evaluate('document.body.innerText'),/Loading reservation availability/);
 await respond('availability',1,503,{message:'Schedule offline'});
 await waitFor("document.querySelector('[role=alert]') && document.body.innerText.includes('Retry availability')");
 assert.equal(await empty(),false);
 assert.equal(await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='Refresh bookings').disabled"),true);
 // Same-frame clicks exercise the synchronous duplicate-request guard.
 await evaluate("(()=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent==='Retry availability');b.click();b.click();b.click();})()");await frame();
 assert.equal(await evaluate('availabilityCalls.length'),3);
 await respond('availability',2,503,{message:'Still offline'});await waitFor("document.body.innerText.includes('Still offline')");
 await click('Retry availability');await waitFor('availabilityCalls.length===4');
 await respond('availability',3,200,{timezone:'Australia/Melbourne'});await waitFor('bookingCalls.length===1');
 assert.match(await evaluate("document.querySelector('input[type=date]').value"),/^\d{4}-\d{2}-\d{2}$/);
 assert.equal(await empty(),false);
 await respond('availability',0,503,{message:'Stale availability'});
 assert.equal(await evaluate("document.body.innerText.includes('Stale availability')"),false);
 await respond('booking',0,200,[]);assert.equal(await empty(),true);
 await choose('2026-11-21');await waitFor('bookingCalls.length===2');
 await respond('booking',1,503,{message:'Bookings offline'});await waitFor("document.body.innerText.includes('Booking requests could not be loaded')");
 assert.equal(await empty(),false);assert.equal(await evaluate("document.body.innerText.includes('Retry availability')"),false);
 await click('Refresh bookings');await waitFor('bookingCalls.length===3');
 assert.equal(await evaluate('availabilityCalls.length'),4);
 assert.equal(await evaluate("document.querySelector('input[type=date]').value"),'2026-11-21');
 assert.match(await evaluate('bookingCalls[2].url'),/date=2026-11-21$/);
 await respond('booking',2,200,[await evaluate("row('Current guest')")]);
 await click('Refresh bookings');await waitFor('bookingCalls.length===4');
 await respond('booking',3,503,{message:'Read failed'});
 assert.equal(await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='CONFIRMED').disabled"),false);
 await click('CONFIRMED');await waitFor('patchCalls.length===1 && bookingCalls.length===5');
 assert.equal(await evaluate("patchCalls[0].headers['X-CSRF-TOKEN']"),'csrf');
 assert.equal(await evaluate('JSON.parse(patchCalls[0].body).version'),1);
 await respond('booking',4,200,[]);
 await choose('2026-11-22');await waitFor('bookingCalls.length===6');
 await choose('2026-11-23');await waitFor('bookingCalls.length===7');
 await respond('booking',6,200,[await evaluate("row('Newest guest')")]);
 await respond('booking',5,200,[await evaluate("row('Stale guest')")]);
 assert.match(await evaluate('document.body.innerText'),/Newest guest/);assert.equal(await evaluate("document.body.innerText.includes('Stale guest')"),false);
 await choose('2026-11-24');await waitFor('bookingCalls.length===8');await choose('2026-11-25');await waitFor('bookingCalls.length===9');
 await respond('booking',7,503,{message:'Stale booking error'});assert.match(await evaluate('document.body.innerText'),/Loading bookings/);
 await respond('booking',8,200,[]);
 await click('Refresh bookings');await waitFor('bookingCalls.length===10');
 await evaluate("location.hash='#/staff'");await waitFor("!document.querySelector('input[type=date]')");
 await respond('booking',9,503,{message:'Unmounted booking error'});assert.equal(await evaluate("document.body.innerText.includes('Unmounted booking error')"),false);
 await navigate('FOH');await waitFor('availabilityCalls.length===2');
 await respond('availability',1,200,{timezone:'Australia/Melbourne'});await waitFor('bookingCalls.length===1');
 assert.equal(await evaluate('bookingCalls[0].options.headers.Authorization'),'Bearer test');
 await respond('booking',0,200,[]);assert.equal(await empty(),true);
 await respond('availability',0,200,{timezone:'Australia/Perth'});
 assert.match(await evaluate('document.body.innerText'),/Restaurant date \(Australia\/Melbourne\)/);
 await navigate();await waitFor('availabilityCalls.length===2');await evaluate("location.hash='#/staff'");await waitFor("!document.querySelector('input[type=date]')");
 await respond('availability',1,200,{timezone:'Australia/Melbourne'});assert.equal(await evaluate('bookingCalls.length'),0);
 for(const role of ['BOH','ANON']){
  await navigate(role);await waitFor(role==='BOH'?"document.body.innerText.includes('Access restricted')":"document.querySelector('input[type=password]')");assert.equal(await evaluate('availabilityCalls.length+bookingCalls.length'),0);
 }
 // Identity transitions use the real identity publisher and AuthProvider with mocked HTTP.
 const identity=async(expression)=>{await evaluate(`(async()=>{const api=await import('/src/domains/identity/api/identityApi.js');${expression}})()`);};
 const rotate=async(token,updates='')=>identity(`mockSession={...mockSession,accessToken:'${token}',user:{...mockSession.user,${updates}}};await api.refreshAccess();`);
 const seed=async(name)=>{
  await navigate();await waitFor('availabilityCalls.length===2');
  await respond('availability',1,200,{timezone:'Australia/Melbourne'});await waitFor('bookingCalls.length===1');
  await respond('booking',0,200,[await evaluate(`row('${name}')`)]);
  await waitFor(`document.body.innerText.includes('${name}')`);
 };
 await seed('Retained guest');
 const selected=await evaluate("document.querySelector('input[type=date]').value");
 // A: token rotation retains rows while loading, then replaces them authoritatively.
 await rotate('rotated-one');await waitFor('bookingCalls.length===2');
 await waitFor("document.body.innerText.includes('Refreshing bookings')");
 assert.match(await evaluate('document.body.innerText'),/Retained guest/);
 assert.equal(await evaluate("document.querySelector('input[type=date]').value"),selected);
 assert.equal(await evaluate('bookingCalls[1].options.headers.Authorization'),'Bearer rotated-one');
 await respond('booking',1,200,[await evaluate("row('Refreshed guest')")]);
 await waitFor("document.body.innerText.includes('Refreshed guest') && !document.body.innerText.includes('Refreshing bookings')");
 assert.equal(await evaluate("document.body.innerText.includes('Retained guest')"),false);
 // B: failed same-principal reload retains data and explicitly marks it as potentially stale.
 await rotate('rotated-two');await waitFor('bookingCalls.length===3');
 await respond('booking',2,503,{message:'Token reload failed'});
 await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Token reload failed')");
 assert.match(await evaluate('document.body.innerText'),/Refreshed guest/);
 assert.match(await evaluate("document.querySelector('[role=alert]').textContent"),/earlier load and may be out of date/);
 assert.equal(await empty(),false);
 // G: PATCH succeeds but its read fails; distinguish saved write from stale display.
 await click('CONFIRMED');await waitFor('patchCalls.length===1 && bookingCalls.length===4');
 await respond('booking',3,503,{message:'Post-write reload failed'});
 await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Post-write reload failed') && !document.body.innerText.includes('Updating booking…')");
 assert.match(await evaluate('document.body.innerText'),/Booking update saved/);
 assert.match(await evaluate('document.body.innerText'),/Refreshed guest/);
 assert.equal(await evaluate("document.querySelectorAll('[role=alert]').length"),1);
 assert.match(await evaluate("document.querySelector('[role=alert]').textContent"),/may be out of date/);
 assert.equal(await evaluate('JSON.parse(patchCalls[0].body).version'),1);
 // F: superseded token reads cannot overwrite a later response or clear its loader.
 await rotate('rotated-three');await waitFor('bookingCalls.length===5');
 await rotate('rotated-four');await waitFor('bookingCalls.length===6');
 await respond('booking',4,503,{message:'Obsolete token error'});
 assert.equal(await evaluate("document.body.innerText.includes('Obsolete token error')"),false);
 assert.match(await evaluate('document.body.innerText'),/Refreshing bookings/);
 await respond('booking',5,200,[await evaluate("row('Latest token guest')")]);
 await waitFor("document.body.innerText.includes('Latest token guest')");
 await rotate('rotated-five');await waitFor('bookingCalls.length===7');
 await rotate('rotated-six');await waitFor('bookingCalls.length===8');
 await respond('booking',7,200,[await evaluate("row('Final token guest')")]);
 await waitFor("document.body.innerText.includes('Final token guest')");
 await respond('booking',6,200,[await evaluate("row('Obsolete token guest')")]);
 assert.equal(await evaluate("document.body.innerText.includes('Obsolete token guest')"),false);
 // C/F: a date change clears previous-date rows even during a token reload.
 await rotate('rotated-seven');await waitFor('bookingCalls.length===9');
 await choose('2026-12-01');await waitFor('bookingCalls.length===10');
 assert.equal(await evaluate("document.querySelectorAll('.order-queue .order-panel').length"),0);
 await respond('booking',8,200,[await evaluate("row('Wrong date guest')")]);
 assert.equal(await evaluate("document.body.innerText.includes('Wrong date guest')"),false);
 await respond('booking',9,200,[await evaluate("row('New date guest')")]);
 await waitFor("document.body.innerText.includes('New date guest')");
 // D: sign-out and failed session refresh unmount protected rows, including pending reads.
 await click('Refresh bookings');await waitFor('bookingCalls.length===11');await click('Sign out');
 await waitFor("document.querySelector('input[type=password]')");
 await respond('booking',10,200,[await evaluate("row('After logout guest')")]);
 assert.equal(await evaluate("document.querySelectorAll('.order-queue .order-panel').length"),0);
 assert.equal(await evaluate("document.body.innerText.includes('After logout guest')"),false);
 await seed('Session guest');
 await click('Refresh bookings');await waitFor('bookingCalls.length===2');
 await identity('mockSession=null;await api.refreshAccess().catch(()=>{});');
 await waitFor("document.querySelector('input[type=password]')");
 await respond('booking',1,200,[await evaluate("row('After expiry guest')")]);
 assert.equal(await evaluate("document.body.innerText.includes('Session guest') || document.body.innerText.includes('After expiry guest')"),false);
 // E: account change and an allowed-role change remount before new data is available.
 for(const [change,label] of [["id:'staff-two'",'Account'],["role:'FOH'",'Role'],["id:undefined",'Unknown identity']]){
  await seed('Protected guest');await click('Refresh bookings');await waitFor('bookingCalls.length===2');
  await rotate('changed-principal',change);await waitFor('availabilityCalls.length===4');
  assert.equal(await evaluate("document.body.innerText.includes('Protected guest')"),false);
  await respond('booking',1,200,[await evaluate("row('Old principal response')")]);
  assert.equal(await evaluate("document.body.innerText.includes('Old principal response')"),false);
  await respond('availability',3,200,{timezone:'Australia/Melbourne'});await waitFor('bookingCalls.length===3');
  await respond('booking',2,200,[await evaluate(`row('${label} guest')`)]);
  await waitFor(`document.body.innerText.includes('${label} guest')`);
  if(label==='Unknown identity'){
   await rotate('unknown-rotated');await waitFor('availabilityCalls.length===6');
   assert.equal(await evaluate("document.body.innerText.includes('Unknown identity guest')"),false);
  }
 }
 for(const change of ["role:'BOH'","enabled:false"]){
  await seed('Restricted guest');await click('Refresh bookings');await waitFor('bookingCalls.length===2');
  await rotate('restricted-token',change);
  await waitFor("!document.querySelector('input[type=date]')");
  await respond('booking',1,200,[await evaluate("row('Restricted stale response')")]);
  assert.equal(await evaluate("document.body.innerText.includes('Restricted guest') || document.body.innerText.includes('Restricted stale response')"),false);
  assert.equal(await evaluate('bookingCalls.length'),2);
 }
 assert.deepEqual(errors,[]);
 console.log('PASS: availability recovery, slow/empty states, separate read/write recovery, date preservation, duplicate retries, stale ordering, unmount, token/principal/session boundaries and post-write reload failure; no runtime exceptions.');
}finally{if(ws.readyState===WebSocket.OPEN)await send('Page.close').catch(()=>{});ws.close();}
