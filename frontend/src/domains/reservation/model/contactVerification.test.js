import {test} from 'node:test';
import assert from 'node:assert/strict';
import {emptyVerification,changeDestination,canSubmit,verificationIds} from './contactVerification.js';
const verified={...emptyVerification('0412345678'),status:'verified',id:'challenge',expiresAt:'2099-01-01T00:00:00Z'};
test('reservation requires verified mobile; optional email provides no substitute',()=>{assert.equal(canSubmit(emptyVerification()),false);assert.equal(canSubmit(verified),true);assert.equal(canSubmit(emptyVerification('0412345678')),false);});
test('changed destination clears challenge and verification immediately',()=>{const changed=changeDestination(verified,'0499999999');assert.equal(changed.status,'idle');assert.equal(changed.id,null);assert.equal(canSubmit(changed),false);});
test('expired and invalid challenges do not permit submission',()=>{assert.equal(canSubmit({...verified,expiresAt:'2020-01-01'}),false);assert.equal(canSubmit({...verified,status:'codeSent',error:'Invalid code'}),false);});
test('submission carries SMS identifier only, never email verification',()=>{assert.deepEqual(verificationIds(verified),{phoneVerificationId:'challenge'});assert.deepEqual(verificationIds(emptyVerification()),{phoneVerificationId:null});});
