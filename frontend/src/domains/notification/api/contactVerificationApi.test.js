import { test } from 'node:test';
import assert from 'node:assert/strict';
import { startContactVerification, verifyContactCode } from './contactVerificationApi.js';

test('ordering SMS challenge and check use same-origin cookies and CSRF', async () => {
 const original=globalThis.fetch, calls=[];
 globalThis.fetch=async (url,options)=>{
  calls.push({url,options});
  return Response.json(url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'test'}:{id:'challenge',verified:true});
 };
 try {
  assert.equal((await startContactVerification('SMS','0412 345 678')).id,'challenge');
  assert.equal((await verifyContactCode('challenge','123456')).verified,true);
  assert.deepEqual(calls.map(c=>c.url),['/api/orders/csrf','/api/orders/contact-verifications','/api/orders/csrf','/api/orders/contact-verifications/challenge/verify']);
  assert.deepEqual(JSON.parse(calls[1].options.body),{channel:'SMS',destination:'0412 345 678'});
  assert.deepEqual(JSON.parse(calls[3].options.body),{code:'123456'});
  for(const c of calls)assert.equal(c.options.credentials,'same-origin');
  for(const c of [calls[1],calls[3]])assert.equal(c.options.headers['X-CSRF-TOKEN'],'test');
 } finally {globalThis.fetch=original;}
});
test('invalid, expired, limited and unavailable verification errors are surfaced', async () => {
 const original=globalThis.fetch;
 try {
  for(const [status,message] of [[400,'Invalid code'],[400,'Verification expired'],[429,'Too many requests'],[503,'SMS verification unavailable']]) {
   globalThis.fetch=async url=>Response.json(url.endsWith('/csrf')?{headerName:'X-CSRF-TOKEN',token:'test'}:{message},{status:url.endsWith('/csrf')?200:status});
   await assert.rejects(verifyContactCode('challenge','123456'),{message});
  }
  globalThis.fetch=async()=>new Response('unavailable',{status:503});
  await assert.rejects(startContactVerification('SMS','0412345678'),{message:'Verification service unavailable. Please try again.'});
 } finally {globalThis.fetch=original;}
});
