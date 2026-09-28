import { useEffect, useState } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { getHomepageSettings, saveHomepageSettings } from '../api/menuApi';
import { useMenuNavigationGuard } from '../hooks/useMenuAdminForm';
import { allowMenuNavigation } from '../model/menuAdminNavigation';

export default function HomepageSettingsPage() {
  const { authorization } = useAuth();
  const [settings, setSettings] = useState(null), [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(false), [needsReload, setNeedsReload] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState('');
  const dirty = settings !== null && JSON.stringify(selected) !== JSON.stringify(settings.collectionIds);
  const guard = useMenuNavigationGuard(dirty, busy);
  function loaded(data) { setSettings(data); setSelected(data.collectionIds); setNeedsReload(false); }
  useEffect(() => {
    let active = true;
    getHomepageSettings(authorization).then(data => { if (active) loaded(data); })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [authorization]);
  async function reload() {
    if (!allowMenuNavigation()) return;
    setBusy(true); setError(''); setNotice('');
    try { loaded(await getHomepageSettings(authorization)); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const data = await saveHomepageSettings({ version: settings.version, collectionIds: selected }, authorization);
      guard.current.dirty = false; loaded(data); setNotice('Home page collections saved.');
    } catch (e) { setError(e.message); if (e.status === 409) setNeedsReload(true); }
    finally { setBusy(false); }
  }
  function move(index, direction) {
    setSelected(previous => {
      const next = [...previous];
      [next[index], next[index + direction]] = [next[index + direction], next[index]];
      return next;
    });
  }
  return <main className="staff-menu page-width">
    <header className="staff-heading"><h1>Home page</h1><button type="button" disabled={busy} onClick={reload}>Refresh settings</button></header>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {!settings && !error && <p role="status">Loading home page settings…</p>}
    {needsReload && <p>These settings changed elsewhere. Refresh before saving again.</p>}
    {settings && <form className="staff-panel" onSubmit={save}>
      <fieldset disabled={busy || needsReload}>
        <legend>Signature dishes collections</legend>
        <p>Select up to 20 collections. The home page shows the first four dishes from each in the order below. Dish order follows each collection’s category and item ordering.</p>
        <p>Only published collections appear publicly. Collections outside ordering hours remain visible for browsing. Clear all selections to hide this section.</p>
        <div className="homepage-collection-choices">
          {settings.collections.map(collection => <label className="staff-check" key={collection.id}>
            <input type="checkbox" checked={selected.includes(collection.id)} disabled={!selected.includes(collection.id) && selected.length >= 20}
              onChange={event => setSelected(previous => event.target.checked ? [...previous, collection.id] : previous.filter(id => id !== collection.id))} />
            {collection.name} {collection.status !== 'PUBLISHED' && `(${collection.status.toLowerCase()} — hidden on home page)`}
          </label>)}
        </div>
        {!settings.collections.length && <p>Create a collection in Menu before featuring it here.</p>}
        <h2>Display order</h2>
        {!selected.length && <p>No collections selected. The Signature Dishes section will be hidden.</p>}
        <ol className="homepage-collection-order">{selected.map((id, index) => {
          const name = settings.collections.find(c => c.id === id)?.name || 'Missing collection';
          return <li key={id}><span>{name}</span><div>
            <button type="button" disabled={index === 0} aria-label={`Move ${name} up`} onClick={() => move(index, -1)}>Move up</button>
            <button type="button" disabled={index === selected.length - 1} aria-label={`Move ${name} down`} onClick={() => move(index, 1)}>Move down</button>
          </div></li>;
        })}</ol>
        <div className="staff-toolbar"><button type="submit" disabled={!dirty}>{busy ? 'Saving…' : 'Save home page collections'}</button>
          <button type="button" disabled={!dirty} onClick={() => { setSelected(settings.collectionIds); setError(''); }}>Cancel changes</button>
          <a href="#home">View home page</a></div>
      </fieldset>
    </form>}
  </main>;
}
