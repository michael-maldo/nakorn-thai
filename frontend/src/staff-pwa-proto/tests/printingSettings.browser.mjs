import assert from 'node:assert/strict';
import {browser} from './browserHarness.mjs';
import {installPrototypeFixtures} from './prototypeFixtures.mjs';
import {staffSessionFixture} from '../../../tests/e2e/staffIdentityFixtures.mjs';
import {installStaffLayoutFixtures} from '../../../tests/e2e/staffLayoutFixtures.mjs';
const b=await browser(),{send,evaluate,waitFor,click,field}=b;
const base=process.env.PROTO_TEST_URL||'http://127.0.0.1:5175/staff-pwa-proto/';
try{
 await send('Page.addScriptToEvaluateOnNewDocument',{source:`(${installPrototypeFixtures.toString()})(${staffSessionFixture.toString()},${installStaffLayoutFixtures.toString()});localStorage.removeItem('nakorn-staff-pwa-proto:orders:printer');localStorage.setItem('nakorn.staff.printer',JSON.stringify({name:'Original untouched'}));`});
 await send('Page.navigate',{url:base+'#/staff'});await waitFor("document.querySelector('.proto-app')");
 await click('Manage');await evaluate("[...document.querySelectorAll('dialog a')].find(a=>a.textContent.includes('Settings & integrations')).click()");
 await waitFor("document.querySelector('summary')?.textContent==='Printing'");await evaluate("document.querySelector('summary').click()");await waitFor("document.querySelector('details').open");
 assert.equal(await evaluate("document.querySelectorAll('details form').length"),1);
 await field('Printer name','Prototype counter');await field('HTTPS printer origin / IP','https://printer.test');await field('Paper width','80');await field('Docket layout','detailed');
 const writes=await evaluate('fixture.writes.length');await click('Save profile');await waitFor("document.body.innerText.includes('Profile saved.')");
 assert.equal(await evaluate('fixture.writes.length'),writes);
 assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn-staff-pwa-proto:orders:printer')).paperWidth"),80);
 assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn.staff.printer')).name"),'Original untouched');
 assert.equal(await evaluate("[...document.querySelectorAll('details button')].filter(b=>/Test Print|Check saved endpoint/.test(b.textContent)).every(b=>b.disabled)"),true);
 assert.equal(await evaluate("document.querySelector('details [type=checkbox]').checked"),false);
 await b.check('Enable printing on this device');
 assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn-staff-pwa-proto:orders:printer')).deviceEnabled"),true);
 assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn-staff-pwa-proto:orders:printer')).autoPrint"),false);
 const capable=await evaluate("document.body.innerText.includes('Experimental browser printing enabled')");
 assert.equal(await evaluate("[...document.querySelectorAll('details button')].find(b=>b.textContent==='Test Print').disabled"),!capable);
 await b.check('Enable printing on this device');
 assert.equal(await evaluate("[...document.querySelectorAll('details button')].find(b=>b.textContent==='Test Print').disabled"),true);
 await click('Device settings');await waitFor("document.querySelector('dialog input')");
 assert.equal(await evaluate("document.querySelector('dialog input[maxlength]').value"),'Prototype counter');
 await evaluate("(()=>{const input=document.querySelector('dialog input[maxlength]');Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'Updated via device');input.dispatchEvent(new Event('input',{bubbles:true}));})()");
 await click('Save profile','dialog button');await waitFor("document.querySelector('dialog[open]').innerText.includes('Profile saved.')");await click('Close dialog');
 assert.equal(await evaluate("document.querySelector('details input[maxlength]').value"),'Updated via device');
 for(const role of ['FOH','BOH']){
  await send('Page.navigate',{url:base+'?role='+role+'#/staff/settings'});await waitFor("document.body.innerText.includes('Access restricted')");
  assert.equal(await evaluate("!!document.querySelector('.proto-manage')"),false);assert.equal(await evaluate("!!document.querySelector('summary')"),false);
  await click('Device settings');await waitFor("document.querySelector('dialog input')");
  await field('Printer name',role+' device');await field('HTTPS printer origin / IP','https://printer.test');await click('Save profile');await waitFor("document.body.innerText.includes('Profile saved.')");
  assert.equal(await evaluate("[...document.querySelectorAll('dialog button')].find(b=>b.textContent==='Test Print').disabled"),true);
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('nakorn.staff.printer')).name"),'Original untouched');
  await click('Close dialog');
 }
 assert.deepEqual(b.errors,[]);
 console.log('PASS: ADMIN Manage → Settings & integrations → Printing reuses operational profile; local save has no API writes, original preferences isolated, disabled gate intact, FOH/BOH device access without administration.');
}finally{await b.close();}
