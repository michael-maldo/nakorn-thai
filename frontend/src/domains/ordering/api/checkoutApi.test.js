import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { refreshCartPrices, prepareCheckout, resumeOrder, recoverCheckoutFailure } from './checkoutApi.js';
import { createCartLine, orderLines } from '../model/cartModel.js';
const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });
const offer = (id = 'c', base = 2000) => ({ id, slug: `discovered-${id}`, name: `Menu ${id}`, availability: { available: true }, categories: [], items: [
  { id: 'dish', name: 'Curry', available: true, variations: [{ id: 'v', name: 'Standard', available: true, priceMinor: base }], optionGroups: [
    { id: 'g', name: 'Protein', active: true, selectionType: 'MULTIPLE', minSelections: 0, maxSelections: 3,
      options: [{ id: 'o', name: 'Prawns', available: true, priceDeltaMinor: 600 }] }] }] });
const line = (menu = offer(), options = []) => createCartLine(menu, menu.items[0], menu.items[0].variations[0], options);
function catalog(menus, extra = async () => { throw new Error('Unexpected request'); }) {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push(url);
    if (url === '/api/menu/collections') return Response.json(menus.map(({ items, categories, ...collection }) => collection));
    const menu = menus.find((entry) => url === `/api/menu/collections/${entry.slug}/items`);
    if (menu) return Response.json(menu);
    return extra(url, options);
  };
  return calls;
}
const pending = (cart) => ({ requestId: 'request', trackingToken: 'secret', customerName: 'Test', phone: '0400000000', notes: '',
  email: null, paymentMethod: 'PAY_AT_RESTAURANT', items: orderLines(cart) });

