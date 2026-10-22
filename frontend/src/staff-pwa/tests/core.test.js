import test from 'node:test';
import { printConfirmedAcceptance } from '../features/printing/acceptancePrinting.js';
import assert from 'node:assert/strict';
import { createPendingTracker, createSoundEngine, normalizeAlertSettings } from '../features/notifications/alerts.js';
import { docketText, eposEnvelope, printerUrl, createPrinter, printerDefaults, printingEnabled, orderDocketXml } from '../features/printing/epos.js';
import { docketLines } from '../features/printing/dockets.js';
import { paymentText } from '../features/orders/presentation.js';
const order = { id: 'one', status: 'ACCEPTED', reference: 'NT-42', customerName: 'Guest & <family>', email: 'guest@example.test', fulfilment: 'PICKUP', currency: 'AUD', subtotalMinor: 4800, paymentStatus: 'PAID', paymentReference: 'CAPTURE-ABC123', phone: '0412345678', createdAt: '2026-10-22T01:00:00Z', estimatedReadyAt: '2026-10-22T01:20:00Z', totalMinor: 4800, paymentMethod: 'PAYPAL', paidAt: '2026-10-22T01:01:00Z', notes: 'No peanuts', items: [{ dishName: 'Curry', variationName: 'Chicken', quantity: 2, unitPriceMinor: 2400, lineTotalMinor: 4800, selectedOptions: [{ optionName: 'Hot', optionGroupName: 'Spice', quantity: 1 }] }] };
const config = { ...printerDefaults, endpoint: 'https://192.168.1.100' };
test('pending tracker establishes silent baseline, suppresses repeats, survives reconnection and clears acknowledgements', () => {
  const tracker = createPendingTracker();
  assert.deepEqual(tracker.accept([{ id: 'first', status: 'NEW' }]), { arrived: false, count: 0 });
  assert.deepEqual(tracker.accept([{ id: 'first', status: 'NEW' }, { id: 'next', status: 'NEW' }]), { arrived: true, count: 1 });
  assert.deepEqual(tracker.accept([{ id: 'next', status: 'NEW' }]), { arrived: false, count: 1 });
  // Failed reads never call accept, so no baseline reset or duplicate after reconnection.
  assert.deepEqual(tracker.accept([{ id: 'first', status: 'NEW' }, { id: 'next', status: 'NEW' }]), { arrived: false, count: 1 });
  tracker.acknowledge(); assert.equal(tracker.accept([{ id: 'next', status: 'NEW' }]).count, 0);
  assert.deepEqual(tracker.accept([{ id: 'next', status: 'ACCEPTED' }]), { arrived: false, count: 0 });
});
test('audio activation is explicit and interruptions return visible disabled state', async () => {
  let created = 0, nodes = 0, context, ready;
  class Context {
    constructor() { created++; context = this; this.state = 'suspended'; this.currentTime = 0; }
    async resume() { this.state = 'running'; }
    createOscillator() { nodes++; return { frequency: {}, connect() {}, start() {}, stop() {}, disconnect() {} }; }
    createGain() { return { gain: { setValueAtTime() {}, linearRampToValueAtTime() {}, exponentialRampToValueAtTime() {} }, connect() {}, disconnect() {} }; }
    async close() { this.state = 'closed'; }
  }
  const engine = createSoundEngine(Context, value => { ready = value; });
  assert.equal(created, 0); assert.equal(engine.play({ volume: 1 }), false);
  await engine.enable(); assert.equal(ready, true); assert.equal(engine.play({ sound: 'chime', volume: .3 }), true); assert.equal(nodes, 2);
  context.state = 'interrupted'; context.onstatechange(); assert.equal(ready, false); assert.equal(engine.play({}), false);
  await engine.enable(); assert.equal(engine.ready(), true); engine.close();
});
test('sound settings sanitize volume and supported choices', () => {
  assert.deepEqual(normalizeAlertSettings({ volume: 2, sound: 'unknown' }), { volume: 1, sound: 'double' });
  assert.equal(normalizeAlertSettings({ volume: -1 }).volume, 0);
  assert.equal(normalizeAlertSettings({ volume: 'bad' }).volume, .65);
});
test('kitchen and detailed dockets preserve snapshots and distinct privacy boundaries', () => {
  const kitchen = docketText(order, 'kitchen'), detailed = docketText(order, 'detailed');
  for (const text of [kitchen, detailed]) for (const expected of ['2 x Curry', 'Chicken', 'Hot', 'No peanuts', '#NT-42']) assert.ok(text.includes(expected), expected);
  assert.ok(kitchen.includes('KITCHEN ORDER')); assert.ok(kitchen.includes('2 x Curry / Chicken'));
  assert.ok(!kitchen.includes(order.customerName)); assert.ok(!kitchen.includes(order.email)); assert.ok(!kitchen.includes('PAYPAL')); assert.ok(!kitchen.includes('CAPTURE-ABC123'));
  for (const expected of ['CUSTOMER INFORMATION', 'FULFILLMENT', 'PICKUP / TAKEAWAY', 'PAYMENT INFORMATION', 'CAPTURE-ABC123', 'PAYPAL ONLINE', 'PAID', 'ORDER ITEMS', 'Unit Price', 'Line Total', 'ORDER SUMMARY', 'Subtotal', 'TOTAL', 'CUSTOMER INSTRUCTIONS', 'END OF ORDER', order.customerName, order.phone, order.email, '$48.00']) assert.ok(detailed.includes(expected), expected);
  assert.throws(() => docketText({ ...order, status: 'NEW' }, 'detailed'), /server-confirmed/);
  assert.throws(() => docketText(order, 'unknown'), /layout/);
});
test('payment text never equates historical paidAt with settled payment under review', () => {
  assert.match(paymentText(order), /server verified/);
  assert.match(paymentText({ ...order, paymentReviewRequired: true }), /review/);
  assert.match(paymentText({ ...order, refundStatus: 'REQUIRED' }), /Refund: REQUIRED/);
  assert.match(paymentText({ ...order, paidAt: null }), /not verified/);
  assert.match(paymentText({ ...order, paidAt: null, paymentMethod: 'PAY_AT_RESTAURANT' }), /due at handover/);
});
test('XML escapes customer input, strips controls and requests feed then cut without raw TCP', () => {
  const xml = eposEnvelope(docketText(order, 'detailed') + '\u0001');
  assert.ok(xml.includes('Guest &amp; &lt;family&gt;')); assert.ok(!xml.includes('\u0001'));
  assert.ok(xml.includes('<feed line="3"/><cut type="feed"/>'));
  assert.ok(xml.includes('http://schemas.xmlsoap.org/soap/envelope/'));
  assert.ok(!eposEnvelope('', { cut: false }).includes('<cut'));
});
test('printer endpoints reject plaintext, embedded credentials and arbitrary paths', () => {
  assert.equal(printerUrl(config), 'https://192.168.1.100/cgi-bin/epos/service.cgi?devid=local_printer&timeout=10000');
  for (const endpoint of ['http://192.168.1.100', 'https://user:pass@printer.test', 'https://printer.test/a', 'https://printer.test/?secret=x']) assert.throws(() => printerUrl({ ...config, endpoint }));
});
test('printing and automatic printing default off and cannot dispatch jobs', async () => {
  assert.equal(printingEnabled, false); assert.equal(printerDefaults.autoPrint, false);
  const printer = createPrinter({ fetcher() { assert.fail('must not send'); } });
  await assert.rejects(printer.printOrder(config, order), /disabled/);
  await assert.rejects(printer.test(config), /disabled/);
  await assert.rejects(printer.check(config), /disabled/);
});
test('offline and uncertain sends are not retried', async () => {
  let sends = 0;
  const printer = createPrinter({ enabled: true, fetcher: async () => { sends++; throw new TypeError('Network error'); } });
  await assert.rejects(printer.printOrder(config, order), /uncertain.*No automatic retry/);
  assert.equal(sends, 1);
});
test('timeout aborts once and duplicate sends are rejected', async () => {
  let sends = 0;
  const printer = createPrinter({ enabled: true, timeoutMs: 10, fetcher: (_url, options) => { sends++; return new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('Timeout')))); } });
  const first = printer.test(config);
  await assert.rejects(printer.test(config), /already in progress/);
  await assert.rejects(first, /uncertain/); assert.equal(sends, 1);
});

