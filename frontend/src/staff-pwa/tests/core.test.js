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
    constructor() { created++; context = this; this.state = 'suspended'; }
    get currentTime() { return performance.now() / 1000; }
    async resume() { this.state = 'running'; }
    createOscillator() { nodes++; return { frequency: {}, connect() {}, start() {}, stop() {}, disconnect() {} }; }
    createGain() { return { gain: { setValueAtTime() {}, linearRampToValueAtTime() {}, exponentialRampToValueAtTime() {} }, connect() {}, disconnect() {} }; }
    async close() { this.state = 'closed'; }
  }
  const engine = createSoundEngine(Context, value => { ready = value; }, { log() {}, probeMs: 2 });
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

function audioFixture(overrides = {}) {
  const page = new EventTarget(), host = new EventTarget(), contexts = [], logs = [], changes = [], policy = {};
  page.hidden = false; page.visibilityState = 'visible';
  class Context {
    constructor() { this.state = 'suspended'; this.frozen = false; this.graphs = []; this.resumes = 0; this.closed = 0; Object.assign(this, policy); contexts.push(this); }
    get currentTime() { return this.frozen ? 0 : performance.now() / 1000; }
    resume() { this.resumes++; if (this.failure) return Promise.reject(this.failure); if (this.hung) return new Promise(() => {}); if (!this.stuck) this.state = 'running'; return Promise.resolve(); }
    close() { this.closed++; this.state = 'closed'; return Promise.resolve(); }
    createOscillator() {
      if (this.playError) throw new DOMException('Audio resource invalid', 'InvalidStateError');
      const node = { frequency: {}, connect() {}, start() {}, stops: 0, stop() { this.stops++; }, disconnect() {} }; this.graphs.push(node); return node;
    }
    createGain() { return { gain: { setValueAtTime() {}, linearRampToValueAtTime(value) { this.volume = value; }, exponentialRampToValueAtTime() {} }, connect() {}, disconnect() {} }; }
  }
  const engine = createSoundEngine(Context, ready => changes.push(ready), { document: page, window: host, log: (event, data) => logs.push({ event, ...data }), probeMs: 2, timeoutMs: 15, ...overrides });
  const visibility = hidden => { page.hidden = hidden; page.visibilityState = hidden ? 'hidden' : 'visible'; page.dispatchEvent(new Event('visibilitychange')); };
  const settle = () => new Promise(resolve => setTimeout(resolve, 8));
  return { engine, contexts, logs, changes, policy, page, host, visibility, settle };
}
test('lock/unlock recovers a fresh context without a diagnostic tone, preserving pending deduplication and settings', async () => {
  const f = audioFixture(), tracker = createPendingTracker(), settings = { sound: 'chime', volume: .35 };
  try {
    tracker.accept([{ id: 'baseline', status: 'NEW' }]);
    await f.engine.enable(); const original = f.contexts[0]; assert.equal(f.engine.play(settings), true);
    f.visibility(true); assert.equal(original.closed, 1); assert.equal(f.engine.ready(), false);
    assert.ok(original.graphs.every(node => node.stops === 2));
    f.visibility(false); f.host.dispatchEvent(new Event('pageshow')); f.host.dispatchEvent(new Event('focus'));
    await f.settle();
    assert.equal(f.contexts.length, 2); assert.equal(f.engine.ready(), true); assert.equal(f.contexts[1].graphs.length, 0);
    assert.equal(tracker.accept([{ id: 'baseline', status: 'NEW' }]).arrived, false);
    await f.engine.enable(); assert.equal(f.contexts.length, 2); assert.equal(f.engine.play(settings), true);
    assert.deepEqual(settings, { sound: 'chime', volume: .35 });
    assert.ok(f.logs.some(entry => entry.event === 'play-scheduled' && entry.volume === .35 && entry.sound === 'chime'));
    assert.equal(tracker.accept([{ id: 'new', status: 'NEW' }]).arrived, true);
    assert.equal(tracker.accept([{ id: 'new', status: 'NEW' }]).arrived, false);
  } finally { f.engine.close(); }
});
test('pageshow and focus recover an existing suspended context without recreating or playing a tone', async () => {
  const f = audioFixture();
  try {
    await f.engine.enable(); const context = f.contexts[0]; context.state = 'suspended'; context.onstatechange();
    f.host.dispatchEvent(new Event('pageshow')); f.host.dispatchEvent(new Event('focus')); await f.settle();
    assert.equal(f.engine.ready(), true); assert.equal(context.resumes, 2); assert.equal(context.graphs.length, 0);
    context.state = 'interrupted'; context.onstatechange(); f.host.dispatchEvent(new Event('focus')); await f.settle();
    assert.equal(f.engine.ready(), true); assert.equal(f.contexts.length, 1);
  } finally { f.engine.close(); }
});
test('stuck interruption, frozen clock and closed contexts are automatically replaced once', async () => {
  for (const mode of ['interrupted', 'frozen', 'closed']) {
    const f = audioFixture();
    try {
      await f.engine.enable(); const context = f.contexts[0];
      if (mode === 'frozen') context.frozen = true;
      else { context.state = mode; context.stuck = true; context.onstatechange(); }
      f.host.dispatchEvent(new Event('focus')); await f.settle();
      assert.equal(f.engine.ready(), true, mode); assert.equal(context.closed, 1);
      assert.equal(f.contexts.length, 2); assert.equal(f.contexts[1].graphs.length, 0);
      if (mode !== 'closed') assert.ok(f.logs.some(entry => entry.event === 'resume-error'));
    } finally { f.engine.close(); }
  }
});
test('playback failures and frozen clock after playback clear readiness and log the cause', async () => {
  for (const mode of ['playError', 'frozen']) {
    const f = audioFixture();
    try {
      await f.engine.enable(); f.contexts[0][mode] = true;
      const scheduled = f.engine.play({ volume: .2 });
      if (mode === 'playError') assert.equal(scheduled, false);
      await f.settle(); assert.equal(f.engine.ready(), false);
      assert.ok(f.logs.some(entry => entry.event === (mode === 'playError' ? 'play-error' : 'play-clock-stalled')));
    } finally { f.engine.close(); }
  }
});
test('blocked and timed-out resume expose failure and do not leave activation stuck', async () => {
  for (const mode of ['failure', 'hung']) {
    const f = audioFixture();
    try {
      await f.engine.enable(); const context = f.contexts[0];
      context[mode] = mode === 'failure' ? new DOMException('Gesture required', 'NotAllowedError') : true;
      f.host.dispatchEvent(new Event('focus')); await new Promise(resolve => setTimeout(resolve, 25));
      assert.equal(f.engine.ready(), false); assert.equal(context.closed, 1);
      assert.ok(f.logs.some(entry => entry.event === 'resume-error'));
      await f.engine.enable(); assert.equal(f.engine.ready(), true);
    } finally { f.engine.close(); }
  }
});
test('hide during activation or BFCache pagehide prevents late promises from re-enabling stale audio', async () => {
  const f = audioFixture();
  const first = f.engine.enable(); f.visibility(true);
  assert.equal(await first, false); f.visibility(false);
  await f.engine.enable(); const original = f.contexts.at(-1);
  const hide = new Event('pagehide'); Object.defineProperty(hide, 'persisted', { value: true }); f.host.dispatchEvent(hide);
  f.host.dispatchEvent(new Event('pageshow')); assert.equal(f.engine.ready(), false); assert.equal(original.closed, 1);
  await f.engine.enable(); const count = f.logs.length; f.engine.close();
  const closedCount = f.contexts.length; f.visibility(true); f.host.dispatchEvent(new Event('focus')); await f.settle();
  assert.equal(f.contexts.length, closedCount); assert.equal(f.logs.length, count + 1); assert.equal(f.engine.ready(), false);
});
test('concurrent enable taps share one activation; hidden pages never create audio', async () => {
  const f = audioFixture();
  try {
    f.visibility(true); assert.equal(await f.engine.enable(), false); assert.equal(f.contexts.length, 0);
    f.visibility(false); const first = f.engine.enable(), second = f.engine.enable();
    assert.equal(first, second); await first; assert.equal(f.contexts.length, 1); assert.equal(f.contexts[0].resumes, 1);
  } finally { f.engine.close(); }
});

