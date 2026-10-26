import { collectionSections, menuAdminHref } from '../model/menuAdminNavigation';
import { StatusBadge } from './MenuAdminLayout';
import MenuCollectionOverview from './MenuCollectionOverview';
import MenuCollectionItems from './MenuCollectionItems';
import MenuCollectionCategories from './MenuCollectionCategories';
import MenuCollectionAvailability from './MenuCollectionAvailability';

export default function MenuCollectionDetail({ row, route, state }) {
  return <>
    {row && <><div className="menu-admin-meta"><StatusBadge value={row.collection.data.status} /><span>{row.collection.data.active ? 'Active' : 'Inactive'}</span><span>{row.memberships.length} items</span></div><nav className="menu-admin-subnav" aria-label="Collection sections">{collectionSections.map(section => <a key={section} aria-current={route.section === section ? 'page' : undefined} href={menuAdminHref('collections', row.collection.id, section)}>{section[0].toUpperCase() + section.slice(1)}</a>)}</nav></>}
    {route.section === 'overview' && <MenuCollectionOverview row={row} state={state} />}
    {route.section === 'items' && <MenuCollectionItems row={row} state={state} />}
    {route.section === 'categories' && <MenuCollectionCategories row={row} state={state} />}
    {route.section === 'availability' && <MenuCollectionAvailability row={row} state={state} />}
  </>;
}
