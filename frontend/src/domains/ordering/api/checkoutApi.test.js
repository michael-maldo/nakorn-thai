import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { refreshCartPrices } from './checkoutApi.js';
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const dish = (id, priceMinor, available = true) => ({ available, variations: [{ id, priceMinor, available: true }] });
const line = (id) => ({ variationId: id, dishName: id, unitPriceMinor: 100, quantity: 2 });
function catalog(data, failures = {}) {
  globalThis.fetch = async (url) => {
    const slug = url.split('/')[4];
    return Response.json({ items: data[slug] || [] }, { status: failures[slug] || 200 });
  };
}
test('mixed collection cart rechecks Chef, regular menu and drinks prices', async () => {
  catalog({ 'chefs-special-recommendations': [dish('chef',1190)], 'regular-menu': [dish('rice',2090)], drinks: [dish('tea',999)] });
  const cart = ['chef','rice','tea'].map(line);
  const updated = await refreshCartPrices(cart);
  assert.deepEqual(updated.map(i => i.unitPriceMinor), [1190,2090,999]);
  assert.deepEqual(updated.map(i => i.quantity), [2,2,2]);
  assert.equal(cart[0].unitPriceMinor,100);
});
test('unavailable lunch and unavailable variations cannot pass checkout', async () => {
  catalog({ 'lunch-specials': [dish('lunch',1490,false)] });
  await assert.rejects(refreshCartPrices([line('lunch')]), /no longer available/);
  const unavailable = dish('rice',2090); unavailable.variations[0].available = false;
  catalog({ 'regular-menu': [unavailable] });
  await assert.rejects(refreshCartPrices([line('rice')]), /no longer available/);
});
test('missing collection is skipped but missing cart item is rejected', async () => {
  catalog({ drinks: [dish('tea',999)] }, { 'chefs-special-recommendations': 404 });
  assert.equal((await refreshCartPrices([line('tea')]))[0].unitPriceMinor,999);
  await assert.rejects(refreshCartPrices([line('removed')]), /no longer available/);
});
test('server failure stops validation rather than using a partial catalog', async () => {
  catalog({ drinks: [dish('tea',999)] }, { 'regular-menu': 503 });
  await assert.rejects(refreshCartPrices([line('tea')]), /service is unavailable/);
});
