import {test} from 'node:test';
import assert from 'node:assert/strict';
import {orderLink} from './orderLinks.js';
const id='abcd1234-0000-4000-8000-000000000001';
test('customer and staff links use UUID locators only',()=>{
 for(const route of ['track-order','staff/orders'])assert.deepEqual(orderLink(`#/${route}?order=${id}`),{orderId:id,invalid:false});
 assert.deepEqual(orderLink('#/track-order'),{orderId:null,invalid:false});
});
test('invalid, duplicated and redirect input never grants access',()=>{
 for(const value of ['bad','https://evil.test','<script>'])assert.deepEqual(orderLink('#/track-order?order='+encodeURIComponent(value)),{orderId:null,invalid:true});
 assert.equal(orderLink(`#/track-order?order=${id}&order=${id}`).invalid,true);
 assert.deepEqual(orderLink(`#/track-order?order=${id}&token=secret&returnUrl=https://evil.test`),{orderId:id,invalid:false});
});
