import {test} from 'node:test';
import assert from 'node:assert/strict';
import {australianMobilePayload as payload,australianMobileDisplay as display,validAustralianMobile as valid} from './australianPhone.js';
import {changeDestination,emptyVerification} from '../../domains/notification/model/contactVerification.js';
import {submitOrder} from '../../domains/ordering/api/orderApi.js';
import {startContactVerification} from '../../domains/notification/api/contactVerificationApi.js';
test('Australian entry and pasted spacing have one country code',()=>{
 for(const raw of ['0434 778 435','434778435','+61 434 778 435','61434778435','+61 (0)434-778-435']){
  assert.equal(payload(raw),'+61434778435');assert.equal(display(raw),'+61 434 778 435');assert.ok(valid(raw));assert.equal(payload(display(raw)),payload(raw));
 }
});
test('partial edits, deletion and invalid input retain their meaning',()=>{
 for(const raw of ['','0','04','0434','+61','+614','+61 434 778 43'])assert.equal(valid(raw),false);
 assert.equal(payload('04'),'+614');assert.equal(display('0434'),'+61 434');assert.equal(payload(''),'');
 for(const raw of ['+61 +61 434778435','04347784355','04hello','+447123456789']){assert.equal(valid(raw),false);assert.equal(display(raw),raw);}
 assert.equal(payload('0434 778 436'),'+61434778436');
});
test('formatting keeps verification; digit changes invalidate it',()=>{
 const state={...emptyVerification('+61434778435'),status:'verified',id:'challenge'};
 assert.equal(changeDestination(state,payload('0434 778 435')),state);
 assert.equal(changeDestination(state,payload('0434 778 436')).id,null);
});
test('verification API receives existing normalized destination without display spacing',async()=>{
 const original=global.fetch;const requests=[];
 global.fetch=async(url,options={})=>{requests.push({url,options});return Response.json(url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'test'}:{id:'challenge'});};
 try{await startContactVerification('SMS',payload('0434 778 435'));assert.deepEqual(JSON.parse(requests.at(-1).options.body),{channel:'SMS',destination:'+61434778435'});}finally{global.fetch=original;}
});

test('checkout sends the same canonical phone and exact saved payload on retry',async()=>{
 const original=global.fetch,submitted=[];
 const saved={requestId:'same-order',trackingToken:'same-token',customerName:'Guest',phone:payload('0434 778 435'),phoneVerificationId:'challenge',email:null,notes:'No cutlery',paymentMethod:'PAY_AT_RESTAURANT',items:[]};
 global.fetch=async(url,options={})=>{if(url.endsWith('/csrf'))return Response.json({headerName:'X-CSRF-TOKEN',token:'test'});submitted.push(JSON.parse(options.body));return Response.json({id:'same-order'});};
 try{await submitOrder(saved);await submitOrder(saved);assert.deepEqual(submitted,[saved,saved]);assert.equal(submitted[0].phone,'+61434778435');}finally{global.fetch=original;}
});
