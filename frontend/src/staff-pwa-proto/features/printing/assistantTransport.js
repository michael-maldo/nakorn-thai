import { escapeXml, EPSON_TM_PRINT_ASSISTANT, syntheticTestDocket } from '../../../shared/staff-orders/printing/epos.js';

export const ASSISTANT_PENDING_KEY = 'nakorn-staff-pwa-proto:printing:assistant-pending';
export const ASSISTANT_RESULT_PARAM = 'nt_tm_print_assistant';
const XML_NAMESPACE = 'http://www.epson-pos.com/schemas/2011/03/epos-print';
const MAX_ENCODED_DATA_LENGTH = 750 * 1024;
function browserSessionStorage() { try { return globalThis.sessionStorage || null; } catch { return null; } }

const errorMessages = {
  101: 'Could not communicate with the printer.', 102: 'The printer operation timed out.',
  103: 'The printer cover is open.', 104: 'The printer cutter reported an error.',
  105: 'The printer reported a mechanical error.', 106: 'The printer reported an unrecoverable error.',
  107: 'The printer is out of paper.', 108: 'The printer is too hot.',
  111: 'The printer battery is depleted.', 199: 'The printer reported a communication error.',
  201: 'Could not communicate with the printer.', 301: 'The printer connection failed.',
  302: 'The printer disconnection failed.', 303: 'The printer does not support disconnection.',
  401: 'The print request contains invalid data.', 402: 'No print data was received.',
  403: 'The PDF data could not be converted.', 404: 'The XML data could not be converted.',
  405: 'The print data exceeds Epson’s supported limit.', 406: 'The printer operation timed out.',
  501: 'No printer is selected in Epson TM Print Assistant.', 502: 'Epson TM Print Assistant reported an internal error.',
  503: 'The selected printer is not supported.', 599: 'Epson TM Print Assistant reported an internal error.',
};

export function isSupportedAssistantPlatform(nav = globalThis.navigator) {
  if (!nav) return false;
  const platform = String(nav.platform || '');
  const userAgent = String(nav.userAgent || '');
  const appleMobile = /iPad|iPhone|iPod/i.test(platform) || /iPad|iPhone|iPod/i.test(userAgent);
  const ipadDesktopMode = platform === 'MacIntel' && Number(nav.maxTouchPoints) > 1;
  const safari = /Safari\//i.test(userAgent) && !/(CriOS|FxiOS|EdgiOS|OPiOS)/i.test(userAgent);
  return (appleMobile || ipadDesktopMode) && /AppleWebKit/i.test(userAgent) && safari;
}

export function buildAssistantTestXml(config, date = new Date().toISOString()) {
  const text = syntheticTestDocket(config.name, date);
  const lines = text.split('\n').slice(0, -1);
  const commands = lines.map(line => `<text lang="en" font="font_a" width="1" height="1">${escapeXml(line)}&#10;</text>`).join('');
  return `<?xml version="1.0" encoding="utf-8"?><epos-print xmlns="${XML_NAMESPACE}">${commands}<feed line="3"/><cut type="feed"/></epos-print>`;
}

function encodeRfc3986(value) {
  return encodeURIComponent(String(value)).replace(/[!'()*]/g, char => `%${char.charCodeAt(0).toString(16).toUpperCase()}`);
}

function callbackUrl(locationLike, result) {
  const url = new URL(locationLike.href);
  url.search = '';
  url.searchParams.set(ASSISTANT_RESULT_PARAM, result);
  return url.href;
}

export function buildAssistantUrl({ xml, successUrl, errorUrl, maxEncodedDataLength = MAX_ENCODED_DATA_LENGTH }) {
  const data = encodeRfc3986(xml);
  if (data.length > maxEncodedDataLength) throw new Error('Synthetic docket exceeds Epson TM Print Assistant’s supported data limit.');
  return `tmprintassistant://tmprintassistant.epson.com/print?success=${encodeRfc3986(successUrl)}&error=${encodeRfc3986(errorUrl)}&ver=1&data-type=eposprintxml&data=${data}&error-dialog=yes`;
}

export function parseAssistantCallback(search) {
  const params = new URLSearchParams(search || '');
  const result = params.get(ASSISTANT_RESULT_PARAM);
  if (result === 'success') return { status: 'confirmed', message: 'Epson reported print completion.' };
  if (result !== 'error') return null;
  const code = (params.get('code') || '').replace(/[^0-9]/g, '').slice(0, 3);
  const message = errorMessages[code] || 'Epson TM Print Assistant reported a print error.';
  return { status: 'failed', code: code || 'unknown', message: `Epson reported error ${code || 'unknown'}: ${message}` };
}

export function consumeAssistantCallback(search, storage = browserSessionStorage()) {
  const result = parseAssistantCallback(search);
  if (result) { try { storage?.removeItem(ASSISTANT_PENDING_KEY); } catch { /* Callback reporting does not depend on clearing the marker. */ } }
  return result;
}

export function markAssistantOutcomeUnknown(storage = browserSessionStorage()) {
  if (!storage?.getItem(ASSISTANT_PENDING_KEY)) return null;
  storage.removeItem(ASSISTANT_PENDING_KEY);
  return { status: 'unknown', message: 'Print outcome unknown. Inspect the printer before any deliberate manual reprint.' };
}

export function createAssistantTransport({
  enabled, getConfig, navigator: nav = globalThis.navigator, location: locationLike = globalThis.location,
  storage = browserSessionStorage(), navigate = url => { globalThis.location.href = url; }, now = () => Date.now(),
  maxEncodedDataLength = MAX_ENCODED_DATA_LENGTH,
} = {}) {
  let invoking = false;
  return {
    test(config) {
      if (!enabled) throw new Error('Printing is disabled in this build.');
      if (config?.deviceEnabled !== true || (getConfig && getConfig().deviceEnabled !== true)) throw new Error('Printing is disabled on this device.');
      if (config?.method !== EPSON_TM_PRINT_ASSISTANT || !config.name?.trim() || ![58, 80].includes(config.paperWidth) || !['kitchen', 'detailed'].includes(config.layout)) throw new Error('Save a valid Epson TM Print Assistant profile first.');
      if (!isSupportedAssistantPlatform(nav)) throw new Error('Epson TM Print Assistant handoff is supported here only on an iOS or iPadOS WebKit browser.');
      if (invoking || storage?.getItem(ASSISTANT_PENDING_KEY)) throw new Error('A previous Epson Assistant handoff has no confirmed result. Inspect the printer before starting another test.');
      if (!storage || !locationLike?.href) throw new Error('This browser cannot track the Epson Assistant print outcome safely.');
      const xml = buildAssistantTestXml(config, new Date(now()).toISOString());
      const url = buildAssistantUrl({
        xml,
        successUrl: callbackUrl(locationLike, 'success'),
        errorUrl: callbackUrl(locationLike, 'error'),
        maxEncodedDataLength,
      });
      invoking = true;
      try {
        storage.setItem(ASSISTANT_PENDING_KEY, String(now()));
        navigate(url);
        return { status: 'handoff-attempted', message: 'Handoff attempted. Epson’s callback will report its result; otherwise the print outcome is unknown.' };
      } catch (error) {
        storage.removeItem(ASSISTANT_PENDING_KEY);
        throw new Error(`Epson TM Print Assistant handoff failed: ${error.message}`);
      } finally {
        invoking = false;
      }
    },
  };
}