test('only confirmed acceptance and reload can trigger one enabled print; failures never undo acceptance', async () => {
  let sends = 0;
  const request = { order, target: 'ACCEPTED', enabled: true, autoPrint: true, current: () => true, send: async () => { sends++; } };
  for (const change of [{ order: undefined }, { order: { ...order, status: 'NEW' } }, { enabled: false }, { autoPrint: false }, { target: 'PREPARING' }, { current: () => false }]) assert.equal(await printConfirmedAcceptance({ ...request, ...change }), false);
  assert.equal(sends, 0);
  assert.equal(await printConfirmedAcceptance(request), true); assert.equal(sends, 1);
  await assert.rejects(printConfirmedAcceptance({ ...request, send: async () => { throw new Error('Offline'); } }), /Offline/);
  assert.equal(order.status, 'ACCEPTED');
});

test('Android detailed docket sections wrap to 58/80mm and ePOS preserves hierarchy and special instruction emphasis', () => {
  const sample = { ...order, email: 'a.very.long.email.for.staff.docket@example.test', items: [{ ...order.items[0], selectedOptions: [{ optionName: 'NO PEANUTS', optionGroupName: 'Allergy note', quantity: 2 }] }] };
  for (const [paperWidth, width] of [[58,32],[80,48]]) {
    const lines = docketLines(sample, 'detailed', { paperWidth, pickupMinutes: 20 });
    assert.ok(lines.every(line => line.text.length <= (line.large ? width/2 : width)));
    const text = lines.map(line => line.text).join('\n');
    assert.ok(text.replaceAll('\n','').includes(sample.email));
    assert.ok(text.includes('Prep Estimate : 20 min'));
    assert.ok(lines.some(line => line.bold && line.text.includes('NO PEANUTS')));
    assert.ok(text.replace(/\s+/g, ' ').includes('x 2 per dish'));
    const xml = orderDocketXml(sample, { ...config, layout: 'detailed', paperWidth });
    assert.ok(xml.includes('align="center" em="true" width="2" height="2"'));
    assert.ok(xml.includes('END OF ORDER')); assert.ok(xml.endsWith('</epos-print></s:Body></s:Envelope>'));
    assert.ok(xml.includes('<feed line="3"/><cut type="feed"/>'));
  }
  const review = docketText({ ...sample, paymentReviewRequired: true }, 'detailed');
  assert.match(review, /Financial review/);
});
