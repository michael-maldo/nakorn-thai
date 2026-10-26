// Prototype-local adaptation of the existing Staff Web regression suite. APIs are mocked.
/** Uses mocked APIs, Vite on 5174 and Chromium remote debugging on 9223.
 * No application database is touched. */
import assert from 'node:assert/strict';
import { staffSessionFixture } from '../../../tests/e2e/staffIdentityFixtures.mjs';
const base = process.env.ORDERS_TEST_URL || 'http://127.0.0.1:5175/staff-pwa-proto/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9225';
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
  let result;try { result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true }); } catch(error) { throw new Error(`Evaluation failed: ${expression} ${JSON.stringify(error)}`); }
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 150; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 40));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}\nFocus: ${await evaluate('document.activeElement?.outerHTML.slice(0,300)')}`);
};
const click = async text => {
  const button = `Array.from(document.querySelectorAll('button')).find(b => b.textContent.trim() === ${JSON.stringify(text)} && !b.disabled)`;
  await waitFor(button); await evaluate(`${button}.click()`);
  await evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
};
function fixture() {
  const role = new URLSearchParams(location.search).get('role') || 'ADMIN';
  window.session = staffSessionFixture({ role });
  window.orders = ['A','B'].map(id => ({ id, reference: id, version: 1, status: 'NEW', createdAt: '2026-10-13T01:00:00Z', customerName: 'Guest '+id, phone: '0412345678', items: [], totalMinor: 2400, paymentMethod: id === 'A' ? 'PAY_AT_RESTAURANT' : 'PAYID' }));
  window.holdReads=false;window.reads=[];window.writes=[];window.requests=[];
  const realFetch=fetch;
  window.fetch=async(url,options={})=>{
    if(!String(url).startsWith('/api'))return realFetch(url,options);
    requests.push({url,options});
    if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'csrf'});
    if(url==='/api/identity/refresh')return session?Response.json(session):new Response(null,{status:401});
    if(url==='/api/identity/logout')return new Response(null,{status:204});
    if(options.method==='PATCH'||options.method==='POST')return new Promise(resolve=>writes.push({url,options,resolve}));
    if(url.startsWith('/api/staff/'))return holdReads?new Promise(resolve=>reads.push({url,options,resolve})):Response.json(orders);
    throw new Error('Unexpected fixture '+url);
  };
  window.respond=(kind,index,status,body)=>window[kind][index].resolve(status===204?new Response(null,{status}):Response.json(body,{status}));
  const realSet=setTimeout,realClear=clearTimeout;let timerId=-1;const polls=new Map();
  window.setTimeout=(fn,delay,...args)=>delay===15000?(polls.set(--timerId,fn),timerId):realSet(fn,delay,...args);
  window.clearTimeout=id=>polls.has(id)?polls.delete(id):realClear(id);
  window.pollOrders=()=>{const callbacks=[...polls.values()];polls.clear();callbacks.forEach(fn=>fn());};
}
const frame=()=>evaluate('new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)))');
const card=id=>`document.querySelector('[data-order-id="${id}"]')`;
const text=id=>`${card(id)}?.innerText`;
const button=(id,label)=>`[...${card(id)}.querySelectorAll('button')].find(b=>b.textContent===${JSON.stringify(label)})`;
const press=async(id,label)=>{await waitFor(`${button(id,label)} && !${button(id,label)}.disabled && !${button(id,label)}.closest('fieldset:disabled')`);await evaluate(`${button(id,label)}.click()`);await frame();};
const input=async(id,name,value)=>{
  const selector=`[data-order-id="${id}"] [name="${name}"]`;
  await evaluate(`(()=>{const e=document.querySelector(${JSON.stringify(selector)});e.focus();Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(e,${JSON.stringify(value)});e.dispatchEvent(new Event('input',{bubbles:true}));})()`);await waitFor(`document.querySelector(${JSON.stringify(selector)}).value===${JSON.stringify(value)}`);
};
const check=async(id,name)=>{await evaluate(`${card(id)}.querySelector('[name=${name}]').click()`);await frame();};
const respond=async(kind,index,status,body)=>{await evaluate(`respond('${kind}',${index},${status},${JSON.stringify(body)})`);await frame();};
const startRead=async()=>{const index=await evaluate('reads.length');await click('Refresh');await waitFor(`reads.length===${index+1}`);return index;};
const finishRead=async(index,rows)=>{await respond('reads',index,200,rows);await waitFor("!document.body.innerText.includes('Refreshing orders…') && !document.body.innerText.includes('Loading orders…')");};
const rotate=async(token,updates='')=>{
 await evaluate(`(async()=>{session=staffSessionFixture({id:session.user.id,role:session.user.role,tokenId:'${token}',${updates}});await(await import('/src/staff-pwa-proto/features/identity/api/identityApi.js')).refreshAccess();})()`);
};
const seed=async(role='ADMIN')=>{
 await send('Page.navigate',{url:'about:blank'});await send('Page.navigate',{url:`${base}?role=${role}#/staff/orders`});
 await waitFor("document.querySelectorAll('.staff-order-card').length===2 && !document.body.innerText.includes('Loading orders…')");
 await evaluate('holdReads=true');
};
try {
 await send('Page.enable');await send('Emulation.setDeviceMetricsOverride',{width:1360,height:1000,deviceScaleFactor:1,mobile:false});await send('Runtime.enable');await send('Page.bringToFront');
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`window.staffSessionFixture=(${staffSessionFixture.toString()});(${fixture.toString()})()`});
 await seed();
 let rows=await evaluate('orders');
 // Two independent drafts; polling, unchanged refresh and reordering preserve DOM identity/focus.
 await press('A','Accept order');await input('A','pickupMinutes','37');
 await input('B','bankReference','BANK-DRAFT');await check('B','bankReceiptChecked');
 await evaluate(`${card('B')}.querySelector('[name=bankReference]').focus();window.originalInput=${card('B')}.querySelector('[name=bankReference]');originalInput.setSelectionRange(2,5)`);
 await evaluate('pollOrders()');await waitFor('reads.length===1');await finishRead(0,[rows[1],rows[0]]);
 assert.equal(await evaluate("document.activeElement===originalInput && originalInput.selectionStart===2 && originalInput.selectionEnd===5"),true);
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=pickupMinutes]').value`),'37');
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReference]').value`),'BANK-DRAFT');
 // A writes while B remains editable/focused; duplicate submissions synchronously prevented.
 await evaluate(`(()=>{const f=${card('A')}.querySelector('.staff-order-action');f.requestSubmit();f.requestSubmit();${card('B')}.querySelector('[name=bankReference]').focus();})()`);
 await waitFor('writes.length===1');await input('B','bankReference','BANK-STILL-EDITING');
 assert.equal(await evaluate(`${card('B')}.querySelector('fieldset').disabled`),false);
 assert.equal(await evaluate("writes[0].options.headers['X-CSRF-TOKEN']"),'csrf');
 assert.deepEqual(await evaluate('JSON.parse(writes[0].options.body)'),{version:1,status:'ACCEPTED',pickupMinutes:37,paymentCollected:false});
 await respond('writes',0,204);await waitFor('reads.length===2');
 rows=[{...rows[0],status:'ACCEPTED',version:2},rows[1]];
 await finishRead(1,rows);
 assert.equal(await evaluate('document.activeElement===originalInput'),true);
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReference]').value`),'BANK-STILL-EDITING');
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=pickupMinutes]')===null`),true);
 // Version change retains the draft, blocks it, and requires explicit discard/rebase.
 let index=await startRead();rows=[rows[0],{...rows[1],version:2}];await finishRead(index,rows);
 assert.match(await evaluate(text('B')),/Order changed since this payment draft/);
 assert.equal(await evaluate(button('B','Confirm PayID payment')+'.disabled'),true);
 assert.equal(await evaluate('document.activeElement===originalInput'),true);
 await press('B','Discard payment draft and use latest order');
 assert.equal(await evaluate(`document.activeElement===${card('B')}.querySelector('h2')`),true);
 await input('B','bankReference','LATEST-BANK');await check('B','bankReceiptChecked');
 await press('B','Confirm PayID payment');await waitFor('writes.length===2');
 assert.equal(await evaluate('JSON.parse(writes[1].options.body).version'),2);
 await respond('writes',1,503,{message:'Bank service failed'});await waitFor('reads.length===4');await finishRead(3,rows);
 assert.match(await evaluate(text('B')),/Bank service failed/);
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReference]').value`),'LATEST-BANK');
 // Successful payment followed by failed reload is distinct; block a repeat write until refreshed.
 await press('B','Confirm PayID payment');await waitFor('writes.length===3');await respond('writes',2,200,{paid:true});await waitFor('reads.length===5');
 await respond('reads',4,503,{message:'Queue reload failed'});await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Queue reload failed')");
 assert.match(await evaluate(text('B')),/Payment verified/);assert.match(await evaluate(text('B')),/earlier load/);
 assert.equal(await evaluate(`${card('B')}.querySelector('fieldset').disabled`),true);
 index=await startRead();rows=[rows[0],{...rows[1],version:3,paidAt:'2026-10-13T02:00:00Z'}];await finishRead(index,rows);
 // Status draft conflicts and failed writes keep the original version and reason.
 await press('A','Cancel order');await input('A','reason','Keep this reason');
 index=await startRead();rows=[{...rows[0],version:3},rows[1]];await finishRead(index,rows);
 assert.match(await evaluate(text('A')),/Order changed since this status draft/);
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=reason]').value`),'Keep this reason');
 await press('A','Discard status draft and use latest order');await press('A','Cancel order');await input('A','reason','Retry reason');
 await press('A','Confirm cancel order');await waitFor('writes.length===4');assert.equal(await evaluate('JSON.parse(writes[3].options.body).version'),3);
 await respond('writes',3,409,{message:'Order changed; reload the queue'});await waitFor('reads.length===8');await finishRead(7,rows);
 assert.match(await evaluate(text('A')),/Order changed since this status draft/);
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=reason]').value`),'Retry reason');
 // Missing dirty order stays as a labelled, unsaveable draft until deliberately discarded.
 index=await startRead();await finishRead(index,[rows[1]]);
 assert.match(await evaluate(text('A')),/no longer in this queue/);
 assert.equal(await evaluate(`${card('A')}.querySelector('fieldset').disabled`),true);
 await press('A','Discard drafts for this order');await waitFor(`${card('A')}===null`);
 // Rapid refresh clicks coalesce, and a token change invalidates the serialized older response.
 await evaluate("(()=>{const b=[...document.querySelectorAll('button')].find(b=>b.textContent==='Refresh');b.click();b.click();b.click();})()");await waitFor('reads.length===10');
 await rotate('rotated');await respond('reads',9,200,[{...rows[1],customerName:'Obsolete response'}]);await waitFor('reads.length===11');
 assert.equal(await evaluate("document.body.innerText.includes('Obsolete response')"),false);
 await finishRead(10,[rows[1]]);
 // Filtering hides cards without dropping drafts; queue change retains only labelled unsaved work.
 await press('B','Cancel order');await input('B','reason','Filtered draft');
 await evaluate("(()=>{const s=[...document.querySelectorAll('select')].find(e=>e.options[0].value==='ALL');s.value='ACCEPTED';s.dispatchEvent(new Event('change',{bubbles:true}));})()");await frame();
 assert.equal(await evaluate(`${card('B')}.hidden`),true);
 await evaluate("(()=>{const s=[...document.querySelectorAll('select')].find(e=>e.options[0].value==='ALL');s.value='ALL';s.dispatchEvent(new Event('change',{bubbles:true}));})()");await frame();
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=reason]').value`),'Filtered draft');
 await evaluate("(()=>{const s=[...document.querySelectorAll('select')].find(e=>e.options[0].value==='active');s.value='history';s.dispatchEvent(new Event('change',{bubbles:true}));})()");await waitFor('reads.length===12');
 await finishRead(11,[]);assert.match(await evaluate(text('B')),/no longer in this queue/);
 // Successful status write + failed reload keeps other drafts/focus and a distinct saved outcome.
 await seed();let workflowRows=await evaluate('orders');
 await input('B','bankReference','Other order draft');
 await press('A','Cancel order');await input('A','reason','No stock');
 await press('A','Confirm cancel order');await waitFor('writes.length===1');
 await evaluate(`${card('B')}.querySelector('[name=bankReference]').focus();window.otherInput=${card('B')}.querySelector('[name=bankReference]');true`);
 await respond('writes',0,204);await waitFor('reads.length===1');await respond('reads',0,503,{message:'Status reload failed'});
 await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Status reload failed')");
 assert.match(await evaluate(text('A')),/Order #A: Cancelled/);
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=reason]')===null`),true);
 assert.equal(await evaluate(`${card('A')}.querySelector('fieldset').disabled`),true);
 assert.equal(await evaluate('document.activeElement===otherInput'),true);
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReference]').value`),'Other order draft');
 index=await startRead();await finishRead(index,[workflowRows[1]]);
 assert.equal(await evaluate(`${card('A')}===null`),true);assert.match(await evaluate('document.body.innerText'),/Order #A: Cancelled/);
 // Conflict plus failed reload cannot discard/rebase against a known stale snapshot.
 await seed();workflowRows=await evaluate('orders');
 await press('A','Cancel order');await input('A','reason','Preserved conflict');
 await press('A','Confirm cancel order');await waitFor('writes.length===1');
 await respond('writes',0,409,{message:'Order changed; reload the queue'});await waitFor('reads.length===1');
 await respond('reads',0,503,{message:'Conflict reload failed'});await waitFor("document.body.innerText.includes('Conflict reload failed')");
 assert.equal(await evaluate(`${card('A')}.querySelector('fieldset').disabled`),true);
 assert.equal(await evaluate(`${card('A')}.querySelector('[name=reason]').value`),'Preserved conflict');
 index=await startRead();workflowRows=[{...workflowRows[0],version:2},workflowRows[1]];await finishRead(index,workflowRows);
 await press('A','Discard status draft and use latest order');await press('A','Cancel order');await input('A','reason','Reconciled reason');
 await press('A','Confirm cancel order');await waitFor('writes.length===2');assert.equal(await evaluate('JSON.parse(writes[1].options.body).version'),2);
 await respond('writes',1,204);await waitFor('reads.length===3');await finishRead(2,[workflowRows[1]]);
 // Dirty status + payment drafts on one order remain independent across token rotation.
 await seed();await press('B','Cancel order');await input('B','reason','Independent status draft');
 await input('B','bankReference','Independent payment draft');await check('B','bankReceiptChecked');
 index=await startRead();await rotate('same-principal');await respond('reads',index,503,{message:'Obsolete error'});await waitFor('reads.length===2');
 assert.equal(await evaluate("document.body.innerText.includes('Obsolete error')"),false);
 await respond('reads',1,503,{message:'Rotation reload failed'});await waitFor("document.body.innerText.includes('Rotation reload failed')");
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=reason]').value`),'Independent status draft');
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReference]').value`),'Independent payment draft');
 assert.match(await evaluate('document.body.innerText'),/earlier load and may be out of date/);
 // Handover checkbox and PayPal verification keep their existing API contracts/payment gate.
 await seed('FOH');workflowRows=await evaluate('orders');index=await startRead();
 workflowRows=[{...workflowRows[0],status:'READY'}, {...workflowRows[1],paymentMethod:'PAYPAL'}];await finishRead(index,workflowRows);
 await press('A','Complete handover');await check('A','paymentCollected');
 await press('A','Confirm complete handover');await waitFor('writes.length===1');
 assert.deepEqual(await evaluate('JSON.parse(writes[0].options.body)'),{version:1,status:'COMPLETED',paymentCollected:true});
 await respond('writes',0,204);await waitFor('reads.length===2');await finishRead(1,[workflowRows[1]]);
 assert.equal(await evaluate(button('B','Accept order')+'.disabled'),true);
 await press('B','Check PayPal payment');await waitFor('writes.length===2');
 assert.equal(await evaluate('writes[1].url'),'/api/staff/payments/B/check');assert.deepEqual(await evaluate('JSON.parse(writes[1].options.body)'),{});
 await respond('writes',1,200,{paid:false});await waitFor('reads.length===3');await finishRead(2,[workflowRows[1]]);
 assert.match(await evaluate(text('B')),/Payment is not yet completed/);
 // A mutation queued behind a read must never execute after sign-out.
 await seed();await press('A','Cancel order');await input('A','reason','Queued private action');index=await startRead();
 await press('A','Confirm cancel order');await waitFor(`${card('A')}.querySelector('fieldset').disabled`);
 const csrfBefore=await evaluate("requests.filter(r=>r.url==='/api/staff/orders/csrf').length");
 await click('Sign out');await waitFor("document.querySelector('[name=password]')");await respond('reads',index,200,await evaluate('orders'));
 // A read queued after the mutation proves earlier serialized work has settled.
 await evaluate("(async()=>{window.queueBarrier=(await import('/src/staff-pwa-proto/features/ordering/api/orderApi.js')).getStaffOrders('',false);})()");
 await waitFor('reads.length===2');await respond('reads',1,200,[]);await evaluate('queueBarrier');
 assert.equal(await evaluate('writes.length'),0);
 assert.equal(await evaluate("requests.filter(r=>r.url==='/api/staff/orders/csrf').length"),csrfBefore);
 // A deliberate pointer blur while saving must not be overridden by completion/reload.
 await seed();await press('A','Cancel order');await input('A','reason','Pointer case');
 await press('A','Confirm cancel order');await waitFor('writes.length===1');
 await evaluate(`${card('B')}.querySelector('[name=bankReference]').focus({preventScroll:true})`);
 const point=await evaluate("(()=>{const p=document.querySelector('.proto-identity');p.scrollIntoView({block:'center',behavior:'instant'});const r=p.getBoundingClientRect();return {x:r.x+20,y:r.y+10};})()");
 await send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,...point});
 await send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,...point});
 await waitFor('document.activeElement===document.body');
 await respond('writes',0,204);await waitFor('reads.length===1');
 assert.equal(await evaluate('document.activeElement===document.body'),true);
 await finishRead(0,await evaluate('orders'));
 assert.equal(await evaluate('document.activeElement===document.body'),true);
 // Keyboard navigation to another editor during a write remains deliberate focus.
 await seed();await press('A','Cancel order');await input('A','reason','Keyboard case');
 await press('A','Confirm cancel order');await waitFor('writes.length===1');
 await evaluate(`${card('B')}.querySelector('[name=bankReference]').focus()`);
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});
 await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Tab',code:'Tab',windowsVirtualKeyCode:9});
 await evaluate('window.keyboardFocus=document.activeElement;true');
 assert.equal(await evaluate(`${card('B')}.querySelector('[name=bankReceiptChecked]')===keyboardFocus`),true);
 await respond('writes',0,204);await waitFor('reads.length===1');await finishRead(0,await evaluate('orders'));
 assert.equal(await evaluate('document.activeElement===keyboardFocus'),true);
 // Removing a focused editor with no intervening intent falls back to its own card heading.
 await seed();let focusRows=await evaluate('orders');
 await evaluate(`${card('B')}.querySelector('[name=bankReference]').focus()`);
 index=await startRead();await finishRead(index,[focusRows[0],{...focusRows[1],version:2,paidAt:'2026-10-13T02:00:00Z'}]);
 assert.equal(await evaluate(`document.activeElement===${card('B')}.querySelector('h2')`),true);
 // A disappearing dirty order has a heading fallback; explicit discard removes it safely.
 await seed();await input('B','bankReference','Disappearing editor');index=await startRead();
 await finishRead(index,[await evaluate('orders[0]')]);
 assert.equal(await evaluate(`document.activeElement===${card('B')}.querySelector('h2')`),true);
 await press('B','Discard drafts for this order');await waitFor(`${card('B')}===null`);
 assert.equal(await evaluate("document.activeElement===document.querySelector('main h1')"),true);
 // Replacing even the same user's session clears drafts without taking external focus.
 await seed();await input('B','bankReference','Private session draft');index=await startRead();
 await evaluate("window.externalFocus=[...document.querySelectorAll('button')].find(b=>b.textContent==='Sign out');externalFocus.focus();true");
 await rotate('new-session',"sessionId:'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'");
 await respond('reads',index,200,await evaluate('orders'));await waitFor('reads.length===2');
 assert.equal(await evaluate("document.querySelector('[name=bankReference]')?.value==='Private session draft'"),false);
 assert.equal(await evaluate('document.activeElement===externalFocus'),true);
 await respond('reads',1,200,await evaluate('orders'));await waitFor('reads.length===3');await finishRead(2,await evaluate('orders'));
 assert.equal(await evaluate('document.activeElement===externalFocus'),true);
 // Navigation during an outstanding read never restores focus to the former workspace.
 await seed();await input('B','bankReference','Unmounted editor');index=await startRead();
 await evaluate("location.hash='#/staff'");await waitFor("!document.querySelector('.staff-order-card')");
 await evaluate("window.navFocus=[...document.querySelectorAll('button')].find(b=>b.textContent==='Sign out');navFocus.focus();true");
 await respond('reads',index,200,await evaluate('orders'));
 assert.equal(await evaluate('document.activeElement===navFocus'),true);
 // Principal/role change and session failure destroy protected drafts and ignore old callbacks.
 await seed();await press('A','Cancel order');await input('A','reason','Private draft');index=await startRead();
 await rotate('other-user',"id:'22222222-2222-4222-8222-222222222222'");await respond('reads',index,200,[{...rows[0],customerName:'Old principal'}]);await waitFor('reads.length===2');
 assert.equal(await evaluate("document.body.innerText.includes('Old principal') || document.querySelector('[name=reason]')?.value==='Private draft'"),false);
 await respond('reads',1,200,await evaluate('orders'));await waitFor('reads.length===3');await finishRead(2,await evaluate('orders'));
 await press('A','Cancel order');await input('A','reason','Role draft');
 await rotate('kitchen-token',"role:'BOH'");await waitFor('reads.length===4');
 assert.equal(await evaluate("document.querySelector('[name=reason]')"),null);
 await respond('reads',3,200,[]);await waitFor('reads.length===5');await finishRead(4,[{...rows[0],status:'ACCEPTED'},rows[1]]);
 assert.equal(await evaluate("document.querySelector('[name=bankReference]')"),null);
 await press('A','Start preparing');await press('A','Confirm start preparing');await waitFor('writes.length===1');
 assert.equal(await evaluate('JSON.parse(writes[0].options.body).status'),'PREPARING');
 await respond('writes',0,204);await waitFor('reads.length===6');await finishRead(5,[{...rows[0],status:'PREPARING',version:4}]);
 await evaluate("(async()=>{session=null;await(await import('/src/staff-pwa-proto/features/identity/api/identityApi.js')).refreshAccess().catch(()=>{});})()");await waitFor("document.querySelector('[name=password]')");
 assert.equal(await evaluate("document.querySelectorAll('.staff-order-card').length"),0);
 await seed('FOH');await press('A','Accept order');await input('A','pickupMinutes','25');index=await startRead();await click('Sign out');await waitFor("document.querySelector('[name=password]')");
 await respond('reads',index,200,await evaluate('orders'));assert.equal(await evaluate("document.querySelectorAll('.staff-order-card').length"),0);
 await seed();await press('A','Cancel order');await input('A','reason','Unmount draft');index=await startRead();await evaluate("location.hash='#/staff'");await waitFor("!document.querySelector('.staff-order-card')");await respond('reads',index,503,{message:'Unmount error'});
 assert.equal(await evaluate("document.body.innerText.includes('Unmount error')"),false);
 assert.deepEqual(errors,[]);
 console.log('PASS: simultaneous drafts, polling/refresh/reorder focus, per-order writes, original versions/conflicts/discard, read/write failures, disappearance, duplicate/stale requests, filtering, queue changes, principal/role/session cleanup and existing PayID/status contracts.');
} finally {if(ws.readyState===WebSocket.OPEN)await send('Page.close').catch(()=>{});ws.close();}