test('automatic recovery is gated by shift activation; initial and historical orders never play', async () => {
  const f = audioFixture(), tracker = createPendingTracker();
  try {
    f.host.dispatchEvent(new Event('pageshow')); f.host.dispatchEvent(new Event('focus'));
    assert.equal(await f.engine.recover(), false); assert.equal(f.contexts.length, 0);
    const orders = [{ id: 'historical', status: 'NEW' }]; tracker.accept(orders);
    assert.deepEqual(tracker.arrivals(), []);
    await f.engine.enable(); f.visibility(true); f.visibility(false); await f.settle();
    tracker.accept(orders); assert.deepEqual(tracker.arrivals(), []); assert.equal(f.contexts.at(-1).graphs.length, 0);
  } finally { f.engine.close(); }
});
test('incoming orders share a bounded recovery and coalesce one alert with latest volume', async () => {
  const f = audioFixture(), settings = { sound: 'chime', volume: .2 };
  try {
    await f.engine.enable(); const original = f.contexts[0];
    original.state = 'suspended'; original.onstatechange();
    const first = f.engine.notifyPending(['a'], () => settings);
    const second = f.engine.notifyPending(['a', 'b'], () => settings);
    assert.equal(first, second); settings.volume = .45;
    assert.equal(await first, true); assert.equal(original.resumes, 2); assert.equal(original.graphs.length, 2);
    assert.equal(await f.engine.notifyPending(['a', 'b'], () => settings), false);
    assert.equal(original.graphs.length, 2);
    assert.ok(f.logs.some(entry => entry.event === 'play-scheduled' && entry.volume === .45 && entry.sound === 'chime'));
    assert.ok(f.logs.some(entry => entry.event === 'recovery-outcome' && entry.source === 'incoming-order' && entry.outcome === 'successful'));
  } finally { f.engine.close(); }
});
test('blocked restoration stops retries across focus, orders and later locks; manual fallback never replays failed orders', async () => {
  const f = audioFixture();
  try {
    await f.engine.enable(); f.policy.failure = new DOMException('Gesture required', 'NotAllowedError');
    f.visibility(true); f.visibility(false); await f.settle();
    assert.equal(f.engine.ready(), false); const count = f.contexts.length;
    for (let i = 0; i < 3; i++) { f.host.dispatchEvent(new Event('focus')); assert.equal(await f.engine.notifyPending([`failed-${i}`], () => ({})), false); }
    f.visibility(true); f.visibility(false); await f.settle(); assert.equal(f.contexts.length, count);
    delete f.policy.failure; await f.engine.enable();
    assert.equal(await f.engine.notifyPending(['failed-0', 'failed-1', 'failed-2'], () => ({})), false);
    assert.equal(f.contexts.at(-1).graphs.length, 0);
    assert.equal(await f.engine.notifyPending(['fresh'], () => ({})), true);
    assert.ok(f.logs.some(entry => entry.event === 'recovery-outcome' && entry.outcome === 'blocked'));
  } finally { f.engine.close(); }
});
test('incoming-only recovery rejection, interruption, stall and timeout require activation without audible diagnostics', async () => {
  for (const [property, value, outcome] of [['failure', new DOMException('Gesture required', 'NotAllowedError'), 'blocked'], ['stuck', true, 'blocked'], ['frozen', true, 'stalled'], ['hung', true, 'timed-out']]) {
    const f = audioFixture();
    try {
      await f.engine.enable(); const context = f.contexts[0]; context.state = 'suspended'; context.onstatechange();
      context[property] = value; f.policy[property] = value;
      assert.equal(await f.engine.notifyPending(['unheard'], () => ({})), false);
      assert.equal(f.engine.ready(), false);
      assert.ok(f.contexts.every(context => !context.graphs.length));
      assert.ok(f.logs.some(entry => entry.event === 'recovery-outcome' && entry.outcome === outcome), outcome);
      const count = f.contexts.length;
      assert.equal(await f.engine.notifyPending(['other'], () => ({})), false); assert.equal(f.contexts.length, count);
      delete f.policy[property]; await f.engine.enable();
      assert.equal(await f.engine.notifyPending(['unheard'], () => ({})), false);
    } finally { f.engine.close(); }
  }
});
test('manual gesture preempts a hung automatic recovery and cancels its pending notification', async () => {
  const f = audioFixture();
  try {
    await f.engine.enable(); const old = f.contexts[0]; old.state = 'suspended'; old.hung = true; old.onstatechange();
    const pending = f.engine.notifyPending(['old'], () => ({}));
    assert.equal(await f.engine.enable(), true); assert.equal(await pending, false);
    assert.equal(f.engine.ready(), true); assert.equal(f.contexts.at(-1).graphs.length, 0);
    assert.equal(await f.engine.notifyPending(['old'], () => ({})), false);
  } finally { f.engine.close(); }
});
test('restoration and incoming recovery share one replacement; hide/logout cancel delayed alerts', async () => {
  const f = audioFixture();
  try {
    await f.engine.enable(); f.visibility(true); f.visibility(false);
    f.host.dispatchEvent(new Event('pageshow')); f.host.dispatchEvent(new Event('focus'));
    const first = f.engine.notifyPending(['first'], () => ({}));
    assert.equal(await first, true); assert.equal(f.contexts.length, 2); assert.equal(f.contexts[1].resumes, 1);
    const context = f.contexts[1]; context.state = 'suspended'; context.onstatechange();
    const next = f.engine.notifyPending(['hidden'], () => ({})); f.visibility(true);
    assert.equal(await next, false); f.visibility(false); await f.settle();
    assert.equal(f.contexts.at(-1).graphs.length, 0);
    const current = f.contexts.at(-1); current.state = 'suspended'; current.onstatechange();
    const closed = f.engine.notifyPending(['logout'], () => ({})); f.engine.close();
    assert.equal(await closed, false); assert.equal(f.engine.ready(), false);
  } finally { f.engine.close(); }
});

