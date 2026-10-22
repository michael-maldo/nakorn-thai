import { useRef, useState } from 'react';
import { printerUrl, printingEnabled } from './epos';
export default function PrinterSettings({ config, onSave, printer }) {
  const [draft, setDraft] = useState(config), [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const field = (name, value) => setDraft(previous => ({ ...previous, [name]: value }));
  async function run(operation) {
    if (pending.current) return;
    pending.current = true; setBusy(true); setMessage('Contacting printer…');
    try { const result = await operation(config); setMessage(result.message); }
    catch (error) { setMessage(error.message); }
    finally { pending.current = false; setBusy(false); }
  }
  return <section><h1>Printer Management</h1><p>Device-local profiles. Saved does not mean online.</p>
    <p className="sp-notice">{printingEnabled ? 'Experimental browser printing enabled by build configuration. Physical output still requires verification.' : 'Browser printing is disabled in this build. Configuration can be saved; no printer jobs will be sent.'}</p>
    <form className="sp-settings-card" onSubmit={event => {
      event.preventDefault(); try { printerUrl(draft); onSave(draft); setMessage('Profile saved. HTTPS, ePOS service, Safari access and physical output remain unverified.'); } catch (error) { setMessage(error.message); }
    }}><fieldset disabled={busy}><legend>Epson TM-m30II · LAN ePOS-Print XML</legend>
      <label>Printer name<input value={draft.name} maxLength={80} required onChange={event => field('name', event.target.value)} /></label>
      <label>HTTPS printer origin / IP<input type="url" placeholder="https://192.168.1.100" value={draft.endpoint} required onChange={event => field('endpoint', event.target.value)} /></label>
      <label>Epson device ID<input value={draft.deviceId} required onChange={event => field('deviceId', event.target.value)} /></label>
      <label>Paper width<select value={draft.paperWidth || 58} onChange={event => field('paperWidth', Number(event.target.value))}><option value={58}>58 mm · 32 columns</option><option value={80}>80 mm · 48 columns</option></select></label>
      <label>Docket layout<select value={draft.layout} onChange={event => field('layout', event.target.value)}><option value="kitchen">Kitchen only</option><option value="detailed">Detailed online order</option></select></label>
      <label><span><input type="checkbox" disabled={!printingEnabled} checked={printingEnabled && draft.autoPrint} onChange={event => field('autoPrint', event.target.checked)} /> Print once after server-confirmed acceptance on this device</span></label>
      <p>Automatic printing defaults off. Never retries after uncertain delivery. A printing failure does not undo acceptance.</p><button>Save profile</button>
    </fieldset></form>
    <div className="sp-actions"><button disabled={!printingEnabled || busy || !config.endpoint} onClick={() => run(printer.check)}>Check saved endpoint (no paper)</button><button disabled={!printingEnabled || busy || !config.endpoint} onClick={() => { if (window.confirm('Send one test docket to the saved printer, with feed and cut?')) run(printer.test); }}>Test Print</button></div>
    {message && <p className="sp-notice" role="status">{message}</p>}
    <p>Use the configured printer origin, normally HTTPS port 443 for this model. A different port must be verified against its firmware. No raw TCP, discovery scan, TLS bypass or print bridge is used.</p>
    <p>The iPad must reach the printer on the restaurant LAN. Its certificate must be trusted and match the configured hostname/IP. ePOS-Print must already be enabled and allow this origin’s CORS request. Browser errors may not distinguish certificate, CORS and local-network permission failures. Do not change the working Wix/printer configuration during development.</p>
  </section>;
}
