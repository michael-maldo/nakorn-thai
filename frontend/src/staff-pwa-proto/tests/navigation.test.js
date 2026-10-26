import test from 'node:test';
import assert from 'node:assert/strict';
import {visibleRoutes,operationRoutes,managementRoutes,canAccess} from '../app/routes.js';
test('all authorized destinations partition without duplication or changed permissions',()=>{
 for(const role of ['ADMIN','FOH','BOH','UNKNOWN']){
 const a=operationRoutes(role),b=managementRoutes(role);
 assert.deepEqual(a.map(x=>x.label),role==='BOH'?['Home','Orders']:role==='FOH'?['Home','Orders','Reservations']:role==='UNKNOWN'?[]:['Home','Orders','Reservations','Function enquiries']);
 assert.equal(new Set([...a,...b]).size,a.length+b.length);
 assert.deepEqual(new Set([...a,...b]),new Set(visibleRoutes(role)));
 }
 assert.equal(managementRoutes('ADMIN').length,6);
 assert.deepEqual(managementRoutes('FOH'),[]);
 assert.equal(canAccess('#/staff/ordering','FOH'),false);
 assert.equal(canAccess('#/staff/functions','FOH'),false);
 assert.deepEqual(managementRoutes('BOH'),[]);
 assert.equal(canAccess('#/staff/menu/items/dish/images','ADMIN'),true);
 assert.equal(canAccess('#/staff/menu/items/dish/images','FOH'),false);
});
