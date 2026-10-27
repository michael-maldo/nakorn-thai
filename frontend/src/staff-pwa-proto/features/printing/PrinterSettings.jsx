import { useEffect, useMemo, useRef, useState } from 'react';
import { DIRECT_EPOS, EPSON_TM_PRINT_ASSISTANT, printingEnabled, printerUrl, validPrinterProfile } from '../../../shared/staff-orders/printing/epos.js';
import { createAssistantTransport } from './assistantTransport.js';

function validProfile(config) {
  if (config.method === EPSON_TM_PRINT_ASSISTANT) return !!config.name?.trim() && [58, 80].includes(config.paperWidth) && ['kitchen', 'detailed'].includes(config.layout);
  try { printerUrl(config); return validPrinterProfile(config); } catch { return false; }
}

export default function PrinterSettings({ config, onSave, printer }) {
  const [draft, setDraft] = useState(config), [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const [endpointStatus, setEndpointStatus] = useState('Not checked'), [testStatus, setTestStatus] = useState('Not tested');
  const pending = useRef(false), configRef = useRef(config);
  configRef.current = config;
  const assistant = useMemo(() => createAssistantTransport({ enabled: printingEnabled, getConfig: () => configRef.current }), []);
  const canSend = printingEnabled && config.deviceEnabled === true && validProfile(config);
  useEffect(() => { setDraft(config); setEndpointStatus('Not checked'); setTestStatus('Not tested'); }, [config]);
  const field = (name, value) => setDraft(previous => ({ ...previous, [name]: value }));
  async function run(operation, setStatus, selectedConfig = config) {
    if (!printingEnabled || selectedConfig.deviceEnabled !== true || !validProfile(selectedConfig) || pending.current) return;
    pending.current = true; setBusy(true); setMessage('Preparing printer request…');
    try {
      const result = await operation(selectedConfig);
      setMessage(result.message); setStatus(result.message);
    } catch (error) { setMessage(error.message); setStatus(`Failed: ${error.message}`); }
    finally { pending.current = false; setBusy(false); }
  }
  function saveProfile(event) {
    event.preventDefault();
    try {
      if (!validProfile(draft)) throw new Error(draft.method === EPSON_TM_PRINT_ASSISTANT ? 'Enter a printer name and valid paper/docket settings.' : 'Enter a valid direct ePOS printer profile.');
      const saved = { ...draft, autoPrint: draft.method === EPSON_TM_PRINT_ASSISTANT ? false : !!draft.autoPrint };
      onSave(saved);
      setMessage(draft.method === EPSON_TM_PRINT_ASSISTANT ? 'Experimental Assistant profile saved on this device. Acceptance printing remains off.' : 'Profile saved. HTTPS, ePOS service, Safari access and physical output remain unverified.');
    } catch (error) { setMessage(error.message); }
  }
  return <section><h1>Printer Management</h1><p>Device-local profiles. Saved does not mean online.</p>
    <p className="sp-notice">{printingEnabled ? 'Experimental printer handoff enabled by build configuration. Physical output still requires verification.' : 'Printing is disabled in this build. Configuration can be saved; no printer jobs will be sent.'}</p>
    <label><span><input type="checkbox" checked={config.deviceEnabled === true} disabled={busy} onChange={event => { try { onSave({ ...config, deviceEnabled: event.target.checked, autoPrint: false }); setMessage('Device printing preference saved. Automatic acceptance printing is off.'); } catch (error) { setMessage(error.message); } }} /> Enable printing on this device</span></label>
    <p>Device printing: {config.deviceEnabled === true ? 'Enabled' : 'Disabled'}. {config.method === EPSON_TM_PRINT_ASSISTANT ? 'Method: Epson TM Print Assistant.' : `Endpoint: ${endpointStatus}.`} Test print: {testStatus}.</p>
    <form className="sp-settings-card" onSubmit={saveProfile}>
      <fieldset disabled={busy}><legend>Epson TM-m30II · device profile</legend>
        <label>Printing method<select value={draft.method || DIRECT_EPOS} onChange={event => field('method', event.target.value)}><option value={DIRECT_EPOS}>Direct Epson ePOS-Print (LAN HTTPS)</option><option value={EPSON_TM_PRINT_ASSISTANT}>Epson TM Print Assistant (iPad — Experimental)</option></select></label>
        <label>Printer name<input value={draft.name} maxLength={80} required onChange={event => field('name', event.target.value)} /></label>
        {draft.method === DIRECT_EPOS && <>
          <label>HTTPS printer origin / IP<input type="url" placeholder="https://192.168.1.100" value={draft.endpoint} required onChange={event => field('endpoint', event.target.value)} /></label>
          <label>Epson device ID<input value={draft.deviceId} required onChange={event => field('deviceId', event.target.value)} /></label>
        </>}
        <label>Paper width<select value={draft.paperWidth || 58} onChange={event => field('paperWidth', Number(event.target.value))}><option value={58}>58 mm · 32 columns</option><option value={80}>80 mm · 48 columns</option></select></label>
        <label>Docket layout<select value={draft.layout} onChange={event => field('layout', event.target.value)}><option value="kitchen">Kitchen only</option><option value="detailed">Detailed online order</option></select></label>
        {draft.method === DIRECT_EPOS ? <>
          <label><span><input type="checkbox" disabled={!printingEnabled || !config.deviceEnabled} checked={!!draft.autoPrint} onChange={event => field('autoPrint', event.target.checked)} /> Print once after server-confirmed acceptance on this device</span></label>
          <p>Automatic printing defaults off. Never retries after uncertain delivery. A printing failure does not undo acceptance.</p>
        </> : <div className="sp-notice" role="note"><strong>Experimental:</strong> This method hands an ePOS-Print XML test docket to Epson TM Print Assistant. The app must already be installed, have the TM-m30II selected, and be configured to reach it. Acceptance printing is not connected to this method.</div>}
        <button>Save profile</button>
      </fieldset>
    </form>
    {config.method === EPSON_TM_PRINT_ASSISTANT ? <div className="sp-actions"><button disabled={!canSend || busy} onClick={() => { if (window.confirm('Hand one synthetic test docket to Epson TM Print Assistant?')) run(assistant.test, setTestStatus); }}>Test Print via Epson Assistant</button></div> : <div className="sp-actions"><button disabled={!canSend || busy} onClick={() => run(printer.check, setEndpointStatus)}>Check saved endpoint (no paper)</button><button disabled={!canSend || busy} onClick={() => { if (window.confirm('Send one test docket to the saved printer, with feed and cut?')) run(printer.test, setTestStatus); }}>Test Print</button></div>}
    {message && <p className="sp-notice" role="status">{message}</p>}
    {config.method === EPSON_TM_PRINT_ASSISTANT ? <p>The handoff uses the Epson documented iOS URL scheme. Launching the app is not treated as print confirmation. The callback may open this PWA URL in Safari; return-to-installed-PWA behavior needs testing on the iPad. If no callback arrives, the result is unknown; inspect the printer before any deliberate manual reprint.</p> : <><p>Use the configured printer origin, normally HTTPS port 443 for this model. A different port must be verified against its firmware. No raw TCP, discovery scan, TLS bypass or print bridge is used.</p><p>The iPad must reach the printer on the restaurant LAN. Its certificate must be trusted and match the configured hostname/IP. ePOS-Print must already be enabled and allow this origin’s CORS request. Browser errors may not distinguish certificate, CORS and local-network permission failures. Do not change the working Wix/printer configuration during development.</p></>}
  </section>;
}
