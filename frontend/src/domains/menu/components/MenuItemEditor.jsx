import { useState } from 'react';
import { archiveMenuItem, saveMenuItem } from '../api/menuApi';
import { itemSections, menuAdminHref } from '../model/menuAdminNavigation';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { FormActions, OrderField, StatusBadge } from './MenuAdminLayout';
import MenuImageEditor from './MenuImageEditor';

export default function MenuItemEditor({ item, route, state }) {
  const initial = item || { name: '', slug: '', description: '', categoryId: state.menu.categories[0]?.id || '', status: 'DRAFT', available: true, displayOrder: 0, collectionIds: [], prices: [], version: null };
  const form = useMenuAdminForm(initial, state.busy);
  const { draft, field } = form;
  const [archive, setArchive] = useState(false), [imageBusy, setImageBusy] = useState(false);
  async function save(event) {
    event.preventDefault();
    const result = await state.mutate(() => saveMenuItem(draft, state.authorization, true), 'Item saved.', form.committed);
    if (!item && result.committed) state.navigate(menuAdminHref('items', result.result.id));
  }
  return <>
    {item && <><div className="menu-admin-meta"><StatusBadge value={item.status} /><span>{item.available ? 'Enabled for ordering' : 'Unavailable for ordering'}</span></div><nav className="menu-admin-subnav" aria-label="Item sections">{itemSections.map(section => <a key={section} aria-current={route.section === section ? 'page' : undefined} href={menuAdminHref('items', item.id, section)}>{section === 'pricing' ? 'Pricing / variations' : section[0].toUpperCase() + section.slice(1)}</a>)}</nav></>}
    {route.section === 'images' ? <div className="menu-admin-form"><h2>Item image</h2><MenuImageEditor item={item} authorization={state.authorization} csrf={true} disabled={state.disabled} onBusy={setImageBusy} onSaved={async () => { await state.reload(); }} /><p>{imageBusy ? 'Saving image…' : 'Image changes save independently of item details.'}</p></div> : <form className="menu-admin-form" onSubmit={save}>
      <fieldset disabled={state.disabled}><legend>{route.section === 'pricing' ? 'Pricing & variations' : route.section === 'collections' ? 'Collection membership' : 'Item overview'}</legend>
        {route.section === 'overview' && <><p className="menu-admin-muted">The canonical item is shared by all its collections.</p><div className="menu-admin-fields">
          <label>Name<input autoComplete="off" required maxLength={150} value={draft.name} onChange={e => field('name', e.target.value)} /></label>
          <label>Slug<input required maxLength={180} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={draft.slug} onChange={e => field('slug', e.target.value)} /><small>Unique lowercase words separated by hyphens.</small></label>
          <label className="menu-admin-wide">Description<textarea required maxLength={10000} rows={4} value={draft.description} onChange={e => field('description', e.target.value)} /></label>
          <label>Canonical category<select required value={draft.categoryId} onChange={e => field('categoryId', e.target.value)}>{state.menu.categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
          <label>Status<select value={draft.status} onChange={e => field('status', e.target.value)}>{['DRAFT', 'PUBLISHED', 'ARCHIVED'].map(s => <option key={s}>{s}</option>)}</select></label>
          <OrderField value={draft.displayOrder} onChange={value => field('displayOrder', value)} /><label className="menu-admin-check"><input type="checkbox" checked={draft.available} onChange={e => field('available', e.target.checked)} />Enabled for ordering</label>
        </div><p className="menu-admin-hint">Changing the name or description clears dietary verification and requires allergen review. Food declarations and option configuration are not editable in this interface.</p>{!item && <p>After creating this item, add pricing, choose collections and upload an image.</p>}</>}
        {route.section === 'pricing' && <><p>Prices are in AUD. Each existing variation has its own price; collection overrides remain separate.</p><div className="menu-admin-fields">{draft.prices.map((price, index) => <label key={price.id || 'standard'}>{price.name || 'Standard'} price (AUD)<input type="number" min="0" max="9999999.99" step="0.01" required value={price.amount} onChange={e => field('prices', draft.prices.map((p, n) => n === index ? { ...p, amount: e.target.value } : p))} /></label>)}</div>{!draft.prices.length && <button type="button" onClick={() => field('prices', [{ id: null, name: 'Standard', amount: '' }])}>Add standard price</button>}<p className="menu-admin-muted">Additional variation creation is not supported by the current item editor.</p></>}
        {route.section === 'collections' && <><p>Choose where this item belongs. Existing collection-specific category and price settings are preserved when membership stays selected.</p><div className="menu-admin-choice-list">{state.menu.collections.map(c => <label className="menu-admin-check" key={c.id}><input type="checkbox" checked={draft.collectionIds.includes(c.id)} onChange={e => field('collectionIds', e.target.checked ? [...draft.collectionIds, c.id] : draft.collectionIds.filter(id => id !== c.id))} />{c.name}</label>)}</div><p className="menu-admin-hint">Unchecking a collection removes only its membership, including that membership’s placement and price override. Manage those settings from the collection’s Items view.</p></>}
        <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty} onCancel={() => { form.reset(); state.clearFeedback(); }} label={item ? 'Save changes' : 'Create item'} />
      </fieldset>
    </form>}
    {item && route.section === 'overview' && item.status !== 'ARCHIVED' && <section className="menu-admin-danger-zone"><h2>Archive item</h2><p>Hide this item from the public menu. Its data and historical orders remain intact.</p>{archive ? <><p>Archive <strong>{item.name}</strong>? You can restore it by changing its status.</p><button className="menu-admin-danger" disabled={state.disabled || form.dirty} onClick={() => state.mutate(() => archiveMenuItem(item, state.authorization, true), 'Item archived.')}>Confirm archive</button> <button disabled={state.busy} onClick={() => setArchive(false)}>Keep item</button></> : <button className="menu-admin-danger" disabled={state.disabled || form.dirty} onClick={() => setArchive(true)}>Archive item</button>}</section>}
  </>;
}
