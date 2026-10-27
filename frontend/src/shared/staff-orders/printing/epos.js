import { orderTime } from '../model/presentation.js';
import { docketLines } from './dockets.js';
export { docketText } from './dockets.js';
// A build-time flag cannot be enabled through device preferences.
export const printingEnabled = import.meta.env?.VITE_STAFF_EPOS_ENABLED === 'true';
export const DIRECT_EPOS = 'DIRECT_EPOS';
export const EPSON_TM_PRINT_ASSISTANT = 'EPSON_TM_PRINT_ASSISTANT';
export const printerDefaults = { name: 'Epson TM-m30II', endpoint: '', deviceId: 'local_printer', layout: 'kitchen', paperWidth: 58, deviceEnabled: false, autoPrint: false, method: DIRECT_EPOS };
export function normalizePrinterProfile(profile = {}) {
  const method = profile.method === EPSON_TM_PRINT_ASSISTANT ? EPSON_TM_PRINT_ASSISTANT : DIRECT_EPOS;
  return { ...printerDefaults, ...profile, method, ...(method === EPSON_TM_PRINT_ASSISTANT ? { autoPrint: false } : {}) };
}
export const escapeXml = text => String(text ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/g, '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;').replaceAll("'", '&apos;');
export function syntheticTestDocket(name, date = new Date().toISOString()) {
  return `NAKORN THAI\nPRINTER TEST\n${name}\n${orderTime(date)} (Melbourne)\nTEST PRINT\n1234567890\nABCDEFGHIJKLMNOPQRSTUVWXYZ\nEnd of test\n`;
}
export function printerUrl(config) {
  const url = new URL(config.endpoint);
  if (url.protocol !== 'https:' || url.username || url.password || url.hash || url.search || url.pathname !== '/') throw new Error('Enter an HTTPS printer origin only, with no credentials, path or query.');
  if (!/^[a-zA-Z0-9_.-]{1,64}$/.test(config.deviceId)) throw new Error('Enter a valid Epson device ID.');
  url.pathname = '/cgi-bin/epos/service.cgi';
  url.searchParams.set('devid', config.deviceId); url.searchParams.set('timeout', '10000');
  return url.href;
}
export function validPrinterProfile(config) {
  try { printerUrl(config); return !!config.name?.trim() && [58, 80].includes(config.paperWidth) && ['kitchen', 'detailed'].includes(config.layout); } catch { return false; }
}
function envelope(contents, cut = true) {
  // Epson cut type=feed feeds to the cutter and performs the printer's partial cut.
  return `<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><epos-print xmlns="http://www.epson-pos.com/schemas/2011/03/epos-print">${contents}${cut ? '<feed line="3"/><cut type="feed"/>' : ''}</epos-print></s:Body></s:Envelope>`;
}
export function eposEnvelope(text, { cut = true } = {}) {
  return envelope(`<text lang="en" font="font_a" width="1" height="1"/><text>${escapeXml(text)}</text>`, cut);
}
export function orderDocketXml(order, config, pickupMinutes) {
  const contents = docketLines(order, config.layout, { paperWidth: config.paperWidth, pickupMinutes }).map(line =>
    `<text lang="en" font="font_a" align="${line.center ? 'center' : 'left'}" em="${!!line.bold}" width="${line.large ? 2 : 1}" height="${line.large ? 2 : 1}">${escapeXml(line.text)}&#10;</text>`).join('');
  return envelope(contents);
}
export function parsePrintResponse(xml) {
  // DOMParser is deliberately supplied by the browser; tests use real browser XML too.
  const document = new DOMParser().parseFromString(xml, 'application/xml');
  const response = document.getElementsByTagNameNS('http://www.epson-pos.com/schemas/2011/03/epos-print', 'response')[0];
  if (document.querySelector('parsererror') || !response || !['true', 'false', '1', '0'].includes(response.getAttribute('success'))) throw new Error('Invalid Epson response; outcome uncertain. Inspect the printer before reprinting.');
  if (!['true', '1'].includes(response.getAttribute('success')) || response.getAttribute('code')) throw new Error(`Epson reported ${response.getAttribute('code') || 'failure'} (status ${response.getAttribute('status') || 'unknown'}). Inspect output before manual reprint.`);
  return { success: true, status: response.getAttribute('status'), message: 'Epson reported completion. Check the physical docket and cut.' };
}
export function createPrinter({ enabled = printingEnabled, fetcher = globalThis.fetch, getConfig, timeoutMs = 15000 } = {}) {
  let busy = false;
  async function send(config, xml) {
    if (!enabled) throw new Error('Browser printing is disabled in this build. Hardware verification required.');
    if (config.deviceEnabled !== true || (getConfig && getConfig().deviceEnabled !== true)) throw new Error('Printing is disabled on this device.');
    if (!validPrinterProfile(config)) throw new Error('Save a valid printer profile first.');
    if (busy) throw new Error('A printer request is already in progress.');
    const url = printerUrl(config);
    busy = true;
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), timeoutMs);
    try {
      const result = await fetcher(url, { method: 'POST', mode: 'cors', credentials: 'omit', cache: 'no-store', redirect: 'error', headers: { 'Content-Type': 'text/xml; charset=utf-8', SOAPAction: '""' }, body: xml, signal: abort.signal });
      if (!result.ok) throw new Error(`Printer HTTP ${result.status}`);
      return parsePrintResponse(await result.text());
    } catch (error) {
      throw new Error(`${error.message}. Print outcome may be uncertain. No automatic retry. Inspect paper/output before deliberate manual reprint.`);
    } finally { clearTimeout(timer); busy = false; }
  }
  return {
    printOrder(config, order, pickupMinutes) { return send(config, orderDocketXml(order, config, pickupMinutes)); },
    test(config) { return send(config, eposEnvelope(syntheticTestDocket(config.name))); },
    // Empty ePOS document is a non-printing service probe, not a TCP or physical-output test.
    async check(config) { const result = await send(config, eposEnvelope('', { cut: false })); return { ...result, message: 'ePOS service responded successfully. No paper was printed; physical output remains unverified.' }; },
  };
}
