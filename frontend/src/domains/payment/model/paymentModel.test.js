import { test } from 'node:test';
import assert from 'node:assert/strict';
import { paypalReturnMode, safeApprovalUrl } from './paymentModel.js';
test('PayPal return and cancel markers work before and after hash',()=>{
 assert.equal(paypalReturnMode({search:'?paypal=return&token=PP',hash:'#/order-confirmation'}),'return');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation?paypal=cancel'}),'cancel');
 assert.equal(paypalReturnMode({search:'',hash:'#/order-confirmation'}),'');
});
test('only HTTPS PayPal approval destinations can be presented',()=>{
 assert.equal(safeApprovalUrl('https://www.sandbox.paypal.com/checkoutnow?token=PP'),'https://www.sandbox.paypal.com/checkoutnow?token=PP');
 for(const url of ['javascript:alert(1)','http://www.paypal.com/','https://paypal.com.evil.test/','https://user@paypal.com/','https://evil.test/',undefined])assert.equal(safeApprovalUrl(url),null);
});
