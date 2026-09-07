import { useEffect, useState } from 'react';
import { getCollectionConfiguration, saveCollectionConfiguration } from '../api/menuApi';

export default function MenuCollectionAvailabilityEditor({ authorization }) {
  const [collections, setCollections] = useState([]), [draft, setDraft] = useState(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  useEffect(() => {
    let active = true;
    getCollectionConfiguration(authorization).then(rows => { if (active) setCollections(rows); })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [authorization]);
  async function reload() {
    setBusy(true); setError(''); setNotice('');
    try { setCollections(await getCollectionConfiguration(authorization)); setDraft(null); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      await saveCollectionConfiguration(draft, authorization); setDraft(null);
      setNotice('Collection availability saved.'); setCollections(await getCollectionConfiguration(authorization));
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  const field = (key, value) => setDraft(current => ({ ...current, data: { ...current.data, [key]: value } }));
  return <details className="staff-panel"><summary>Collection availability</summary>
    {error && <p className="staff-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    <p>A daily cutoff permits ordering only while the restaurant is open and before that time in the restaurant timezone. Existing collection schedules still apply.</p>
    <button type="button" disabled={busy} onClick={reload}>Reload collections</button>
    <div className="staff-table-wrap"><table className="staff-table"><caption>Menu collections</caption><thead><tr><th>Collection</th><th>Status</th><th>Active</th><th>Daily cutoff</th><th>Action</th></tr></thead><tbody>
      {collections.map(({ collection }) => <tr key={collection.id}><td>{collection.data.name}</td><td>{collection.data.status}</td><td>{collection.data.active ? 'Yes' : 'No'}</td><td>{collection.data.dailyCutoffTime || 'None'}</td><td><button type="button" disabled={busy} onClick={() => setDraft({ ...collection, data: { ...collection.data } })}>Edit availability</button></td></tr>)}
    </tbody></table></div>
    {draft && <form onSubmit={save}><fieldset disabled={busy}><legend>{draft.data.name}</legend>
      <label>Status<select value={draft.data.status} onChange={e => field('status', e.target.value)}>{['DRAFT', 'PUBLISHED', 'ARCHIVED'].map(status => <option key={status}>{status}</option>)}</select></label>
      <label className="staff-check"><input type="checkbox" checked={draft.data.active} onChange={e => field('active', e.target.checked)} />Active</label>
      <label>Daily cutoff (restaurant local time)<input type="time" step={1} value={draft.data.dailyCutoffTime || ''} onChange={e => field('dailyCutoffTime', e.target.value || null)} /></label>
      <p>Leave blank for no daily cutoff. Opening days, hours and closed dates are managed under Restaurant scheduling.</p>
      <div className="staff-toolbar"><button>Save availability</button><button type="button" onClick={() => setDraft(null)}>Cancel</button></div>
    </fieldset></form>}
  </details>;
}
