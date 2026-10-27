import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ASSISTANT_PENDING_KEY, ASSISTANT_RESULT_PARAM, buildAssistantTestXml, buildAssistantUrl,
  consumeAssistantCallback, createAssistantTransport, isSupportedAssistantPlatform,
  markAssistantOutcomeUnknown,
} from './assistantTransport.js';
import { DIRECT_EPOS, EPSON_TM_PRINT_ASSISTANT, normalizePrinterProfile, printerDefaults } from '../../../shared/staff-orders/printing/epos.js';
import { readPreference, writePreference } from '../../app/orderPreferences.js';

const config = { ...printerDefaults, method: EPSON_TM_PRINT_ASSISTANT, deviceEnabled: true, name: `Kitchen & <iPad> O'Neil` };
const ios = { platform: 'iPad', userAgent: 'Mozilla/5.0 (iPad; CPU OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1', maxTouchPoints: 5 };
function memoryStorage() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}

test('legacy profiles migrate to direct ePOS and device-local Assistant selection stays experimental', () => {
  assert.equal(normalizePrinterProfile({ name: 'Old profile', endpoint: 'https://printer.test' }).method, DIRECT_EPOS);
  assert.equal(normalizePrinterProfile({ ...config, autoPrint: true }).autoPrint, false);
  const saved = globalThis.localStorage, values = new Map();
  globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
  try {
    assert.equal(writePreference('printer', { ...config, autoPrint: true }), true);
    assert.equal(readPreference('printer', {}).method, EPSON_TM_PRINT_ASSISTANT);
    assert.equal(readPreference('printer', {}).autoPrint, true); // UI/controller normalization disables it before use.
  } finally { globalThis.localStorage = saved; }
});

test('Assistant handoff URL uses the exact scheme and RFC3986-encoded bare ePOS XML', () => {
  const xml = buildAssistantTestXml(config, '2026-10-27T02:03:04.000Z');
  assert.match(xml, /^<\?xml[^>]*><epos-print xmlns="http:\/\/www\.epson-pos\.com\/schemas\/2011\/03\/epos-print">/);
  assert.doesNotMatch(xml, /Envelope|SOAP/);
  assert.match(xml, /PRINTER TEST/);
  assert.match(xml, /Kitchen &amp; &lt;iPad&gt; O&apos;Neil/);
  assert.match(xml, /<feed line="3"\/><cut type="feed"\/><\/epos-print>$/);
  const url = buildAssistantUrl({ xml, successUrl: 'https://staff.example/staff-pwa-proto/?result=success#/staff', errorUrl: 'https://staff.example/staff-pwa-proto/?result=error#/staff' });
  assert.ok(url.startsWith('tmprintassistant://tmprintassistant.epson.com/print?'));
  assert.match(url, /&ver=1&data-type=eposprintxml&data=/);
  assert.match(url, /&error-dialog=yes$/);
  assert.match(url, /%3Cepos-print/);
  assert.match(url, /%26amp%3B/);
  assert.doesNotMatch(url, /Kitchen & <iPad>/);
});

test('unsupported platforms, disabled printing, and invalid profiles never invoke the native handler', () => {
  assert.equal(isSupportedAssistantPlatform({ platform: 'Linux x86_64', userAgent: 'Mozilla/5.0 Chrome/130' }), false);
  let handoffs = 0;
  const base = { enabled: true, navigator: ios, location: { href: 'https://staff.example/staff-pwa-proto/#/staff' }, storage: memoryStorage(), navigate: () => handoffs++ };
  assert.throws(() => createAssistantTransport({ ...base, navigator: { platform: 'Linux', userAgent: 'Chrome' } }).test(config), /only on an iOS or iPadOS/);
  assert.throws(() => createAssistantTransport({ ...base, enabled: false }).test(config), /disabled in this build/);
  assert.throws(() => createAssistantTransport({ ...base }).test({ ...config, deviceEnabled: false }), /disabled on this device/);
  assert.throws(() => createAssistantTransport({ ...base }).test({ ...config, name: '' }), /valid Epson TM Print Assistant profile/);
  assert.equal(handoffs, 0);
});

test('handoff is attempted once, stores no docket data, and never counts navigation as completion', () => {
  const storage = memoryStorage(), calls = [];
  const transport = createAssistantTransport({
    enabled: true, navigator: ios, location: { href: 'https://staff.example/staff-pwa-proto/#/staff' }, storage,
    now: () => Date.parse('2026-10-27T02:03:04Z'), navigate: url => calls.push(url),
  });
  const result = transport.test(config);
  assert.equal(result.status, 'handoff-attempted');
  assert.match(result.message, /no callback means|outcome is unknown/i);
  assert.equal(calls.length, 1);
  assert.equal(storage.getItem(ASSISTANT_PENDING_KEY), String(Date.parse('2026-10-27T02:03:04Z')));
  assert.throws(() => transport.test(config), /previous Epson Assistant handoff/);
  assert.equal(calls.length, 1);
  assert.deepEqual(markAssistantOutcomeUnknown(storage), { status: 'unknown', message: 'Print outcome unknown. Inspect the printer before any deliberate manual reprint.' });
  assert.equal(markAssistantOutcomeUnknown(storage), null);
  assert.equal(calls.length, 1);
});

test('Epson success/error callbacks are parsed safely and clear pending state', () => {
  const storage = memoryStorage(); storage.setItem(ASSISTANT_PENDING_KEY, '123');
  const success = consumeAssistantCallback(`?${ASSISTANT_RESULT_PARAM}=success`, storage);
  assert.deepEqual(success, { status: 'confirmed', message: 'Epson reported print completion.' });
  assert.equal(storage.getItem(ASSISTANT_PENDING_KEY), null);
  storage.setItem(ASSISTANT_PENDING_KEY, '456');
  const failure = consumeAssistantCallback(`?${ASSISTANT_RESULT_PARAM}=error&code=107&message=${encodeURIComponent('untrusted callback text')}`, storage);
  assert.equal(failure.status, 'failed');
  assert.equal(failure.message, 'Epson reported error 107: The printer is out of paper.');
  assert.doesNotMatch(failure.message, /untrusted callback text/);
  assert.equal(storage.getItem(ASSISTANT_PENDING_KEY), null);
  assert.equal(consumeAssistantCallback('?unrelated=value', storage), null);
});

test('failed native invocation clears pending marker and is not retried', () => {
  const storage = memoryStorage(); let calls = 0;
  const transport = createAssistantTransport({ enabled: true, navigator: ios, location: { href: 'https://staff.example/' }, storage, navigate() { calls++; throw new Error('Navigation rejected'); } });
  assert.throws(() => transport.test(config), /handoff failed: Navigation rejected/);
  assert.equal(storage.getItem(ASSISTANT_PENDING_KEY), null);
  assert.equal(calls, 1);
});
