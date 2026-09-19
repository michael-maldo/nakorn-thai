import { useState } from 'react';
import { availabilityLabel, menuAdminHref } from '../model/menuAdminNavigation';
import { EmptyState, StatusBadge } from './MenuAdminLayout';

export default function MenuCollectionList({ rows }) {
  const [query, setQuery] = useState('');
  const visible = rows.filter(r => `${r.collection.data.name} ${r.collection.data.slug}`.toLowerCase().includes(query.toLowerCase()));
  return <>
    <div className="menu-admin-toolbar"><label>Search collections<input type="search" placeholder="Name or slug" value={query} onChange={e => setQuery(e.target.value)} /></label><a className="menu-admin-primary" href={menuAdminHref('collections', 'new')}>Create collection</a></div>
    <p className="menu-admin-muted">{visible.length} collections · Availability includes restaurant opening rules and is evaluated when refreshed.</p>
    {visible.length ? <div className="menu-admin-table-wrap"><table className="menu-admin-table"><caption className="sr-only">Menu collections</caption><thead><tr><th>Collection</th><th>Status</th><th>Active</th><th>Available now</th><th>Items</th><th>Timing</th><th>Order</th></tr></thead><tbody>{visible.map(row => <tr key={row.collection.id}>
      <td data-label="Collection"><a className="menu-admin-resource-link" href={menuAdminHref('collections', row.collection.id)}>{row.collection.data.name}</a><small>{row.collection.data.slug}</small></td><td data-label="Status"><StatusBadge value={row.collection.data.status} /></td><td data-label="Active">{row.collection.data.active ? 'Active' : 'Inactive'}</td>
      <td data-label="Available now"><StatusBadge value={availabilityLabel(row.orderingAvailability)} positive={row.orderingAvailability?.available} /></td><td data-label="Items">{row.memberships.length}</td><td data-label="Timing">{row.collection.data.dailyCutoffTime ? `Before ${row.collection.data.dailyCutoffTime}` : 'No daily cutoff'}<small>{row.collection.data.dailyCutoffTime && row.restaurantTimezone}</small><small>{row.schedules.length ? `${row.schedules.length} schedule rules` : 'No collection schedule'}</small></td><td data-label="Order">{row.collection.data.displayOrder}</td>
    </tr>)}</tbody></table></div> : <EmptyState title={rows.length ? 'No matching collections' : 'No collections yet'}>{rows.length ? 'Try another name or slug.' : 'Create a collection, then add existing menu items.'}</EmptyState>}
  </>;
}
