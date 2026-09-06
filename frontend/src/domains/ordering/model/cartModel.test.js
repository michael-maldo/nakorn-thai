import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cartReducer, cartTotal } from './cartReducer.js';
import { configurationKey, createCartLine, orderLines, restoreCart, serializeCart } from './cartModel.js';

const makeLine = (collectionId = 'c', selections = []) => createCartLine({ id: collectionId, name: 'Menu', slug: 'menu' },
  { id: 'dish', name: 'Curry', optionGroups: [{ id: 'g', name: 'Extras', active: true, selectionType: 'MULTIPLE', minSelections: 0, maxSelections: 5,
    options: [{ id: 'a', name: 'Prawns', available: true, priceDeltaMinor: 600 }, { id: 'b', name: 'Sauce', available: true, priceDeltaMinor: 100 }] }] },
  { id: 'v', name: 'Standard', priceMinor: 2000 }, selections);

test('identical configuration merges regardless of option ordering, names, price or dish quantity', () => {
  const line = makeLine('c', [{ optionId: 'b', quantity: 1 }, { optionId: 'a', quantity: 2 }]);
  const reordered = { ...line, dishName: 'Renamed', quantity: 9, unitPriceMinor: 3400, selectedOptions: [...line.selectedOptions].reverse() };
  assert.equal(configurationKey(line), configurationKey(reordered));
  let cart = cartReducer([], { type: 'add', line });
  cart = cartReducer(cart, { type: 'add', line: reordered });
  assert.equal(cart.length, 1); assert.equal(cart[0].quantity, 2); assert.equal(cartTotal(cart), 6800);
});
test('same variation in different collections, options, or option quantities remains separate', () => {
  let cart = [];
  for (const line of [makeLine('c'), makeLine('other'), makeLine('c', [{ optionId: 'a', quantity: 1 }]),
    makeLine('c', [{ optionId: 'b', quantity: 1 }]), makeLine('c', [{ optionId: 'a', quantity: 2 }])])
    cart = cartReducer(cart, { type: 'add', line });
  assert.equal(cart.length, 5);
  const changed = cartReducer(cart, { type: 'quantity', id: cart[2].key, quantity: 3 });
  assert.deepEqual(changed.map((line) => line.quantity), [1, 1, 3, 1, 1]);
  assert.equal(cartReducer(changed, { type: 'remove', id: cart[2].key }).length, 4);
});
test('order lines contain collection, normalized options and displayed expected price', () => {
  const line = { ...makeLine('c', [{ optionId: 'b', quantity: 1 }, { optionId: 'a', quantity: 2 }]), quantity: 3 };
  assert.deepEqual(orderLines([line]), [{ collectionId: 'c', variationId: 'v', quantity: 3, expectedUnitPriceMinor: 3300,
    selectedOptions: [{ optionId: 'a', quantity: 2 }, { optionId: 'b', quantity: 1 }] }]);
  assert.equal(cartTotal([line]), 9900);
});
test('legacy cart lacking collection is discarded with an explanation; configured lines survive', () => {
  const legacy = { variationId: 'v', quantity: 1, unitPriceMinor: 2490 };
  const restored = restoreCart(JSON.stringify([legacy, makeLine()]));
  assert.equal(restored.lines.length, 1); assert.match(restored.notice, /could not be restored/);
  assert.throws(() => orderLines([legacy]), /current menu again/);
  assert.deepEqual(restoreCart(serializeCart([makeLine()])), { lines: [makeLine()], notice: '' });
});
test('corrupt, duplicate-option and invalid-price storage cannot be submitted', () => {
  assert.match(restoreCart('{bad').notice, /menu has changed/);
  const line = makeLine('c', [{ optionId: 'a', quantity: 1 }]);
  for (const malformed of [{ ...line, unitPriceMinor: 1 }, { ...line, selectedOptions: [...line.selectedOptions, ...line.selectedOptions] }])
    assert.equal(restoreCart(serializeCart([malformed])).lines.length, 0);
  assert.throws(() => orderLines([{ ...line, issue: 'No longer offered' }]), /No longer offered/);
});
