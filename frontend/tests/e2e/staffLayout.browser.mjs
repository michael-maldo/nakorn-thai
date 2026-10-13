/** Start Vite on 5174 and isolated Chromium CDP on 9223 (or override URLs).
 * APIs are synthetic; screenshots/results go to the OS temporary directory. */
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { installStaffLayoutFixtures } from './staffLayoutFixtures.mjs';
const base = process.env.STAFF_LAYOUT_TEST_URL || 'http://127.0.0.1:5174/';
const debug = process.env.CHROME_DEBUG_URL || 'http://127.0.0.1:9223';
const artifacts = join(tmpdir(), 'nakorn-stage2-layout');
await mkdir(artifacts, { recursive: true });
const page = await (await fetch(`${debug}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => ws.addEventListener('open', resolve, { once: true }));
let sequence = 0, checks = 0;
const pending = new Map(), errors = [], results = [];
ws.addEventListener('message', event => {
  const message = JSON.parse(event.data);
  if (message.id) {
    const waiter = pending.get(message.id); if (!waiter) return; pending.delete(message.id); clearTimeout(waiter.timeout);
    message.error ? waiter.reject(message.error) : waiter.resolve(message.result);
  } else if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++sequence;
  const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
  pending.set(id, { resolve, reject, timeout }); ws.send(JSON.stringify({ id, method, params }));
});
ws.addEventListener('close', () => { for (const waiter of pending.values()) { clearTimeout(waiter.timeout); waiter.reject(new Error('Browser connection closed')); } pending.clear(); });
const evaluate = async expression => {
  const value = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
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
const check = (value, message) => { checks++; assert.ok(value, message); };
const frame = () => evaluate('new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)))');
const navigate = async (route, width, role = 'ADMIN') => {
  await send('Emulation.setDeviceMetricsOverride', { width, height: 1000, deviceScaleFactor: 1, mobile: false });
  // A complete document navigation installs role-specific fixtures before React mounts.
  await send('Page.navigate', { url: 'about:blank' });
  await send('Page.navigate', { url: `${base}?role=${role}#/${route}` });
  await waitFor("document.querySelector('h1') && !/Checking your staff session|Loading menu administration|Loading home page settings|Loading orders|Updating bookings|Updating enquiries|Updating schedule|Updating ordering status/.test(document.body.innerText)");
  if (['staff/orders','staff/reservations','staff/functions'].includes(route)) await waitFor("document.querySelectorAll('.order-queue .order-panel').length===2");
  if (route === 'staff/users') await waitFor("document.querySelectorAll('.staff-table tbody tr').length===1");
  if (route === 'staff/homepage') await waitFor("document.querySelector('.homepage-collection-choices')");
  if (route === 'staff/settings' && role === 'ADMIN') await waitFor("document.querySelector('form fieldset') && !document.querySelector('form fieldset').disabled");
  await frame();
};
const capture = async label => {
  const screenshot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  await writeFile(join(artifacts, `${label}.png`), Buffer.from(screenshot.data, 'base64'));
};
const measure = () => evaluate(`(()=>{
  const main=document.querySelector('.staff-workspace main'), box=main.getBoundingClientRect();
  const queues=[...document.querySelectorAll('.order-queue .order-panel')].map(e=>{const r=e.getBoundingClientRect();return {width:r.width,x:r.x,right:r.right};});
  const escaped=[...main.querySelectorAll('input,select,textarea,fieldset,button')].filter(e=>{
    if(e.closest('.staff-table-wrap,.menu-admin-table-wrap'))return false;
    const r=e.getBoundingClientRect();if(!r.width||!r.height)return false;
    const parent=e.closest('.order-panel')||main,p=parent.getBoundingClientRect();return r.left<p.left-1||r.right>p.right+1;
  }).map(e=>({tag:e.tagName,name:e.name,text:e.textContent}));
  return {width:innerWidth,client:document.documentElement.clientWidth,scroll:document.scrollingElement.scrollWidth,mainX:box.x,mainWidth:box.width,queues,escaped,headers:document.querySelectorAll('.staff-topbar').length,menuHeaders:document.querySelectorAll('.menu-admin-topbar').length,signOut:[...document.querySelectorAll('button')].filter(e=>e.textContent==='Sign out').length};
})()`);
const routes = ['staff', 'staff/orders', 'staff/ordering', 'staff/homepage', 'staff/menu', 'staff/reservations', 'staff/functions', 'staff/restaurant', 'staff/settings', 'staff/users', 'staff/menu/items/new', ...['overview', 'pricing', 'options', 'collections', 'images'].map(s => `staff/menu/items/dish/${s}`), 'staff/menu/collections', 'staff/menu/collections/new', ...['overview', 'items', 'categories', 'availability'].map(s => `staff/menu/collections/main/${s}`)];
try {
  await send('Page.enable'); await send('Runtime.enable'); await send('Page.bringToFront');
  await send('Page.addScriptToEvaluateOnNewDocument', { source: `(${installStaffLayoutFixtures.toString()})()` });
  for (const width of [1440, 1024, 768, 390]) {
    let commonX;
    for (const route of routes) {
      await navigate(route, width);
      const data = await measure(); results.push({ route, ...data });
      check(data.scroll <= data.client + 1, `${route} ${width}: page overflow ${JSON.stringify(data)}`);
      check(data.escaped.length === 0, `${route} ${width}: controls escape ${JSON.stringify(data.escaped)}`);
      check(data.headers === 1 && data.menuHeaders === 0 && data.signOut === 1, `${route} ${width}: duplicate shell/account controls`);
      check(await evaluate("document.querySelectorAll('.staff-skip').length===1 && document.querySelectorAll('.menu-admin-skip').length===0"), `${route}: one skip link`);
      if (width===390 && route==='staff/menu') check(await evaluate("document.querySelector('.menu-admin-toolbar select').clientWidth>=140"), `${route}: mobile selected filter remains readable`);
      if (width>=1024) check(await evaluate("getComputedStyle(document.querySelector('.staff-nav-toggle')).display==='none'"), `${route}: desktop toggle hidden`);
      if (!route.startsWith('staff/menu')) {
        commonX ??= data.mainX;
        check(Math.abs(data.mainX - commonX) < 1, `${route} ${width}: inconsistent gutter`);
      }
      if (['staff/orders','staff/reservations','staff/functions'].includes(route)) check(data.queues.length === 2 && data.queues.every(q => q.width >= 300), `${route} ${width}: unreadable queue card`);
      const hardeningStable=await evaluate(`(async()=>{
        const style=document.querySelector('style[data-vite-dev-id$="/staff.css"]'),text=style.textContent;
        const frame=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
        const snapshot=()=>JSON.stringify([...document.querySelectorAll('.staff-workspace,.staff-workspace *')].map(e=>{const s=getComputedStyle(e),r=e.getBoundingClientRect();return [r.width||r.height?[r.x+scrollX,r.y+scrollY,r.width,r.height]:[0,0,0,0],[...s].map(p=>[p,s.getPropertyValue(p)])];}));
        const after=snapshot();
        try{style.textContent=text.replaceAll(':where(.staff-workspace) ','');await frame();return after===snapshot();}
        finally{style.textContent=text;await frame();}
      })()`);
      check(hardeningStable, `${route} ${width}: root guards changed staff appearance`);
      await capture(`${route.replaceAll('/', '-')}-${width}`);
    }
    await navigate('staff/settings', width);
    const labels = ['Business settings', 'PayPal', 'PayID', 'Twilio', 'Email / SMTP'];
    for (const label of labels) {
      await evaluate(`Array.from(document.querySelectorAll('.staff-section-navigation button')).find(b=>b.textContent===${JSON.stringify(label)}).click()`); await frame();
      const selected = await evaluate(`(()=>{const buttons=[...document.querySelectorAll('.staff-section-navigation button')],e=buttons.find(b=>b.getAttribute('aria-pressed')==='true'),s=getComputedStyle(e);return {label:e.textContent,selected:buttons.filter(b=>b.getAttribute('aria-pressed')==='true').length,underline:s.textDecorationLine,weight:s.fontWeight,navHeight:e.parentElement.getBoundingClientRect().height};})()`);
      check(selected.label === label && selected.selected === 1 && selected.underline.includes('underline'), `Settings ${width}: visible/semantic selection ${label}`);
      check(selected.navHeight < 200, `Settings ${width}: navigation dominates viewport`);
      await evaluate("document.querySelector('form').scrollIntoView({block:'start',behavior:'instant'})"); await frame();
      const data = await measure(); check(data.escaped.length === 0 && data.scroll <= data.client + 1, `Settings ${label} ${width}: form overflow`);
      await capture(`settings-${label.replaceAll('/', '-')}-${width}`);
    }
    console.log(`Verified staff routes and Settings sections at ${width}px`);
    for (const role of ['FOH', 'BOH']) {
      await navigate('staff', width, role);
      const links = await evaluate("[...document.querySelectorAll('#staff-navigation a')].map(e=>e.getAttribute('href'))");
      check(!links.some(l=>['#/staff/menu','#/staff/users','#/staff/settings'].includes(l)), `${role}: admin navigation leaked`);
      check(links.includes('#/staff/orders') && (role === 'FOH' ? links.includes('#/staff/reservations') : !links.includes('#/staff/reservations')), `${role}: role navigation`);
      await capture(`${role}-navigation-${width}`);
    }
  }
  for (const width of [1440,1024,768,390]) {
    await navigate('staff', width, 'ANON');
    check(await evaluate("document.querySelectorAll('.staff-login input').length===2 && document.scrollingElement.scrollWidth<=document.documentElement.clientWidth"), `Login ${width}: fields and reflow`);
    check(await evaluate("getComputedStyle(document.querySelector('.staff-login input')).borderColor==='rgb(115, 133, 122)'"), `Login ${width}: contrast boundary`);
    await capture(`login-${width}`);
  }
  await navigate('staff', 390);
  await evaluate("document.querySelector('.staff-nav-toggle').focus()");
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', text: '\r', unmodifiedText: '\r', windowsVirtualKeyCode: 13, nativeVirtualKeyCode: 13 });
  await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
  await waitFor("document.querySelector('.staff-nav-toggle').getAttribute('aria-expanded')==='true'");
  check(await evaluate("document.querySelector('.staff-nav-toggle').getAttribute('aria-expanded')==='true'"), 'Keyboard expands navigation');
  await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Escape', code: 'Escape', windowsVirtualKeyCode: 27 });
  await waitFor("document.querySelector('.staff-nav-toggle').getAttribute('aria-expanded')==='false'");
  check(await evaluate("document.querySelector('.staff-nav-toggle').getAttribute('aria-expanded')==='false' && document.activeElement===document.querySelector('.staff-nav-toggle')"), 'Escape returns toggle focus');
  check(await evaluate("getComputedStyle(document.activeElement).outlineStyle==='solid'"), 'Visible keyboard focus');
  await navigate('staff/menu/items/dish/images', 390);
  check(await evaluate("document.querySelectorAll('.menu-admin-subnav a').length===7 && [...document.querySelectorAll('.menu-admin-subnav a')].every(e=>e.getBoundingClientRect().right<=document.documentElement.clientWidth)"), 'All Menu sections visible without hidden horizontal navigation');
  await navigate('staff/settings', 390);
  const cursor = await evaluate("(()=>{const e=document.querySelector('button');e.disabled=true;const disabled=getComputedStyle(e).cursor;e.setAttribute('aria-busy','true');return {disabled,busy:getComputedStyle(e).cursor};})()");
  check(cursor.disabled === 'not-allowed' && cursor.busy === 'wait', 'Disabled versus busy cursor');
  // Inspect every selector, including nested media/container/forced-color rules.
  const isolation = await evaluate(`(()=>{
    const style=document.querySelector('style[data-vite-dev-id$="/staff.css"]');
    const selectors=[];
    function visit(rules){for(const rule of rules){if(rule.selectorText)selectors.push(...rule.selectorText.split(',').map(s=>s.trim()));if(rule.cssRules)visit(rule.cssRules);}}
    visit(style.sheet.cssRules);
    const rooted=s=>s==='.staff-workspace'||s==='.staff-login-page'||s.startsWith('.staff-workspace ')||s.startsWith('.staff-login-page ')||s.startsWith(':where(.staff-workspace) ');
    const classes=[...new Set(selectors.flatMap(s=>s.match(/\\.[a-zA-Z][a-zA-Z0-9_-]*/g)||[]))].map(s=>s.slice(1)).filter(s=>!['staff-workspace','staff-login-page'].includes(s));
    return {count:selectors.length,unscoped:selectors.filter(s=>!rooted(s)),classes};
  })()`);
  check(isolation.count>0 && isolation.unscoped.length===0, `Unscoped staff selectors: ${JSON.stringify(isolation.unscoped)}`);
  // Remove and restore only the new staff stylesheet in the same rendered public page.
  const publicSnapshot = `JSON.stringify([...document.querySelectorAll('.site-header,.page-width,.button,.staff-panel,input,select,textarea')].filter(e=>e.getBoundingClientRect().width).map(e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return [e.tagName,e.className,r.x,r.y,r.width,r.height,s.color,s.backgroundColor,s.border,s.padding,s.fontSize];}))`;
  for (const width of [1440,1024,768,390]) for (const route of ['', 'menu', 'reservations', 'functions']) {
    await navigate(route, width); await waitFor("document.querySelector('.site-header') && !/Loading dishes|Loading our menu/.test(document.body.innerText)");
    check(await evaluate("document.querySelector('[role=alert]')===null"), `Public ${route || 'home'} ${width}: fixture error`);
    await evaluate("window.staffStyle=document.querySelector('style[data-vite-dev-id$=\"/staff.css\"]');window.staffStyleText=staffStyle.textContent;staffStyle.textContent=''"); await frame();
    const before = await evaluate(publicSnapshot); await capture(`public-before-${route || 'home'}-${width}`);
    await evaluate('staffStyle.textContent=staffStyleText'); await frame();
    const after = await evaluate(publicSnapshot); await capture(`public-after-${route || 'home'}-${width}`);
    await writeFile(join(artifacts, `public-${route || 'home'}-${width}.json`), JSON.stringify({before:JSON.parse(before),after:JSON.parse(after)},null,2));
    check(before === after, `Public ${route || 'home'} ${width}: staff CSS changed rendering`);
    // Simulate future public components reusing staff child classes, never staff roots.
    const collisionSafe=await evaluate(`(async()=>{
      const probe=document.createElement('div');probe.id='staff-css-isolation-probe';probe.className=${JSON.stringify(isolation.classes)}.join(' ');
      for(const tag of ['div','nav','h1','h2','p','small','span','dt','dd','label','fieldset','input','select','textarea','button','a']){
        const node=document.createElement(tag);node.className=probe.className;node.textContent=tag==='input'?'':'Public class reuse';
        if(tag==='button'){node.type='button';node.disabled=true;node.setAttribute('aria-busy','true');node.setAttribute('aria-pressed','true');}
        if(tag==='select'){const option=document.createElement('option');option.textContent='Public option';node.append(option);}
        probe.append(node);
      }
      document.body.append(probe);
      const style=document.querySelector('style[data-vite-dev-id$="/staff.css"]'),text=style.textContent;
      const frame=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
      const snapshot=()=>JSON.stringify([probe,...probe.querySelectorAll('*')].map(e=>{const s=getComputedStyle(e);return [...s].map(p=>[p,s.getPropertyValue(p)]);}));
      try{style.textContent='';await frame();const before=snapshot();style.textContent=text;await frame();return before===snapshot();}
      finally{style.textContent=text;probe.remove();}
    })()`);
    check(collisionSafe, `Public ${route || 'home'} ${width}: staff child-class reuse leaks styles`);
  }
  check(errors.length === 0, `Runtime exceptions: ${JSON.stringify(errors)}`);
  await writeFile(join(artifacts, 'results.json'), JSON.stringify({ checks, routeRenders: results.length, selectorIsolation: isolation, results, errors }, null, 2));
  console.log(`PASS: ${checks} layout assertions; ${results.length} staff route/width renders; 20 Settings section renders; 8 role-navigation renders; 16 public comparisons; 16 class-reuse isolation checks. Artifacts: ${artifacts}`);
} finally { if (ws.readyState===WebSocket.OPEN) await send('Page.close').catch(() => {}); ws.close(); }
