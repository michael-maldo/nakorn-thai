import { useEffect, useState } from 'react';
import { restaurantRequest } from '../api/restaurantApi';

const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
export default function RestaurantSchedulePage() {
  const [schedule, setSchedule] = useState(null), [timezone, setTimezone] = useState('');
  const [windowDraft, setWindowDraft] = useState(null), [closureDraft, setClosureDraft] = useState(null);
  const [busy, setBusy] = useState(true), [error, setError] = useState(''), [notice, setNotice] = useState('');
  function loaded(data) { setSchedule(data); setTimezone(data.settings.timezone); }
  useEffect(() => {
    let active = true;
    restaurantRequest().then(data => { if (active) loaded(data); }).catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, []);
  async function reload() {
    setBusy(true); setError(''); setNotice('');
    try { loaded(await restaurantRequest()); setWindowDraft(null); setClosureDraft(null); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(path, method, body) {
    setBusy(true); setError(''); setNotice('');
    try {
      await restaurantRequest(path, { method, body });
      setWindowDraft(null); setClosureDraft(null); loaded(await restaurantRequest()); setNotice('Restaurant schedule saved.');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  function saveWindow(event) {
    event.preventDefault();
    const { id, dayOfWeek, opensAt, closesAt, active, displayOrder, version } = windowDraft;
    save(`/hours${id ? `/${id}` : ''}`, id ? 'PUT' : 'POST', { dayOfWeek, opensAt, closesAt, active, displayOrder, version });
  }
  function saveClosure(event) {
    event.preventDefault();
    const { id, closedDate, reason, version } = closureDraft;
    save(`/closed-dates${id ? `/${id}` : ''}`, id ? 'PUT' : 'POST', { closedDate, reason, version });
  }
  return <main className="staff-menu page-width"><a href="#/staff">Staff home</a><header className="staff-heading"><h1>Restaurant scheduling</h1><button disabled={busy} onClick={reload}>Reload schedule</button></header>
    {error && <p role="alert" className="staff-error">{error}</p>}{notice && <p role="status">{notice}</p>}{busy && <p role="status">Updating schedule…</p>}
    <p>Opening hours control new pickup orders and requested table times. A day with no active windows is closed. Closed dates override every opening window on that local date.</p>
    {schedule && <>
      <form className="staff-panel" onSubmit={e => { e.preventDefault(); save('/settings', 'PUT', { timezone, version: schedule.settings.version }); }}><fieldset disabled={busy}>
        <h2>Restaurant timezone</h2><label>Timezone<input required maxLength={64} value={timezone} onChange={e => setTimezone(e.target.value)} /></label>
        <p>Use an IANA timezone such as Australia/Melbourne. Changing it changes how all opening hours and booking times are interpreted.</p><button>Save timezone</button>
      </fieldset></form>
      <section className="staff-panel"><h2>Weekly opening hours</h2><p>Times use {schedule.settings.timezone}. Closing is exclusive. A closing time earlier than opening ends on the next day. Opening and closing must differ.</p>
        <button disabled={busy} onClick={() => setWindowDraft({ dayOfWeek: 1, opensAt: '', closesAt: '', active: true, displayOrder: 0 })}>Add opening window</button>
        {!schedule.hours.length && <p>No hours configured. Ordering and booking requests are closed.</p>}
        <div className="staff-table-wrap"><table className="staff-table"><caption>Opening windows</caption><thead><tr><th>Day</th><th>Opens</th><th>Closes</th><th>Status</th><th>Actions</th></tr></thead><tbody>{schedule.hours.map(row => <tr key={row.id}>
          <td>{weekdays[row.dayOfWeek - 1]}</td><td>{row.opensAt}</td><td>{row.closesAt}{row.closesAt < row.opensAt && ' (next day)'}</td><td>{row.active ? 'Active' : 'Inactive'}</td>
          <td><button disabled={busy} onClick={() => setWindowDraft({ ...row })}>Edit</button> <button disabled={busy} onClick={() => save(`/hours/${row.id}?version=${row.version}`, 'DELETE')}>Remove</button></td>
        </tr>)}</tbody></table></div>
      </section>
      {windowDraft && <form className="staff-panel" onSubmit={saveWindow}><fieldset disabled={busy}>
        <h2>{windowDraft.id ? 'Edit opening window' : 'New opening window'}</h2>
        <label>Day<select value={windowDraft.dayOfWeek} onChange={e => setWindowDraft({ ...windowDraft, dayOfWeek: Number(e.target.value) })}>{weekdays.map((day, i) => <option key={day} value={i + 1}>{day}</option>)}</select></label>
        <label>Opens<input type="time" step={1} required value={windowDraft.opensAt} onChange={e => setWindowDraft({ ...windowDraft, opensAt: e.target.value })} /></label>
        <label>Closes<input type="time" step={1} required value={windowDraft.closesAt} onChange={e => setWindowDraft({ ...windowDraft, closesAt: e.target.value })} /></label>
        <label>Display order<input type="number" min={0} max={2147483647} required value={windowDraft.displayOrder} onChange={e => setWindowDraft({ ...windowDraft, displayOrder: Number(e.target.value) })} /></label>
        <label className="staff-check"><input type="checkbox" checked={windowDraft.active} onChange={e => setWindowDraft({ ...windowDraft, active: e.target.checked })} />Active</label>
        <div className="staff-toolbar"><button>Save window</button><button type="button" onClick={() => setWindowDraft(null)}>Cancel</button></div>
      </fieldset></form>}
      <section className="staff-panel"><h2>Closed dates</h2><p>A closure blocks the entire local date, including the after-midnight part of the previous day's opening window.</p>
        <button disabled={busy} onClick={() => setClosureDraft({ closedDate: '', reason: '' })}>Add closed date</button>
        {!schedule.closedDates.length && <p>No closed dates configured.</p>}
        <div className="staff-table-wrap"><table className="staff-table"><caption>Closed local dates</caption><thead><tr><th>Date</th><th>Reason</th><th>Actions</th></tr></thead><tbody>{schedule.closedDates.map(row => <tr key={row.id}>
          <td>{row.closedDate}</td><td>{row.reason}</td><td><button disabled={busy} onClick={() => setClosureDraft({ ...row, reason: row.reason || '' })}>Edit</button> <button disabled={busy} onClick={() => save(`/closed-dates/${row.id}?version=${row.version}`, 'DELETE')}>Remove</button></td>
        </tr>)}</tbody></table></div>
      </section>
      {closureDraft && <form className="staff-panel" onSubmit={saveClosure}><fieldset disabled={busy}>
        <h2>{closureDraft.id ? 'Edit closed date' : 'New closed date'}</h2><label>Local date<input type="date" required value={closureDraft.closedDate} onChange={e => setClosureDraft({ ...closureDraft, closedDate: e.target.value })} /></label>
        <label>Reason (optional)<input maxLength={500} value={closureDraft.reason} onChange={e => setClosureDraft({ ...closureDraft, reason: e.target.value })} /></label>
        <div className="staff-toolbar"><button>Save closed date</button><button type="button" onClick={() => setClosureDraft(null)}>Cancel</button></div>
      </fieldset></form>}
    </>}
  </main>;
}
