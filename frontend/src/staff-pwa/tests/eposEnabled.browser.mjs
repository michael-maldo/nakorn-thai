// Synthetic APIs only. Printer requests are blocked by CDP, and no print action is clicked.
import assert from 'node:assert/strict';
import {browser} from '../../staff-pwa-proto/tests/browserHarness.mjs';
import {installPwaFixtures} from '../../../tests/e2e/staffPwaFixtures.mjs';
import {staffSessionFixture} from '../../../tests/e2e/staffIdentityFixtures.mjs';
const b=await browser(),{send,evaluate,waitFor}=b;
const base=process.env.STAFF_EPOS_PREVIEW_URL||'http://127.0.0.1:5177/';
try{
 await send('Network.setBlockedURLs',{urls:['https://printer.invalid/*',base+'api/*']});
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${installPwaFixtures.toString()})(${staffSessionFixture.toString()});`});
 for(const role of ['ADMIN','FOH','BOH']){
  await send('Page.navigate',{url:base+'staff/?role='+role+'#/printers'});
  await waitFor("document.querySelector('h1')?.textContent==='Printer Management'");
  await evaluate("localStorage.setItem('nakorn.staff.printer',JSON.stringify({name:'Preserved',endpoint:'https://printer.invalid',deviceId:'local_printer',paperWidth:80,layout:'kitchen',autoPrint:true}));location.reload()");
  await waitFor("document.querySelector('input[maxlength]')?.value==='Preserved'");
  assert.ok(await evaluate("document.body.innerText.includes('Experimental browser printing enabled')"));
  assert.equal(await evaluate("document.querySelector('input[type=checkbox]').checked"),false);
  assert.equal(await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='Test Print').disabled"),true);
  await evaluate("document.querySelector('input[type=checkbox]').click()");
  await waitFor("JSON.parse(localStorage.getItem('nakorn.staff.printer')).deviceEnabled===true");
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn.staff.printer')).autoPrint"),false);
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn.staff.printer')).paperWidth"),80);
  assert.equal(await evaluate("[...document.querySelectorAll('button')].find(b=>b.textContent==='Test Print').disabled"),false);
  await evaluate('location.reload()');await waitFor("document.querySelector('input[type=checkbox]')?.checked");
  await evaluate("location.hash='/settings'");
  await waitFor("document.querySelector('summary')?.textContent==='Printing'");
  if(role==='ADMIN'){
   await evaluate("document.querySelector('[aria-label=Manage]').click()");
   await waitFor("document.querySelector('.sp-manage')");
   assert.ok(await evaluate("document.querySelector('.sp-manage').innerText.includes('Settings & integrations')"));
  }
  else assert.equal(await evaluate("!!document.querySelector('[aria-label=Manage]')"),false);
 }
 assert.deepEqual(b.errors,[]);
 console.log('PASS: enabled original production bundle; legacy profile preservation, device opt-in/persistence, auto-print off, all operational roles and ADMIN-only integration access. No printer requests.');
}finally{await b.close();}