test('a silently stalled running context is replaced before incoming-order playback', async () => {
  const f = audioFixture();
  try {
    await f.engine.enable(); const old = f.contexts[0]; old.frozen = true;
    assert.equal(await f.engine.notifyPending(['new'], () => ({ volume: .6 })), true);
    assert.equal(old.graphs.length, 0); assert.equal(old.closed, 1); assert.equal(f.contexts.length, 2);
    assert.equal(f.contexts[1].graphs.length, 2);
    assert.equal(await f.engine.notifyPending(['new'], () => ({})), false);
  } finally { f.engine.close(); }
});
test('failed replacement is bounded, remains single-flight and records interrupted or failed outcomes', async () => {
  for (const outcome of ['interrupted', 'failed']) {
    const f = audioFixture();
    try {
      await f.engine.enable(); const context = f.contexts[0];
      context.state = 'interrupted'; context.stuck = true;
      if (outcome === 'interrupted') Object.assign(f.policy, { state: 'interrupted', stuck: true });
      else f.policy.failure = new Error('Audio device failed');
      const first = f.engine.recover(), second = f.engine.recover(); assert.equal(first, second);
      assert.equal(await first, false); assert.equal(f.contexts.length, 2);
      assert.ok(f.logs.some(entry => entry.event === 'recovery-outcome' && entry.outcome === outcome));
      assert.equal(await f.engine.recover(), false); assert.equal(f.contexts.length, 2);
    } finally { f.engine.close(); }
  }
});