test('refresh uses discovered identity and never collapses prices across collections', async () => {
  const first = offer('c', 2100), second = offer('other', 1000);
  const calls = catalog([first, second]);
  const updated = await refreshCartPrices([line(offer()), line(offer('other'))]);
  assert.deepEqual(updated.map((entry) => entry.unitPriceMinor), [2100, 1000]);
  assert.equal(calls.filter((url) => url.includes('/items')).length, 2);
});
test('refresh follows a renamed slug by collection ID', async () => {
  const menu = offer(); menu.slug = 'renamed-menu';
  catalog([menu]);
  assert.equal((await refreshCartPrices([line()]))[0].collectionSlug, 'renamed-menu');
});
test('unavailable collection, dish, variation, option and missing membership block submission', async () => {
  const options = [{ optionId: 'o', quantity: 1 }];
  const cart = [line(offer(), options)];
  for (const mutate of [
    (menu) => { menu.availability = { available: false, reason: 'OUTSIDE_SCHEDULE' }; },
    (menu) => { menu.items[0].available = false; },
    (menu) => { menu.items[0].variations[0].available = false; },
    (menu) => { menu.items[0].optionGroups[0].options[0].available = false; },
    (menu) => { menu.items = []; },
  ]) {
    const menu = offer(); mutate(menu); catalog([menu]);
    await assert.rejects(prepareCheckout(cart), (error) => error.lines[0].issue.length > 0);
  }
  catalog([]);
  await assert.rejects(prepareCheckout(cart), (error) => /no longer offered/.test(error.lines[0].issue));
});
test('base or option price changes require review and preserve configured selection', async () => {
  const selected = [{ optionId: 'o', quantity: 2 }];
  const cart = [line(offer(), selected)];
  const menu = offer(); menu.items[0].optionGroups[0].options[0].priceDeltaMinor = 650;
  catalog([menu]);
  await assert.rejects(prepareCheckout(cart), (error) => /changed/.test(error.message) && error.lines[0].unitPriceMinor === 3300);
  const refreshed = await refreshCartPrices(cart);
  assert.equal((await prepareCheckout(refreshed)).items[0].expectedUnitPriceMinor, 3300);
  assert.deepEqual((await prepareCheckout(refreshed)).items[0].selectedOptions, selected);
});
test('zero effective price stays zero during refresh', async () => {
  const menu = offer('c', 0); menu.items[0].priceOverrideMinor = 0; menu.items[0].variations[0].variationBasePriceMinor = 2490;
  catalog([menu]);
  const result = await prepareCheckout([line(menu)]);
  assert.equal(result.items[0].expectedUnitPriceMinor, 0);
});
test('menu transport failures prevent submission rather than using partial prices', async () => {
  globalThis.fetch = async (url) => url === '/api/menu/collections'
    ? Response.json([{ id: 'c', slug: 'menu', name: 'Menu', availability: { available: true } }]) : new Response(null, { status: 503 });
  await assert.rejects(prepareCheckout([line()]), /service is unavailable/);
});
test('409 recovery checks the saved order before clearing and refreshes changed cart', async () => {
  catalog([offer('c', 2200)], async () => new Response(null, { status: 404 }));
  const failure = Object.assign(new Error('A price changed'), { status: 409 });
  const result = await recoverCheckoutFailure(failure, pending([line()]), [line()]);
  assert.equal(result.clearPending, true); assert.equal(result.lines[0].unitPriceMinor, 2200);
  assert.match(result.message, /Review the current cart/);
});
test('409 missing membership marks cart line instead of submitting from another collection', async () => {
  catalog([offer('other')], async () => new Response(null, { status: 404 }));
  const result = await recoverCheckoutFailure(Object.assign(new Error('Dish is not in the selected collection'), { status: 409 }), pending([line()]), [line()]);
  assert.match(result.lines[0].issue, /no longer offered/);
});
test('uncertain 409 lookup preserves pending request; successful lookup uses stored result', async () => {
  const failure = Object.assign(new Error('Conflict'), { status: 409 });
  globalThis.fetch = async () => new Response(null, { status: 503 });
  assert.equal((await recoverCheckoutFailure(failure, pending([line()]), [line()])).clearPending, false);
  globalThis.fetch = async () => Response.json({ id: 'request', items: [] });
  assert.equal((await recoverCheckoutFailure(failure, pending([line()]), [line()])).order.id, 'request');
});
test('400 recovery retains backend message and asks for details/options review', async () => {
  catalog([offer()], async () => new Response(null, { status: 404 }));
  const result = await recoverCheckoutFailure(Object.assign(new Error('Invalid selections'), { status: 400 }), pending([line()]), [line()]);
  assert.equal(result.clearPending, true); assert.match(result.message, /Check your details and option selections/);
});
test('saved replay returns historical order without current availability checks', async () => {
  const calls = [];
  globalThis.fetch = async (url) => { calls.push(url); return Response.json({ id: 'request', totalMinor: 2000 }); };
  assert.equal((await resumeOrder(pending([line()]))).totalMinor, 2000);
  assert.deepEqual(calls, ['/api/orders/request']);
});
test('uncommitted pending attempt refreshes offers then sends exact original payload', async () => {
  const payload = pending([line(offer(), [{ optionId: 'o', quantity: 1 }])]);
  let submitted;
  catalog([offer()], async (url, options) => {
    if (url === '/api/orders/request') return new Response(null, { status: 404 });
    if (url === '/api/orders/csrf') return Response.json({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    assert.equal(url, '/api/orders'); submitted = options.body; return Response.json({ id: 'request' });
  });
  await resumeOrder(payload);
  assert.equal(submitted, JSON.stringify(payload));
});
test('changed uncommitted pending offers and legacy submissions are never silently sent', async () => {
  const calls = catalog([offer('c', 2300)], async () => new Response(null, { status: 404 }));
  await assert.rejects(resumeOrder(pending([line()])), (error) => error.clearPending && error.lines[0].unitPriceMinor === 2300);
  const legacy = pending([line()]); delete legacy.items[0].collectionId;
  await assert.rejects(resumeOrder(legacy), (error) => error.clearPending && /previous menu/.test(error.message));
  assert.equal(calls.includes('/api/orders'), false);
});
