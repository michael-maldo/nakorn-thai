import { test } from 'node:test';
import assert from 'node:assert/strict';
import {emptyVerification,changeDestination,isVerified} from './contactVerification.js';
const verified={...emptyVerification('0412345678'),status:'verified',id:'sms-id',expiresAt:'2099-01-01T00:00:00Z'};
test('mobile verification requires successful, unexpired server challenge',()=>{
 assert.ok(isVerified(verified));
 for(const state of [emptyVerification('0412345678'),{...verified,status:'codeSent'},{...verified,id:null},{...verified,expiresAt:'2020-01-01'}])assert.ok(!isVerified(state));
});
test('any mobile edit immediately clears verification including formatting edits',()=>{
 for(const destination of ['0499999999','0412 345 678','']){
  const next=changeDestination(verified,destination);
  assert.deepEqual(next,emptyVerification(destination));assert.ok(!isVerified(next));
 }
 assert.equal(changeDestination(verified,verified.destination),verified);
});
