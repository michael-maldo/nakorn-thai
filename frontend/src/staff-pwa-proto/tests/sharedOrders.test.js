import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {orderActions as original} from '../../domains/ordering/model/orderModel.js';
import {orderActions as prototype} from '../features/ordering/model/orderModel.js';
import {createSoundEngine as legacySound} from '../../staff-pwa/features/notifications/alerts.js';
import {createSoundEngine} from '../../shared/staff-orders/audio/alerts.js';
import {createPrinter as legacyPrinter} from '../../staff-pwa/features/printing/epos.js';
import {createPrinter} from '../../shared/staff-orders/printing/epos.js';
import {readPreference,writePreference} from '../app/orderPreferences.js';
import '../../staff-pwa/tests/core.test.js';
test('both apps use identical permissions, audio and printer implementations',()=>{
 assert.equal(original,prototype);assert.equal(legacySound,createSoundEngine);assert.equal(legacyPrinter,createPrinter);
 for(const role of ['ADMIN','FOH','BOH'])for(const [state,next] of Object.entries({NEW:'ACCEPTED',ACCEPTED:'PREPARING',PREPARING:'READY',READY:'COMPLETED'}))assert.deepEqual(original(role,state),role==='ADMIN'?[next,'CANCELLED']:[next]);
});
test('prototype contains no copied queue, polling, sound or printing controller',()=>{
 const page=readFileSync(new URL('../features/ordering/pages/OrdersAdminPage.jsx',import.meta.url),'utf8');
 assert.match(page,/shared\/staff-orders\/components\/OrdersPage/);assert.doesNotMatch(page,/setTimeout|setInterval|changeOrderStatus|createSoundEngine/);
 for(const path of ['../../staff-pwa/StaffApp.jsx','../StaffProtoApp.jsx'])assert.match(readFileSync(new URL(path,import.meta.url),'utf8'),/shared\/staff-orders\/hooks\/useOrdersController/);
});
test('prototype device preferences cannot overwrite original PWA preferences',()=>{
 const saved=globalThis.localStorage,values=new Map([['nakorn.staff.alerts','{"volume":0.2}']]);
 globalThis.localStorage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v)};
 try{assert.equal(writePreference('alerts',{volume:0.8}),true);assert.deepEqual(readPreference('alerts',{}),{volume:0.8});assert.equal(values.get('nakorn.staff.alerts'),'{"volume":0.2}');}finally{globalThis.localStorage=saved;}
});

test('legacy operational paths are reexports, not duplicate implementations',()=>{
 for(const path of ['orders/OrderDetails.jsx','orders/useOrderMonitor.js','notifications/alerts.js','printing/epos.js','printing/dockets.js']){
  const source=readFileSync(new URL('../../staff-pwa/features/'+path,import.meta.url),'utf8');
  assert.match(source,/shared\/staff-orders/);assert.doesNotMatch(source,/function |setTimeout|fetch\(/);
 }
});

test('ADMIN integrations Printing reuses the same printer UI and session profile',()=>{
 const page=readFileSync(new URL('../features/restaurant/pages/IntegrationSettingsPage.jsx',import.meta.url),'utf8');
 assert.match(page,/printing\/PrinterSettings/);
 assert.match(page,/<summary>Printing<\/summary>/);
 assert.match(page,/config={orders.config} printer={orders.printer} onSave={orders.savePrinter}/);
});

test('printer preferences persist independently and preserve legacy profiles',async()=>{
 const original=await import('../../staff-pwa/preferences.js');
 const saved=globalThis.localStorage,values=new Map();
 globalThis.localStorage={getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value)};
 try{
  const profile={name:'Existing',endpoint:'https://printer.test',deviceId:'local_printer',paperWidth:80,layout:'detailed',autoPrint:true};
  original.writePreference('printer',profile);
  writePreference('printer',{...profile,name:'Prototype',deviceEnabled:true,autoPrint:false});
  assert.deepEqual(original.readPreference('printer',{}),profile);
  assert.equal(readPreference('printer',{}).deviceEnabled,true);
  original.writePreference('printer',{...original.readPreference('printer',{}),deviceEnabled:true,autoPrint:false});
  assert.equal(original.readPreference('printer',{}).endpoint,profile.endpoint);
  assert.equal(readPreference('printer',{}).name,'Prototype');
 }finally{globalThis.localStorage=saved;}
});
