import { test } from 'node:test';
import assert from 'node:assert/strict';
import { evaluateOptions, changeOptionSelection, normalizeSelectedOptions } from './menuOptions.js';
import { collectionAvailability, selectCollection, menuSections } from './menuCollections.js';
import { createCartLine } from '../../ordering/model/cartModel.js';

const group = (extra = {}) => ({ id: 'g', name: 'Protein', active: true, selectionType: 'SINGLE', minSelections: 1, maxSelections: 1,
  options: [{ id: 'a', name: 'Prawns', available: true, priceDeltaMinor: 600 }, { id: 'b', name: 'Beef', available: false, priceDeltaMinor: 0 }], ...extra });

test('required SINGLE needs exactly one available option with quantity one', () => {
  assert.equal(evaluateOptions([group()], []).valid, false);
  assert.equal(evaluateOptions([group()], [{ optionId: 'a', quantity: 1 }]).valid, true);
  assert.equal(evaluateOptions([group()], [{ optionId: 'a', quantity: 2 }]).valid, false);
  assert.equal(evaluateOptions([group()], [{ optionId: 'a', quantity: 1 }, { optionId: 'b', quantity: 1 }]).valid, false);
});
test('MULTIPLE min and max count option quantities per dish', () => {
  const groups = [group({ selectionType: 'MULTIPLE', minSelections: 2, maxSelections: 3 })];
  assert.equal(evaluateOptions(groups, [{ optionId: 'a', quantity: 1 }]).valid, false);
  assert.equal(evaluateOptions(groups, [{ optionId: 'a', quantity: 2 }]).valid, true);
  assert.equal(evaluateOptions(groups, [{ optionId: 'a', quantity: 3 }]).deltaMinor, 1800);
  assert.equal(evaluateOptions(groups, [{ optionId: 'a', quantity: 4 }]).valid, false);
  const selected = [{ optionId: 'a', quantity: 3 }];
  assert.deepEqual(changeOptionSelection(groups, selected, 'g', 'a', 4), selected);
});
test('unavailable, foreign, duplicate and nonpositive selections are rejected', () => {
  assert.deepEqual(changeOptionSelection([group()], [], 'g', 'b', 1), []);
  for (const selection of [[{ optionId: 'b', quantity: 1 }], [{ optionId: 'foreign', quantity: 1 }],
    [{ optionId: 'a', quantity: 0 }], [{ optionId: 'a', quantity: 1.5 }], [{ optionId: 'a', quantity: 21 }],
    [{ optionId: 'a', quantity: 1 }, { optionId: 'A', quantity: 1 }]])
    assert.equal(evaluateOptions([group()], selection).valid, false);
  assert.equal(evaluateOptions([group({ active: false })], [{ optionId: 'a', quantity: 1 }]).valid, false);
});
test('normalization sorts option IDs and retains quantities without display metadata', () => {
  assert.deepEqual(normalizeSelectedOptions([{ optionId: 'Z', quantity: 2, optionName: 'Extra' }, { optionId: 'A', quantity: 1 }]),
    [{ optionId: 'a', quantity: 1 }, { optionId: 'z', quantity: 2 }]);
});
test('effective base includes a zero override already applied by backend', () => {
  const collection = { id: 'c', name: 'Lunch', slug: 'lunch' };
  const dish = { id: 'dish', name: 'Curry', priceOverrideMinor: 0, optionGroups: [group()] };
  const variation = { id: 'v', name: 'Standard', priceMinor: 0, variationBasePriceMinor: 2490 };
  const line = createCartLine(collection, dish, variation, [{ optionId: 'a', quantity: 1 }]);
  assert.equal(line.basePriceMinor, 0); assert.equal(line.unitPriceMinor, 600);
  // Non-default effective price is also supplied by the backend; never apply the item override here.
  assert.equal(createCartLine(collection, { ...dish, optionGroups: [] }, { ...variation, priceMinor: 2990 }, []).unitPriceMinor, 2990);
});
test('collection selection only chooses discovered candidates and availability labels do not calculate schedules', () => {
  const closed = { id: 'closed', availability: { available: false, reason: 'OUTSIDE_SCHEDULE' } };
  const open = { id: 'open', availability: { available: true }, startsAt: '2999-01-01' };
  assert.equal(selectCollection([closed, open], 'removed'), open);
  assert.equal(selectCollection([closed, open], 'closed'), closed);
  assert.equal(selectCollection([], 'unknown'), null);
  assert.match(collectionAvailability(closed), /outside its ordering hours/);
  assert.equal(collectionAvailability(open), '');
});
test('category sections consume effective placement and membership order', () => {
  const menu = { categories: [{ id: 'special', name: 'Special' }], items: [
    { id: 'a', name: 'Curry', category: { id: 'special' }, categoryId: 'old', displayOrder: 4 },
    { id: 'b', name: 'Rice', category: { id: 'special' }, displayOrder: 1 }] };
  assert.deepEqual(menuSections(menu)[0].items.map((item) => item.id), ['b', 'a']);
  assert.deepEqual(menuSections(menu, 'curry')[0].items.map((item) => item.id), ['a']);
});
