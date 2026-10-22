// Existing repository CDP harness: all staff APIs are synthetic, no printer traffic.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { installPwaFixtures } from './staffPwaFixtures.mjs';
import { staffSessionFixture } from './staffIdentityFixtures.mjs';
const base = process.env.STAFF_LAYOUT_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const artifacts = process.env.STAFF_PWA_ARTIFACTS || '/tmp/nakorn-staff-pwa';
await mkdir(artifacts, {recursive:true});
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0, checks = 0;
const pending = new Map(), errors = [], results = [], audioEvents = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); if (!waiter) return; pending.delete(message.id); clearTimeout(waiter.timeout);
    message.error ? waiter.reject(message.error) : waiter.resolve(message.result);
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
  else if (message.method === 'Runtime.consoleAPICalled' && message.params.args[0]?.value === '[Nakorn Staff audio]') {
    audioEvents.push(message.params.args[1]?.preview?.properties?.find(property => property.name === 'event')?.value);
  }
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence;
  const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
  pending.set(id, { resolve, reject, timeout }); ws.send(JSON.stringify({ id, method, params }));
});
ws.addEventListener('close', () => { for (const waiter of pending.values()) { clearTimeout(waiter.timeout); waiter.reject(new Error('Browser connection closed')); } pending.clear(); });
const evaluate = async (expression, userGesture = true) => {
  const value = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true, userGesture });
  if (value.exceptionDetails) throw new Error(JSON.stringify(value.exceptionDetails));
  return value.result.value;
};
const waitFor = async expression => {
  for (let i = 0; i < 160; i++) {
    if (await evaluate(`Boolean(${expression})`)) return;
    await new Promise(resolve => setTimeout(resolve, 50));
  }
  throw new Error(`Timed out: ${expression}\n${await evaluate('document.body.innerText')}`);
};
const check = (value, message) => { assert.ok(value, message); checks++; };
const click = text => evaluate(`(()=>{const button=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(text)});if(!button)throw Error('Missing button '+${JSON.stringify(text)});button.click();})()`);
const nav = async (width, height, role = 'ADMIN') => {
  await send('Emulation.setDeviceMetricsOverride', {width,height,deviceScaleFactor:2,mobile:true});
  await send('Page.navigate',{url:`${base}staff/?role=${role}`});
  await waitFor(role === 'ANON' ? "document.querySelector('form')" : "document.querySelectorAll('.sp-order-card').length > 0");
};
try {
  await send('Page.enable'); await send('Runtime.enable'); await send('Page.bringToFront');
  await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${installPwaFixtures.toString()})(${staffSessionFixture.toString()});`});
  await send('Page.addScriptToEvaluateOnNewDocument', {source: `
    window.phase2Contexts=[];
    const NativeContext=window.AudioContext;
    window.AudioContext=class extends NativeContext {
      constructor(...args){super(...args);window.phase2Contexts.push(this);}
      resume(){
        if(window.phase2AudioPolicy==='blocked')return Promise.reject(new DOMException('Simulated Safari activation required','NotAllowedError'));
        if(window.phase2AudioPolicy==='hung'){this.phase2ResumePending=true;return new Promise(()=>{});}
        return super.resume();
      }
    };
  `});
  for (const [width,height] of [[810,1080],[1080,810],[1024,768],[768,1024],[390,844],[540,360]]) {
    await nav(width,height);
    check(await evaluate('document.scrollingElement.scrollWidth<=innerWidth'),`${width} no page overflow`);
    check(await evaluate("document.querySelector('.sp-footer').getBoundingClientRect().bottom<=innerHeight+1"),'persistent footer fits viewport');
    check(await evaluate("document.querySelectorAll('.sp-summary').length===4"),'four queue summaries');
    check(await evaluate("!document.querySelector('.sp-sidebar').getBoundingClientRect().width || document.querySelector('.sp-sidebar').getBoundingClientRect().right<innerWidth"),'sidebar fits');
    await click('↻ Refresh');
    await waitFor("!document.querySelector('.sp-page-heading button').disabled");
    await evaluate("document.querySelector('.sp-order-card').click()");
    await waitFor("document.querySelector('dialog[open] .staff-order-card')");
    check(await evaluate("document.querySelector('dialog').getBoundingClientRect().right<=innerWidth"),'dialog fits safe viewport');
    check(await evaluate("document.querySelector('dialog').getBoundingClientRect().height<=innerHeight"),'dialog height fits');
    check(await evaluate("document.querySelector('dialog').textContent.includes('No peanuts')"),'order details retain notes');
    await evaluate("document.querySelector('[aria-label=\"Close dialog\"]').click()");
    if (width >= 900) {
      await evaluate("document.querySelector('.sp-sidebar>button').click()");
      check(await evaluate("document.querySelector('.sp-sidebar').getBoundingClientRect().width===72"),'collapsed rail');
      check(await evaluate("(()=>{const width=document.querySelector('.sp-content').clientWidth-24;return [...document.querySelectorAll('.sp-column')].filter(e=>getComputedStyle(e).display!=='none').length===(width>=960?4:1)})()"),'queue columns adapt to available content width');
      await evaluate("document.querySelector('.sp-sidebar>button').click()");
    } else {
      await evaluate("document.querySelector('[aria-label=\"Open navigation\"]').click()");
      await waitFor("document.querySelector('dialog[open] .sp-drawer')");
      check(await evaluate("document.querySelector('.sp-drawer').textContent.includes('Printer Management')"),'hamburger navigation');
      await evaluate("document.querySelector('[aria-label=\"Close dialog\"]').click()");
    }
    await send('Page.captureScreenshot',{format:'png'}).then(result=>writeFile(`${artifacts}/${width}x${height}.png`,Buffer.from(result.data,'base64')));
  }
  await nav(1080,810);
  // Role-aware, versioned acceptance; test duplicate Confirm events synchronously.
  await evaluate("document.querySelector('.sp-order-card').click()"); await waitFor("document.querySelector('dialog .staff-order-card')");
  await click('Accept order');
  await waitFor("document.querySelector('dialog form.staff-order-action')");
  await evaluate("(()=>{let f=document.querySelector('form.staff-order-action'); f.requestSubmit(); f.requestSubmit();})()");
  await waitFor("pwaFixture.orders[0].status==='ACCEPTED' && document.querySelector('dialog').textContent.includes('Server confirmed')");
  check(await evaluate("pwaFixture.requests.filter(r=>r.method==='PATCH').length===1"),'duplicate acceptance suppressed');
  check(await evaluate("pwaFixture.requests.find(r=>r.method==='PATCH').headers['X-CSRF-TOKEN']==='synthetic'"),'acceptance uses CSRF');
  check(await evaluate("document.querySelector('dialog').textContent.includes('Browser printing disabled')"),'default printing disabled');
  await evaluate("document.querySelector('[aria-label=\"Close dialog\"]').click()");
  // New pending detection and error/reconnect preserve baseline.
  await evaluate("pwaFixture.orders.push({...pwaFixture.orders[0],id:'new-arrival',reference:'NEW-ARRIVAL',status:'NEW'})");
  await click('↻ Refresh'); await waitFor("document.querySelector('.sp-alert')");
  check(await evaluate("document.querySelector('.sp-alert').textContent.includes('1 new pending')"),'one new pending alert');
  await click('↻ Refresh'); await waitFor("!document.querySelector('.sp-page-heading button').disabled");
  check(await evaluate("document.querySelector('.sp-alert').textContent.includes('1 new pending')"),'no duplicate polling alert');
  await evaluate('pwaFixture.queueError=true'); await click('↻ Refresh'); await waitFor("document.querySelector('.sp-error')");
  check(await evaluate("document.querySelector('.sp-monitor').textContent.includes('unavailable')"),'network error status');
  await evaluate('pwaFixture.queueError=false'); await click('↻ Refresh'); await waitFor("!document.querySelector('.sp-error')");
  check(await evaluate("document.querySelector('.sp-alert').textContent.includes('1 new pending')"),'reconnect without duplicate alert');
  await click('Acknowledge'); await waitFor("!document.querySelector('.sp-alert')");
  // Failure blocks replay until an explicit reload.
  await evaluate("pwaFixture.statusError=true;document.querySelector('[aria-label=\"Accepted orders\"] .sp-order-card').click()"); await waitFor("document.querySelector('dialog .staff-order-card')");
  await click('Start preparing'); await evaluate("document.querySelector('form.staff-order-action').requestSubmit()");
  await waitFor("document.querySelector('dialog').textContent.includes('Version conflict')");
  check(await evaluate("document.querySelector('dialog fieldset').disabled"),'failed transition blocks further writes');
  await evaluate("document.querySelector('[aria-label=\"Close dialog\"]').click();location.hash='/settings'");
  await waitFor("document.querySelector('h1')?.textContent==='Settings'");
  check(await evaluate("document.body.textContent.includes('Sound not enabled')"),'sound activation indicator');
  await click('Enable Sound'); await waitFor("document.body.textContent.includes('Sound enabled')");
  // Simulate suspension with a real Chromium visibility transition, not a physical iPad lock.
  const audioCount = () => audioEvents.filter(event => event === 'context-created').length;
  const beforeLock = await audioCount();
  const away = await send('Target.createTarget', {url:'about:blank'});
  try {
    await send('Target.activateTarget', {targetId:away.targetId});
    await waitFor('document.hidden');
    check(await evaluate("document.querySelector('.sp-sound-status').textContent==='Sound not enabled'"),'hide immediately invalidates audio readiness');
    await send('Page.bringToFront'); await waitFor('!document.hidden');
    await waitFor("document.querySelector('.sp-sound-status').textContent==='Sound enabled'");
    check(audioCount()===beforeLock+1,'restoration creates at most one replacement context');
    const restorePlays=audioEvents.filter(event=>event==='play-scheduled').length;
    await evaluate("window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true})); window.dispatchEvent(new Event('focus'))", false);
    await waitFor("document.querySelector('.sp-sound-status').textContent==='Sound enabled'");
    check(audioCount()===beforeLock+1,'pageshow and focus share recovery without repeated context creation');
    check(audioEvents.filter(event=>event==='play-scheduled').length===restorePlays,'restoration emits no diagnostic tones');
    check(await evaluate("!document.querySelector('.sp-alert')"),'unlock does not replay acknowledged pending-order alerts');
    const plays = audioEvents.filter(event => event === 'play-scheduled').length;
    await evaluate("pwaFixture.orders.push({...pwaFixture.orders[0],id:'after-unlock',reference:'AFTER-UNLOCK',status:'NEW'});location.hash='/orders'");
    await waitFor("document.querySelector('.sp-page-heading button') && !document.querySelector('.sp-page-heading button').disabled");
    await click('↻ Refresh'); await waitFor("document.querySelector('.sp-alert')");
    for(let i=0;i<100 && audioEvents.filter(event=>event==='play-scheduled').length<plays+1;i++)await new Promise(resolve=>setTimeout(resolve,20));
    check(audioEvents.filter(event => event === 'play-scheduled').length===plays+1,'new incoming order schedules one sound after recovery');
    await click('↻ Refresh'); await waitFor("!document.querySelector('.sp-page-heading button').disabled");
    check(audioEvents.filter(event => event === 'play-scheduled').length===plays+1,'repeat polling after recovery remains silent');
  } finally { await send('Target.closeTarget', {targetId:away.targetId}); }
  // Model Safari denial and a pending resume explicitly. This does not emulate real iPad audio hardware.
  await evaluate("window.phase2AudioPolicy='blocked'", false);
  await evaluate("window.dispatchEvent(new PageTransitionEvent('pagehide',{persisted:true}));window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true}))", false);
  await waitFor("window.phase2Contexts.at(-1).state==='closed'");
  check(await evaluate("document.querySelector('.sp-monitor button').textContent==='Tap to Enable Sound'"),'blocked recovery keeps prominent manual fallback');
  const blockedCount=audioCount(), blockedPlays=audioEvents.filter(event=>event==='play-scheduled').length;
  await evaluate("window.dispatchEvent(new Event('focus'));pwaFixture.orders.push({...pwaFixture.orders[0],id:'blocked-order',reference:'BLOCKED-ORDER',status:'NEW'})", false);
  await click('↻ Refresh'); await waitFor("document.body.textContent.includes('BLOCKED-ORDER')");
  check(audioCount()===blockedCount,'incoming order does not repeatedly recreate an activation-blocked engine');
  check(audioEvents.filter(event=>event==='play-scheduled').length===blockedPlays,'blocked order stays visible without a false sound');
  await evaluate("window.phase2AudioPolicy='allowed'", false); await click('Tap to Enable Sound');
  await waitFor("document.querySelector('.sp-sound-status').textContent==='Sound enabled'");
  const afterManual=audioEvents.filter(event=>event==='play-scheduled').length;
  await click('↻ Refresh'); await waitFor("!document.querySelector('.sp-page-heading button').disabled");
  check(audioEvents.filter(event=>event==='play-scheduled').length===afterManual,'manual activation does not replay failed notification');
  await evaluate("window.phase2AudioPolicy='hung';window.dispatchEvent(new PageTransitionEvent('pagehide'));window.dispatchEvent(new PageTransitionEvent('pageshow'));pwaFixture.orders.push({...pwaFixture.orders[0],id:'hung-order',reference:'HUNG-ORDER',status:'NEW'})", false);
  await click('↻ Refresh'); await waitFor("document.body.textContent.includes('HUNG-ORDER')");
  check(await evaluate("window.phase2Contexts.at(-1).phase2ResumePending && window.phase2Contexts.at(-1).state!=='closed'"),'queue renders while audio recovery remains pending');
  await evaluate("window.phase2AudioPolicy='allowed'", false); await click('Tap to Enable Sound');
  await waitFor("document.querySelector('.sp-sound-status').textContent==='Sound enabled'");
  check(true,'manual activation preempts hung automatic recovery');
  await evaluate("location.hash='/printers'"); await waitFor("document.querySelector('h1')?.textContent==='Printer Management'");
  check(await evaluate("[...document.querySelectorAll('button')].find(e=>e.textContent==='Test Print').disabled"),'no unverified printing');
  await nav(810,1080,'BOH');
  check(await evaluate("!document.body.textContent.includes('Synthetic guest')"),'kitchen customer privacy');
  await evaluate("location.hash='/printers'"); await waitFor("document.querySelector('h1')?.textContent==='Access restricted'");
  check(true,'printer role restriction');
  await nav(1080,810,'FOH');
  await evaluate("document.querySelector('.sp-order-card').click()"); await waitFor("document.querySelector('dialog .staff-order-card')");
  check(await evaluate("document.querySelector('dialog').textContent.includes('Accept order')"),'FOH may accept');
  check(await evaluate("!document.querySelector('dialog').textContent.includes('Start preparing')"),'FOH cannot prepare');
  // Forced 401 clears protected workspace through existing refresh contract.
  await evaluate("document.querySelector('[aria-label=\"Close dialog\"]').click(); pwaFixture.expire=true");
  await click('↻ Refresh'); await waitFor("document.querySelector('.staff-login-page')");
  check(await evaluate("!document.querySelector('.sp-order-card') && !document.querySelector('dialog[open]')"),'expiration removes protected data');
  await nav(810,1080,'ANON');
  check(await evaluate("document.querySelector('input[type=password]')!==null"),'login shown for anonymous');
  await evaluate("pwaFixture.loginError=true; document.querySelector('[name=username]').value='staff'; document.querySelector('[name=password]').value='synthetic'; document.querySelector('form').requestSubmit()");
  await waitFor("document.querySelector('[role=alert]')?.textContent.includes('Invalid credentials')");
  check(true,'failed login feedback');
  await evaluate("pwaFixture.loginError=false; document.querySelector('form').requestSubmit()");
  await waitFor("document.querySelector('.sp-order-card')");
  check(await evaluate("pwaFixture.requests.some(r=>r.path==='/api/identity/login' && r.headers['X-CSRF-TOKEN']==='synthetic')"),'login uses established CSRF contract');
  await evaluate("location.hash='/settings'"); await waitFor("document.querySelector('h1')?.textContent==='Settings'");
  await click('Sign out'); await waitFor("document.querySelector('.staff-login-page')");
  check(await evaluate("!document.querySelector('.sp-app')"),'logout removes protected workspace');
  // XML parser and mock transport use browser DOMParser, never a real printer.
  const printerChecks = await evaluate(`(async()=>{
    const p=await import('/src/staff-pwa/features/printing/epos.js');
    const response=success=>'<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><response xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print" success="'+success+'" code="'+(success==='true'?'':'EPTR_OFF_LINE')+'" status="2"/></s:Body></s:Envelope>';
    let checks=0;
    if(p.parsePrintResponse(response('true')).success)checks++;
    for(const xml of [response('false'),'<html>broken</html>','<bad>']){try{p.parsePrintResponse(xml)}catch{checks++}}
    let sent=0;
    const printer=p.createPrinter({enabled:true,fetcher:async()=>{sent++;return new Response(response('true'))}});
    await printer.test({...p.printerDefaults,endpoint:'https://printer.invalid'}); if(sent===1)checks++;
    return checks;
  })()`);
  check(printerChecks===5,'browser XML and mock print completion/error handling');
  check(errors.length===0,`no browser exceptions: ${JSON.stringify(errors)}`);
  console.log(`Staff PWA: ${checks} browser assertions passed. Screenshots: ${artifacts}`);
} finally { await send('Page.close').catch(()=>{}); ws.close(); }
