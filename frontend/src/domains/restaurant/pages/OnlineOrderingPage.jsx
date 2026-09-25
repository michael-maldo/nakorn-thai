import { useEffect, useState } from 'react';
import { restaurantRequest } from '../api/restaurantApi';

export default function OnlineOrderingPage() {
  const [settings, setSettings] = useState(null);
  const [acceptingOrders, setAcceptingOrders] = useState(true);
  const [pauseMessage, setPauseMessage] = useState('');
  const [busy, setBusy] = useState(true), [error, setError] = useState(''), [notice, setNotice] = useState('');
  function loaded(data) {
    setSettings(data); setAcceptingOrders(data.acceptingOrders); setPauseMessage(data.pauseMessage ?? '');
  }
  useEffect(() => {
    let active = true;
    restaurantRequest('/ordering').then(data => { if (active) loaded(data); })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);
  async function reload() {
    setBusy(true); setError(''); setNotice('');
    try { loaded(await restaurantRequest('/ordering')); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await restaurantRequest('/ordering', { method: 'PUT', body: { acceptingOrders, pauseMessage, version: settings.version } });
      loaded(data);
      setNotice(data.acceptingOrders ? 'Staff pause removed. Ordering still follows the availability checks below.' : 'New online orders paused. Existing orders remain accessible.');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <main className="staff-menu page-width">
    <header className="staff-heading"><h1>Online ordering</h1><button type="button" disabled={busy} onClick={reload}>Refresh status</button></header>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {notice && <p role="status">{notice}</p>}{busy && <p role="status">Updating ordering status…</p>}
    {settings && <>
      <section className="staff-panel" aria-labelledby="ordering-status-heading">
        <h2 id="ordering-status-heading">Current availability</h2>
        <p><strong>{settings.status.enabled ? 'Accepting new online orders' : 'Not accepting new online orders'}</strong></p>
        <ul><li>Ordering configuration: {settings.configured ? 'Enabled' : 'Disabled by configuration — contact your system administrator to enable ordering.'}</li>
          <li>Staff control: {settings.acceptingOrders ? 'Accepting orders when available' : 'Paused by staff'}</li>
          <li>Opening hours: {settings.restaurantOpen ? 'Restaurant is open' : 'Outside opening hours'}</li></ul>
        <p>Menu schedules, ordering cutoffs and dish availability also apply.</p>
        {!settings.status.enabled && <p>Customer message: {settings.status.message}</p>}
      </section>
      <form className="staff-panel" onSubmit={save}><fieldset disabled={busy}>
        <legend>Staff ordering control</legend>
        <label className="staff-check"><input type="checkbox" checked={acceptingOrders} onChange={e => setAcceptingOrders(e.target.checked)} />Accept online orders</label>
        <p>Uncheck to pause new orders. Existing orders and their payments remain accessible. Resuming respects restaurant hours and menu availability.</p>
        <label>Customer message while paused (optional)<textarea maxLength={300} value={pauseMessage} onChange={e => setPauseMessage(e.target.value)} /></label>
        <p>Leave blank to show “Online ordering is temporarily paused. Please try again later.”</p>
        <button type="submit">Save ordering settings</button>
      </fieldset></form>
    </>}
  </main>;
}
