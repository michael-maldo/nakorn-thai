import { test } from 'node:test';
import assert from 'node:assert/strict';
import { itemOptionAssignment, optionPriceDraft, optionPriceMinor } from './itemOptionPrices.js';

test('option prices use exact cents and reject missing or invalid input', () => {
  for (const [input, cents] of [['0', 0], ['0.29', 29], ['2.5', 250], ['9999999.99', 999999999]]) assert.equal(optionPriceMinor(input), cents);
  for (const input of ['', '-1', '1.001', '1e3', 'Infinity', '10000000', 'abc']) assert.throws(() => optionPriceMinor(input));
});
test('reusing a group never inherits another item price and new choices remain unpriced', () => {
  const group = { group: { id: 'protein', version: 4 }, options: [{ id: 'beef' }, { id: 'pork' }] };
  const assignment = { version: 2, data: { prices: [{ optionId: 'beef', priceDeltaMinor: 200 }] } };
  assert.deepEqual(optionPriceDraft(group), { beef: '', pork: '' });
  assert.deepEqual(optionPriceDraft(group, assignment), { beef: '2.00', pork: '' });
  assert.deepEqual(itemOptionAssignment(group, assignment, { minSelections: '1', maxSelections: '1', displayOrder: '0', prices: { beef: '0', pork: '' } }),
    { minSelections: 1, maxSelections: 1, displayOrder: 0, version: 2, groupVersion: 4, prices: [{ optionId: 'beef', priceDeltaMinor: 0 }] });
  assert.equal(assignment.data.prices[0].priceDeltaMinor, 200);
});
