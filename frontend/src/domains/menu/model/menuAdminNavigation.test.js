import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseMenuAdminRoute, menuAdminHref, registerMenuGuard, createMenuNavigationApproval, allowMenuNavigation, hasMenuEdits, availabilityLabel, money, instantFromInput, instantToInput } from './menuAdminNavigation.js';

test('Menu opens the item index; Items and Collections have independent URLs', () => {
  assert.deepEqual(parseMenuAdminRoute('#/staff/menu'), { resource: 'items', id: null, section: 'overview' });
  for (const resource of ['items', 'collections']) assert.deepEqual(parseMenuAdminRoute(menuAdminHref(resource)), { resource, id: null, section: 'overview' });
});
test('detail and section URLs survive refresh and support arbitrary data-driven collection IDs', () => {
  for (const id of ['main-id', 'lunch-id', 'seasonal-id']) for (const section of ['overview', 'items', 'categories', 'availability']) {
    assert.deepEqual(parseMenuAdminRoute(menuAdminHref('collections', id, section)), { resource: 'collections', id, section });
  }
  for (const section of ['overview', 'pricing', 'options', 'collections', 'images']) assert.deepEqual(parseMenuAdminRoute(menuAdminHref('items', 'dish', section)), { resource: 'items', id: 'dish', section });
});
test('creation is separate from item and collection lists', () => {
  assert.equal(parseMenuAdminRoute(menuAdminHref('items', 'new')).id, 'new');
  assert.equal(parseMenuAdminRoute(menuAdminHref('collections', 'new')).id, 'new');
  assert.equal(parseMenuAdminRoute('#/staff/menu/items/new/images'), null);
});
test('unknown, malformed and unsupported menu routes fail explicitly', () => {
  for (const route of ['#/staff/orders', '#/staff/menu/options', '#/staff/menu/items/%ZZ', '#/staff/menu/items/dish/schedules', '#/staff/menu/collections/main/items/extra']) assert.equal(parseMenuAdminRoute(route), null);
});
test('unsaved forms protect navigation, while clean and committed forms do not prompt', () => {
  const state = { dirty: false, busy: false }; const unregister = registerMenuGuard(() => state);
  try {
    assert.equal(hasMenuEdits(), false);
    assert.equal(allowMenuNavigation(() => { throw new Error('Unexpected prompt'); }), true);
    state.dirty = true; assert.equal(hasMenuEdits(), true);
    assert.equal(allowMenuNavigation(() => false), false);
    assert.equal(allowMenuNavigation(() => true), true);
    state.dirty = false; assert.equal(allowMenuNavigation(() => false), true);
  } finally { unregister(); }
  assert.equal(hasMenuEdits(), false);
});
test('in-flight writes block navigation even if discard is accepted', () => {
  const unregister = registerMenuGuard(() => ({ dirty: true, busy: true })); let message;
  try { assert.equal(allowMenuNavigation(() => true, value => { message = value; }), false); assert.match(message, /save to finish/); }
  finally { unregister(); }
});
test('availability presentation uses backend results without inferring availability from publication', () => {
  assert.equal(availabilityLabel({ available: false, reason: 'RESTAURANT_CLOSED' }), 'Restaurant closed');
  assert.equal(availabilityLabel({ available: false, reason: 'AFTER_CUTOFF' }), 'Daily cutoff reached');
  assert.equal(availabilityLabel(undefined), 'Not evaluated');
  assert.equal(money(0), '$0.00'); assert.equal(money(1590), '$15.90');
});

test('validity date pickers display UTC and preserve instants across offsets', () => {
  assert.equal(instantToInput('2026-12-01T09:30:00+11:00'), '2026-11-30T22:30:00.000');
  assert.equal(instantFromInput('2026-11-30T22:30'), '2026-11-30T22:30:00.000Z');
  assert.equal(instantFromInput('2026-11-30T22:30:00.123'), '2026-11-30T22:30:00.123Z');
});
test('clearing validity bounds preserves unrestricted null semantics', () => {
  assert.equal(instantToInput(null), '');
  assert.equal(instantFromInput(''), null);
});

test('Settings delegates dirty navigation to an accessible confirmation and resumes only on Leave', () => {
  let leave; let resumed = false;
  const unregister = registerMenuGuard(() => ({dirty:true,busy:false,requestLeave:resume=>{leave=resume;}}));
  try {
    assert.equal(allowMenuNavigation(()=>{throw new Error('Native confirmation must not run');},()=>{},()=>{resumed=true;}),false);
    assert.equal(resumed,false); // Stay leaves the continuation untouched.
    leave();assert.equal(resumed,true);
  } finally { unregister(); }
});

test('Stay, replacement, guard removal and repeated approval invalidate continuations', () => {
  let leave, queued;let calls=0;
  const state={dirty:true,busy:false,requestLeave:action=>{leave=action;}};
  const unregister=registerMenuGuard(()=>state);
  const request=()=>allowMenuNavigation(()=>true,()=>{},ticket=>{queued=ticket;calls++;return true;});
  try {
    request();const cancelled=leave;cancelled.cancel();assert.equal(cancelled(),false);assert.equal(calls,0);
    request();const older=leave;request();assert.equal(older(),false);assert.equal(leave(),true);assert.equal(leave(),false);assert.equal(calls,1);
    request();assert.equal(queued.complete(),false); // New attempt invalidates approved work still queued.
    state.busy=true;assert.equal(leave(),false);state.busy=false;
    leave();unregister();assert.equal(queued.complete(),false);assert.equal(leave(),false);
  } finally {unregister();}
});
test('queued approval cannot execute during a save and can complete only once',()=>{
 let leave,ticket;const state={dirty:true,busy:false,requestLeave:value=>{leave=value;}};
 const unregister=registerMenuGuard(()=>state);
 try {
  allowMenuNavigation(()=>true,()=>{},value=>{ticket=value;return true;});leave();state.busy=true;
  assert.equal(ticket.complete(),false);state.busy=false;assert.equal(ticket.complete(),false);
  allowMenuNavigation(()=>true,()=>{},value=>{ticket=value;return true;});leave();assert.equal(ticket.complete(),true);assert.equal(ticket.complete(),false);
 }finally{unregister();}
});

test('clean navigation queued during restoration also belongs to active guards and respects saves',()=>{
 const state={dirty:false,busy:false};const unregister=registerMenuGuard(()=>state);
 const token=createMenuNavigationApproval();state.busy=true;assert.equal(token.complete(),false);state.busy=false;
 const replacement=createMenuNavigationApproval();unregister();assert.equal(replacement.complete(),false);
});
