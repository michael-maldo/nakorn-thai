import {test} from 'node:test';
import assert from 'node:assert/strict';
import {emptyVerification,changeDestination,canSubmit,verificationIds} from './contactVerification.js';
const verified={...emptyVerification('0412345678'),status:'verified',id:'challenge',expiresAt:'2099-01-01T00:00:00Z'};
test('reservation requires verification and accepts either verified contact',()=>{assert.equal(canSubmit(emptyVerification(),emptyVerification()),false);assert.equal(canSubmit(verified,emptyVerification()),true);assert.equal(canSubmit(emptyVerification(),verified),true);});
test('changed destination clears challenge and verification immediately',()=>{const changed=changeDestination(verified,'0499999999');assert.equal(changed.status,'idle');assert.equal(changed.id,null);assert.equal(canSubmit(changed,emptyVerification()),false);});
test('expired and invalid challenges do not permit submission',()=>{assert.equal(canSubmit({...verified,expiresAt:'2020-01-01'},emptyVerification()),false);assert.equal(canSubmit({...verified,status:'codeSent',error:'Invalid code'},emptyVerification()),false);});
test('submission carries only independently verified identifiers',()=>{assert.deepEqual(verificationIds(verified,emptyVerification('guest@example.com')),{phoneVerificationId:'challenge',emailVerificationId:null});assert.deepEqual(verificationIds(verified,{...verified,id:'email-id'}),{phoneVerificationId:'challenge',emailVerificationId:'email-id'});});
