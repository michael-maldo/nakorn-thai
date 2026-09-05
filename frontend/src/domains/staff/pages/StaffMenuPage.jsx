import { useState } from 'react';
import MenuImageEditor from '../../menu/components/MenuImageEditor';
import { archiveMenuItem, getStaffCsrf, getStaffMenu, saveMenuItem } from '../../menu/api/menuApi';

const blank = { name: '', slug: '', description: '', categoryId: '', status: 'DRAFT', available: true, displayOrder: 0, collectionIds: [], version: null };

export default function StaffMenuPage() {
  const [authorization, setAuthorization] = useState('');
  const [csrf, setCsrf] = useState(null);
  const [menu, setMenu] = useState(null);
  const [draft, setDraft] = useState(null);
  const [pendingArchive, setPendingArchive] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [filter, setFilter] = useState('');
  const [needsReload, setNeedsReload] = useState(false);

  async function login(event) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const bytes = new TextEncoder().encode(`${values.get('username')}:${values.get('password')}`);
    const auth = `Basic ${btoa(String.fromCharCode(...bytes))}`;
    setBusy(true); setError('');
    try {
      const token = await getStaffCsrf(auth);
      const data = await getStaffMenu(auth);
      setCsrf(token); setAuthorization(auth); setMenu(data);
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  async function refresh() {
    setBusy(true); setError('');
    try { setMenu(await getStaffMenu(authorization)); setNeedsReload(false); setDraft(null); setPendingArchive(null); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  async function mutate(action, message) {
    setBusy(true); setError(''); setNotice('');
    try {
      await action();
      setDraft(null); setPendingArchive(null); setNotice(message);
      // A successful write must not be repeated if the following refresh fails.
      setNeedsReload(true);
      setMenu(await getStaffMenu(authorization));
      setNeedsReload(false);
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  function field(name, value) { setDraft((current) => ({ ...current, [name]: value })); }
  function signOut() {
    setAuthorization(''); setCsrf(null); setMenu(null); setDraft(null);
    setPendingArchive(null); setError(''); setNotice(''); setNeedsReload(false);
  }
  const visible = menu?.items.filter((item) => (showArchived || item.status !== 'ARCHIVED') && item.name.toLowerCase().includes(filter.toLowerCase())) || [];

  return <main className="staff-menu page-width">
    <header className="staff-heading"><div><a href="#home">← Restaurant website</a><h1>Menu dashboard</h1></div>
      {menu && <button type="button" disabled={busy} onClick={signOut}>Sign out</button>}
    </header>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {!menu ? <form className="staff-panel staff-login" onSubmit={login}>
      <h2>Staff sign in</h2><p>Use your configured menu administrator account.</p>
      <label>Username<input name="username" autoComplete="username" required disabled={busy} /></label>
      <label>Password<input name="password" type="password" autoComplete="current-password" required disabled={busy} /></label>
      <button className="button button-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    </form> : <>
      <div className="staff-summary" aria-label="Menu summary">
        <span><strong>{menu.items.filter((i) => i.status === 'PUBLISHED').length}</strong> Published</span>
        <span><strong>{menu.items.filter((i) => i.status === 'DRAFT').length}</strong> Drafts</span>
        <span><strong>{menu.items.filter((i) => i.status === 'ARCHIVED').length}</strong> Archived</span>
      </div>
      <div className="staff-toolbar">
        <label>Find a dish<input type="search" value={filter} onChange={(e) => setFilter(e.target.value)} /></label>
        <label className="staff-check"><input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Show archived</label>
        <button disabled={busy} onClick={refresh}>Reload menu</button>
        <button className="button button-primary" disabled={busy || needsReload || !menu.categories.length} onClick={() => { setDraft({ ...blank, categoryId: menu.categories[0].id }); setPendingArchive(null); setError(''); setNotice(''); }}>Add dish</button>
      </div>
      {needsReload && <p role="status">Reload the menu to continue editing.</p>}
      {draft && <form className="staff-panel" onSubmit={(event) => { event.preventDefault(); mutate(() => saveMenuItem(draft, authorization, csrf), 'Dish saved. Published dishes in Signature Dishes now appear on the homepage.'); }}>
        <h2>{draft.id ? 'Edit dish' : 'Add dish'}</h2>
        <fieldset disabled={busy || needsReload} className="staff-form-grid">
          <label>Name<input required maxLength={150} value={draft.name} onChange={(e) => field('name', e.target.value)} /></label>
          <label>Slug<input required maxLength={180} pattern="[a-z0-9]+(-[a-z0-9]+)*" placeholder="yellow-curry" value={draft.slug} onChange={(e) => field('slug', e.target.value)} /><small>Unique lowercase words separated by hyphens.</small></label>
          <label className="staff-wide">Description<textarea required maxLength={10000} rows={3} value={draft.description} onChange={(e) => field('description', e.target.value)} /></label>
          <label>Category<select required value={draft.categoryId} onChange={(e) => field('categoryId', e.target.value)}>{menu.categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
          <label>Publication<select value={draft.status} onChange={(e) => field('status', e.target.value)}>{['DRAFT', 'PUBLISHED', 'ARCHIVED'].map((status) => <option key={status}>{status}</option>)}</select></label>
          <label>Display order<input required type="number" min="0" max="2147483647" step="1" value={draft.displayOrder} onChange={(e) => field('displayOrder', e.target.value === '' ? '' : Number(e.target.value))} /></label>
          <label className="staff-check"><input type="checkbox" checked={draft.available} onChange={(e) => field('available', e.target.checked)} /> Available to order</label>
          <fieldset className="staff-wide"><legend>Collections</legend><p>Select Signature Dishes to feature a published dish on the homepage.</p>
            {menu.collections.map((collection) => <label className="staff-check" key={collection.id}><input type="checkbox" checked={draft.collectionIds.includes(collection.id)} onChange={(e) => field('collectionIds', e.target.checked ? [...draft.collectionIds, collection.id] : draft.collectionIds.filter((id) => id !== collection.id))} />{collection.name}</label>)}
          </fieldset>
          {draft.id ? <MenuImageEditor key={`${draft.id}-${draft.version}`} item={menu.items.find((item) => item.id === draft.id)} authorization={authorization} csrf={csrf} onBusy={setBusy} disabled={busy || needsReload || JSON.stringify(draft) !== JSON.stringify(menu.items.find((item) => item.id === draft.id))} onSaved={async () => {
            setNeedsReload(true);
            setDraft(null);
            setNotice('Photo and focus saved.');
            try { setMenu(await getStaffMenu(authorization)); setNeedsReload(false); }
            catch (failure) { setError(failure.message); }
          }} /> : <p className="staff-wide">Save the new dish first, then edit it to add a photograph.</p>}
          <p className="staff-wide">Changing the name or description clears dietary verification and requires allergen review. Prices, variations, and food declarations are outside this initial editor.</p>
          <div className="staff-toolbar staff-wide"><button className="button button-primary">{busy ? 'Saving…' : 'Save dish'}</button><button type="button" onClick={() => setDraft(null)}>Cancel</button></div>
        </fieldset>
      </form>}
      {pendingArchive && <section className="staff-panel" aria-label="Confirm archive"><h2>Archive {pendingArchive.name}?</h2><p>This removes the dish from the public menu. You can restore it by editing its publication status.</p><div className="staff-toolbar"><button disabled={busy || needsReload} onClick={() => mutate(() => archiveMenuItem(pendingArchive, authorization, csrf), 'Dish archived.')}>Confirm archive</button><button disabled={busy} onClick={() => setPendingArchive(null)}>Cancel</button></div></section>}
      <div className="staff-table-wrap"><table className="staff-table"><caption>Menu items</caption><thead><tr><th>Dish</th><th>Category</th><th>Status</th><th>Availability</th><th>Actions</th></tr></thead><tbody>
        {visible.map((item) => <tr key={item.id}><td><strong>{item.name}</strong><small>{item.slug}</small></td><td>{menu.categories.find((c) => c.id === item.categoryId)?.name}</td><td>{item.status}</td><td>{item.available ? 'Available' : 'Unavailable'}</td><td><div className="staff-toolbar"><button disabled={busy || needsReload} onClick={() => { setDraft({ ...item }); setPendingArchive(null); setError(''); setNotice(''); }}>Edit</button>{item.status !== 'ARCHIVED' && <button disabled={busy || needsReload} onClick={() => { setPendingArchive(item); setDraft(null); }}>Archive</button>}</div></td></tr>)}
      </tbody></table>{!visible.length && <p>No dishes match this view.</p>}</div>
    </>}
  </main>;
}
