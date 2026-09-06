import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

// Use the existing Vite JSX transform and React renderer, without browser/E2E dependencies.
let server, MenuItemCard, MenuItemOptions, CheckoutSummary;
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, watch: null }, appType: 'custom', logLevel: 'error' });
  MenuItemCard = (await server.ssrLoadModule('/src/domains/menu/components/MenuItemCard.jsx')).default;
  MenuItemOptions = (await server.ssrLoadModule('/src/domains/menu/components/MenuItemOptions.jsx')).default;
  CheckoutSummary = (await server.ssrLoadModule('/src/domains/ordering/components/CheckoutSummary.jsx')).default;
});
after(async () => { await server?.close(); });
const collection = { id: 'c', slug: 'discovered', name: 'New menu', availability: { available: true } };
const dish = { id: 'dish', name: 'Curry', available: true, variations: [{ id: 'v', name: 'Standard', priceMinor: 0, variationBasePriceMinor: 2490, available: true, defaultVariation: true }],
  optionGroups: [] };

test('unavailable collection disables adding even if variation is available', () => {
  const html = renderToStaticMarkup(createElement(MenuItemCard, { item: dish, collection: { ...collection, availability: { available: false } }, enabled: true, cart: [], onAdd() {} }));
  assert.match(html, /disabled="">Add to order/);
});
test('effective zero price renders without falling back to original variation price', () => {
  const html = renderToStaticMarkup(createElement(MenuItemCard, { item: dish, collection, enabled: true, cart: [], onAdd() {} }));
  assert.match(html, /\$0\.00 per dish/); assert.doesNotMatch(html, /24\.90/);
  assert.doesNotMatch(html, /disabled="">Add to order/);
});
test('required SINGLE renders a prompt and disables adding before selection', () => {
  const item = { ...dish, optionGroups: [{ id: 'g', name: 'Protein', active: true, selectionType: 'SINGLE', minSelections: 1, maxSelections: 1,
    options: [{ id: 'o', name: 'Prawns', priceDeltaMinor: 600, available: true }] }] };
  const html = renderToStaticMarkup(createElement(MenuItemCard, { item, collection, enabled: true, cart: [], onAdd() {} }));
  assert.match(html, /Choose an option/); assert.match(html, /Choose at least 1 from Protein/);
  assert.match(html, /disabled="">Add to order/);
});
test('unavailable options are disabled in the rendered selector', () => {
  const groups = [{ id: 'g', name: 'Protein', active: true, selectionType: 'SINGLE', minSelections: 0, maxSelections: 1,
    options: [{ id: 'o', name: 'Prawns', available: false, priceDeltaMinor: 600 }] }];
  const html = renderToStaticMarkup(createElement(MenuItemOptions, { groups, selections: [], onChange() {} }));
  assert.match(html, /value="o" disabled=""/); assert.match(html, /unavailable/);
});
test('confirmation displays stored collection and option snapshots without menu reconstruction', () => {
  const line = { dishName: 'Historical Curry', variationName: 'Standard', collectionName: 'Old Lunch', collectionSlug: 'deleted-lunch', quantity: 2, unitPriceMinor: 3200,
    selectedOptions: [{ optionId: 'deleted-option', optionGroupName: 'Protein', optionName: 'Old Prawns', quantity: 2, priceDeltaMinor: 600 }] };
  const html = renderToStaticMarkup(createElement(CheckoutSummary, { line, historical: true }));
  assert.match(html, /Old Lunch/); assert.match(html, /Protein: Old Prawns × 2 per dish/);
  assert.match(html, /\+\$6\.00 each/); assert.match(html, /\$32\.00 per dish/); assert.match(html, /\$64\.00/);
});
test('legacy order snapshots without collection or options remain readable', () => {
  const html = renderToStaticMarkup(createElement(CheckoutSummary, { historical: true,
    line: { dishName: 'Legacy Rice', variationName: 'Standard', quantity: 1, unitPriceMinor: 1000, collectionName: null, selectedOptions: [] } }));
  assert.match(html, /Legacy Rice/); assert.match(html, /\$10\.00/); assert.doesNotMatch(html, /undefined/);
});
