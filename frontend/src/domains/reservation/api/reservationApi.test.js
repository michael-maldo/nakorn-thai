import test from 'node:test';
import assert from 'node:assert/strict';
import { reservationRequest } from './reservationApi.js';
test('booking submission obtains CSRF and preserves request reference',async()=>{
 const original=globalThis.fetch;const calls=[];
 globalThis.fetch=async(url,options)=>{calls.push({url,options});return new Response(JSON.stringify(url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'test-token'}:{reference:'request-id'}),{status:200});};
 try{const receipt=await reservationRequest('',{method:'POST',body:{requestId:'request-id'}});assert.equal(receipt.reference,'request-id');assert.equal(calls[0].url,'/api/reservations/csrf');assert.equal(calls[1].options.headers['X-CSRF-TOKEN'],'test-token');assert.equal(JSON.parse(calls[1].options.body).requestId,'request-id');}finally{globalThis.fetch=original;}
});
test('booking validation message reaches customer',async()=>{
 const original=globalThis.fetch;globalThis.fetch=async()=>new Response(JSON.stringify({message:'Choose a future time'}),{status:400});
 try{await assert.rejects(reservationRequest('/bad'),/Choose a future time/);}finally{globalThis.fetch=original;}
});
