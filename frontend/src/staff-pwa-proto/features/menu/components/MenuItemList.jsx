import { useState } from 'react';
import { menuAdminHref, money } from '../model/menuAdminNavigation';
import { EmptyState, StatusBadge } from './MenuAdminLayout';

export default function MenuItemList({ menu }) {
  const [query, setQuery] = useState(''), [status, setStatus] = useState('current');
  const category = id => menu.categories.find(c => c.id === id)?.name || 'Uncategorised';
  const items = menu.items.filter(i => (status === 'all' || (status === 'current' ? i.status !== 'ARCHIVED' : i.status === status)) && `${i.name} ${category(i.categoryId)}`.toLowerCase().includes(query.toLowerCase()));
  return <>
    <div className="menu-admin-toolbar"><label>Search items<input type="search" placeholder="Name or category" value={query} onChange={e => setQuery(e.target.value)} /></label><label>Status<select value={status} onChange={e => setStatus(e.target.value)}><option value="current">Current items</option><option value="all">All statuses</option><option value="PUBLISHED">Published</option><option value="DRAFT">Draft</option><option value="ARCHIVED">Archived</option></select></label><a className="menu-admin-primary" href={menuAdminHref('items', 'new')}>Create item</a></div>
    <p className="menu-admin-muted">{items.length} {items.length === 1 ? 'item' : 'items'} · Open an item to manage its details, prices, collections and image.</p>
    {items.length ? <div className="menu-admin-table-wrap"><table className="menu-admin-table"><caption className="sr-only">Menu items</caption><thead><tr><th>Item</th><th>Category</th><th>Price / variations</th><th>Status</th><th>Availability</th><th>Collections</th></tr></thead><tbody>{items.map(i => <tr key={i.id}>
      <td data-label="Item"><a className="menu-admin-resource-link" href={menuAdminHref('items', i.id)}>{i.name}</a><small>{i.slug}</small></td>
      <td data-label="Category">{category(i.categoryId)}</td><td data-label="Price / variations">{i.prices.length ? i.prices.map(p => <small key={p.id || 'standard'}>{p.name} · {money(Number(p.amount) * 100)}</small>) : 'No price set'}</td>
      <td data-label="Status"><StatusBadge value={i.status} /></td><td data-label="Availability">{i.available ? 'Enabled' : 'Unavailable'}</td><td data-label="Collections">{i.collectionIds.map(id => menu.collections.find(c => c.id === id)?.name).filter(Boolean).join(', ') || 'None'}</td>
    </tr>)}</tbody></table></div> : <EmptyState title={menu.items.length ? 'No matching items' : 'No menu items yet'}>{menu.items.length ? 'Try a different search or status filter.' : 'Create your first item to start building the menu.'}</EmptyState>}
  </>;
}
