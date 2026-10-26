import { useState } from 'react';
import { removeCollectionChild, saveCollectionMembership } from '../api/menuApi';
import { money } from '../model/menuAdminNavigation';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { ConfirmRemoval, EmptyState, FormActions, OrderField } from './MenuAdminLayout';

export default function MenuCollectionItems({ row, state }) {
  const [editor, setEditor] = useState(null), [pending, setPending] = useState(null), [query, setQuery] = useState('');
  const items = state.menu.items;
  const name = id => items.find(i => i.id === id)?.name || 'Unknown item';
  const category = id => state.menu.categories.find(c => c.id === id)?.name || 'Unknown category';
  const available = items.filter(i => !row.memberships.some(m => m.id === i.id));
  const visible = row.memberships.filter(m => name(m.id).toLowerCase().includes(query.toLowerCase()));
  if (editor) return <MembershipEditor resource={editor} row={row} state={state} onCancel={() => { setEditor(null); state.clearFeedback(); }} />;
  return <>
    <div className="menu-admin-section-heading"><div><h2>Collection items</h2><p>Manage placement and pricing for this collection without changing the shared menu item.</p></div><button className="menu-admin-primary" disabled={state.disabled || !available.length} onClick={() => setEditor({ id: available[0].id, version: null, data: { collectionCategoryId: null, priceOverrideMinor: null, displayOrder: 0 } })}>Add existing item</button></div>
    <label className="menu-admin-search">Find an item<input type="search" value={query} onChange={e => setQuery(e.target.value)} /></label>
    {pending && <ConfirmRemoval title={`Remove ${name(pending.id)} from this collection?`} busy={state.disabled} onCancel={() => setPending(null)} onConfirm={() => state.mutate(() => removeCollectionChild(row.collection.id, 'items', pending, state.authorization), 'Collection membership removed.', () => setPending(null))}>Only this collection membership, its placement and its price override will be removed. The canonical menu item, its other collections and historical orders remain.</ConfirmRemoval>}
    {visible.length ? <div className="menu-admin-table-wrap"><table className="menu-admin-table"><caption className="sr-only">Collection memberships</caption><thead><tr><th>Item</th><th>Category placement</th><th>Price override</th><th>Order</th><th>Actions</th></tr></thead><tbody>{visible.map(m => <tr key={m.id}>
      <td data-label="Item"><strong>{name(m.id)}</strong></td><td data-label="Category placement">{m.data.collectionCategoryId ? category(row.categories.find(c => c.id === m.data.collectionCategoryId)?.data.categoryId) : category(items.find(i => i.id === m.id)?.categoryId)}<small>{m.data.collectionCategoryId ? 'Collection placement' : 'Canonical item category'}</small></td>
      <td data-label="Price override">{m.data.priceOverrideMinor == null ? 'Use item price' : money(m.data.priceOverrideMinor)}</td><td data-label="Order">{m.data.displayOrder}</td><td data-label="Actions"><div className="menu-admin-actions"><button disabled={state.disabled} onClick={() => { setPending(null); setEditor(m); }}>Edit membership<span className="sr-only"> for {name(m.id)}</span></button><button className="menu-admin-danger" disabled={state.disabled} onClick={() => setPending(m)}>Remove membership<span className="sr-only"> for {name(m.id)}</span></button></div></td>
    </tr>)}</tbody></table></div> : <EmptyState title={row.memberships.length ? 'No matching items' : 'This collection has no items'}>{row.memberships.length ? 'Try another item name.' : 'Add an existing menu item. No duplicate item is created.'}</EmptyState>}
  </>;
}
function MembershipEditor({ resource, row, state, onCancel }) {
  const initial = { ...resource.data, itemId: resource.id };
  const form = useMenuAdminForm(initial, state.busy);
  const { draft, field } = form;
  const options = state.menu.items.filter(i => i.id === resource.id || !row.memberships.some(m => m.id === i.id));
  return <form className="menu-admin-form" onSubmit={event => { event.preventDefault(); state.mutate(() => saveCollectionMembership(row.collection.id, draft.itemId, { version: resource.version, data: { collectionCategoryId: draft.collectionCategoryId, priceOverrideMinor: draft.priceOverrideMinor, displayOrder: draft.displayOrder } }, state.authorization), 'Collection membership saved.', () => { form.committed(); onCancel(); }); }}>
    <fieldset disabled={state.disabled}><legend>{resource.version === null ? 'Add existing item' : 'Edit collection membership'}</legend><p>These settings apply only to {row.collection.data.name}.</p><div className="menu-admin-fields">
      <label className="menu-admin-wide">Menu item<select disabled={resource.version !== null} value={draft.itemId} onChange={e => field('itemId', e.target.value)}>{options.map(i => <option key={i.id} value={i.id}>{i.name} ({i.status})</option>)}</select></label>
      <label>Category placement<select value={draft.collectionCategoryId || ''} onChange={e => field('collectionCategoryId', e.target.value || null)}><option value="">Use canonical item category</option>{row.categories.map(c => <option key={c.id} value={c.id}>{state.menu.categories.find(category => category.id === c.data.categoryId)?.name}</option>)}</select></label>
      <OrderField value={draft.displayOrder} onChange={value => field('displayOrder', value)} />
      <label>Price override (AUD cents)<input type="number" min={0} max={Number.MAX_SAFE_INTEGER} step={1} value={draft.priceOverrideMinor ?? ''} onChange={e => field('priceOverrideMinor', e.target.value === '' ? null : Number(e.target.value))} /><small>Blank uses the item price. 0 is free; 1590 means $15.90.</small></label>
    </div><p className="menu-admin-hint">The override applies only to the default/base variation. Other variation prices and option surcharges are unchanged.</p>
    <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty || resource.version === null} onCancel={() => { form.reset(); onCancel(); }} label="Save membership" /></fieldset>
  </form>;
}
