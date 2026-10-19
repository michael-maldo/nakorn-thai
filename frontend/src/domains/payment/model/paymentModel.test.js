import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizePayPalReturnHash, paypalReturnMode, safeApprovalUrl, resumableApproval, paypalView, PAYPAL_CHECK_DELAYS } from './paymentModel.js';
test('observed sandbox return and cancel hashes resolve to the exact confirmation route', () => {
 for (const outcome of ['return', 'cancel']) {
  const location = new URL(`https://nakorn-thai.tech-labs.dev/?paypal=${outcome}#/order-confirmation&token=4D100105X1957711S&PayerID=3AEHFAN7TUYQ6`);
  assert.equal(normalizePayPalReturnHash(location.hash).split('?')[0], '#/order-confirmation');
  assert.equal(paypalReturnMode(location), outcome);
  assert.equal(location.hash, '#/order-confirmation&token=4D100105X1957711S&PayerID=3AEHFAN7TUYQ6');
 }
 assert.equal(normalizePayPalReturnHash('#/order-confirmation&PayerID=payer&token=provider'), '#/order-confirmation?PayerID=payer&token=provider');
});
const order={status:'NEW',paymentMethod:'PAYPAL',paidAt:null,refundStatus:'NONE',paymentReviewRequired:false};
const ready={paid:false,canResumeApproval:true,approvalUrl:'https://www.sandbox.paypal.com/checkoutnow?token=provider',refundStatus:'NONE',financialStatus:'UNCONFIRMED',paymentReviewRequired:false};
test('approval requires explicit backend permission and safe financial state, never callback IDs',()=>{
 assert.equal(resumableApproval(ready),ready.approvalUrl);
 for(const change of [{canResumeApproval:false},{canResumeApproval:undefined},{paid:true},{paymentReviewRequired:true},{refundStatus:'REQUIRED'},{financialStatus:'CAPTURED'},{financialStatus:undefined},{approvalUrl:'https://evil.test/'},{token:'provider',PayerID:'payer',canResumeApproval:false}])assert.equal(resumableApproval({...ready,...change}),null);
});
test('receipt states separate verification, approval, capture, holds and refunds',()=>{
 assert.equal(paypalView(order,null),'checking');
 assert.equal(paypalView(order,ready,{finished:true}),'approval');
 assert.equal(paypalView(order,{...ready,canResumeApproval:false},{finished:true}),'delayed');
 assert.equal(paypalView(order,{paid:true},{finished:true}),'paid');
 assert.equal(paypalView({...order,paidAt:'verified'},null),'paid');
 assert.equal(paypalView({...order,paymentReviewRequired:true},{paid:true}),'review');
 assert.equal(paypalView({...order,paidAt:'verified',refundStatus:'REQUIRED'},null),'refund');
 assert.equal(paypalView({...order,status:'CANCELLED'},ready),'closed');
 assert.equal(paypalView(order,ready,{busy:true}),'checking');
 assert.equal(paypalView(order,{canStartPayment:true},{finished:true}),'approval');
});
test('verification has a short bounded backoff rather than indefinite browser checks',()=>{
 assert.deepEqual(PAYPAL_CHECK_DELAYS,[0,2000,5000,10000,20000]);
 assert.equal(PAYPAL_CHECK_DELAYS.reduce((sum,n)=>sum+n,0),37000);
});
test('normal confirmation and both valid query placements remain unchanged', () => {
 for (const url of [
  'https://example.test/#/order-confirmation',
  'https://example.test/?paypal=return&token=provider&PayerID=payer#/order-confirmation',
  'https://example.test/#/order-confirmation?paypal=cancel&token=provider&PayerID=payer',
 ]) {
  const location = new URL(url);
  assert.equal(normalizePayPalReturnHash(location.hash), location.hash);
  assert.equal(normalizePayPalReturnHash(location.hash).split('?')[0], '#/order-confirmation');
 }
});
test('PayPal normalization preserves other routes and rejects confirmation near-matches', () => {
 for (const hash of ['#/menu', '#/menu?collection=lunch', '#/staff/orders?order=id', '#/track-order?t=private',
  '#/order-confirmation-extra&token=provider', '#/order-confirmationevil&PayerID=payer',
  '#/order-confirmation/extra&token=provider', '#/order-confirmation&notToken=provider',
  '#/order-confirmation&tokenized=provider', '#/order-confirmation%26token=provider',
  '#/staff/orders&token=provider', '#menu', '']) {
  assert.equal(normalizePayPalReturnHash(hash), hash);
  assert.notEqual(normalizePayPalReturnHash(hash).split('?')[0], '#/order-confirmation');
 }
});
test('PayPal return and cancel markers work before and after hash',()=>{
 assert.equal(paypalReturnMode({search:'?paypal=return&token=PP',hash:'#/order-confirmation'}),'return');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation?paypal=cancel'}),'cancel');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation'}),'');
});
test('only HTTPS PayPal approval destinations can be presented',()=>{
 assert.equal(safeApprovalUrl('https://www.sandbox.paypal.com/checkoutnow?token=PP'),'https://www.sandbox.paypal.com/checkoutnow?token=PP');
 for(const url of ['javascript:alert(1)','http://www.paypal.com/','https://paypal.com.evil.test/','https://user@paypal.com/','https://evil.test/',undefined])assert.equal(safeApprovalUrl(url),null);
});
