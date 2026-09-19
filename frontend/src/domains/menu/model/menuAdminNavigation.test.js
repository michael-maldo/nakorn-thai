import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseMenuAdminRoute, menuAdminHref, registerMenuGuard, allowMenuNavigation, hasMenuEdits, availabilityLabel, money, instantFromInput, instantToInput } from './menuAdminNavigation.js';

test('Menu opens the item index; Items and Collections have independent URLs', () => {
  assert.deepEqual(parseMenuAdminRoute('#/staff/menu'), { resource: 'items', id: null, section: 'overview' });
  for (const resource of ['items', 'collections']) assert.deepEqual(parseMenuAdminRoute(menuAdminHref(resource)), { resource, id: null, section: 'overview' });
});
test('detail and section URLs survive refresh and support arbitrary data-driven collection IDs', () => {
  for (const id of ['main-id', 'lunch-id', 'seasonal-id']) for (const section of ['overview', 'items', 'categories', 'availability']) {
    assert.deepEqual(parseMenuAdminRoute(menuAdminHref('collections', id, section)), { resource: 'collections', id, section });
  }
  for (const section of ['overview', 'pricing', 'collections', 'images']) assert.deepEqual(parseMenuAdminRoute(menuAdminHref('items', 'dish', section)), { resource: 'items', id: 'dish', section });
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
