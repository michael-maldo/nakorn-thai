import { useState } from 'react';
import { removeCollectionChild, saveCollectionChild } from '../api/menuApi';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { ConfirmRemoval, EmptyState, FormActions, OrderField } from './MenuAdminLayout';

export default function MenuCollectionCategories({ row, state }) {
  const [editor, setEditor] = useState(null), [pending, setPending] = useState(null);
  const categoryName = id => state.menu.categories.find(c => c.id === id)?.name || 'Unknown category';
  const unused = state.menu.categories.filter(c => !row.categories.some(p => p.data.categoryId === c.id));
  if (editor) return <CategoryEditor resource={editor} row={row} state={state} onCancel={() => { setEditor(null); state.clearFeedback(); }} />;
  return <>
    <div className="menu-admin-section-heading"><div><h2>Category placements</h2><p>Choose existing categories and their order within this collection.</p></div><button className="menu-admin-primary" disabled={state.disabled || !unused.length} onClick={() => setEditor({ version: null, data: { categoryId: unused[0].id, displayOrder: 0 } })}>Add category placement</button></div>
    <p className="menu-admin-hint">Items without an explicit collection placement use their canonical category. Assign placements in the collection’s Items view.</p>
    {pending && <ConfirmRemoval title={`Remove ${categoryName(pending.data.categoryId)} placement?`} busy={state.disabled} onCancel={() => setPending(null)} onConfirm={() => state.mutate(() => removeCollectionChild(row.collection.id, 'categories', pending, state.authorization), 'Category placement removed.', () => setPending(null))}>Only the collection placement is removed. The canonical category remains.</ConfirmRemoval>}
    {row.categories.length ? <div className="menu-admin-table-wrap"><table className="menu-admin-table"><caption className="sr-only">Category placements</caption><thead><tr><th>Category</th><th>Order</th><th>Assigned items</th><th>Actions</th></tr></thead><tbody>{row.categories.map(c => { const count = row.memberships.filter(m => m.data.collectionCategoryId === c.id).length; return <tr key={c.id}>
      <td data-label="Category"><strong>{categoryName(c.data.categoryId)}</strong></td><td data-label="Order">{c.data.displayOrder}</td><td data-label="Assigned items">{count}</td><td data-label="Actions"><div className="menu-admin-actions"><button disabled={state.disabled} onClick={() => { setPending(null); setEditor(c); }}>Edit placement</button><button className="menu-admin-danger" disabled={state.disabled || count > 0} onClick={() => setPending(c)}>Remove placement</button></div>{count > 0 && <small>Reassign these items before removing the placement.</small>}</td>
    </tr>; })}</tbody></table></div> : <EmptyState title="No collection category placements">Items currently use their canonical categories. Add a placement to arrange them differently here.</EmptyState>}
  </>;
}
function CategoryEditor({ resource, row, state, onCancel }) {
  const form = useMenuAdminForm(resource.data, state.busy);
  return <form className="menu-admin-form" onSubmit={event => { event.preventDefault(); state.mutate(() => saveCollectionChild(row.collection.id, 'categories', { ...resource, data: form.draft }, state.authorization), 'Category placement saved.', () => { form.committed(); onCancel(); }); }}><fieldset disabled={state.disabled}><legend>{resource.id ? 'Edit category placement' : 'Add category placement'}</legend><div className="menu-admin-fields">
    <label>Category<select required value={form.draft.categoryId} onChange={e => form.field('categoryId', e.target.value)}>{state.menu.categories.filter(c => c.id === resource.data.categoryId || !row.categories.some(p => p.data.categoryId === c.id)).map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label><OrderField value={form.draft.displayOrder} onChange={value => form.field('displayOrder', value)} />
  </div><FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty || !resource.id} onCancel={() => { form.reset(); onCancel(); }} label="Save placement" /></fieldset></form>;
}
