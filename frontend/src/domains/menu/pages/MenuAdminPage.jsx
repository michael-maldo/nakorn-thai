import { useEffect } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { allowMenuNavigation, parseMenuAdminRoute } from '../model/menuAdminNavigation';
import useMenuAdminData from '../hooks/useMenuAdminData';
import MenuAdminLayout, { EmptyState } from '../components/MenuAdminLayout';
import MenuItemList from '../components/MenuItemList';
import MenuItemEditor from '../components/MenuItemEditor';
import MenuCollectionList from '../components/MenuCollectionList';
import MenuCollectionDetail from '../components/MenuCollectionDetail';

export default function MenuAdminPage({ hash }) {
  const { authorization } = useAuth();
  const state = useMenuAdminData(authorization);
  const route = parseMenuAdminRoute(hash);
  useEffect(() => { if (!state.loading) document.querySelector('.menu-admin h1')?.focus(); }, [state.loading]);
  useEffect(() => { if (state.error) document.querySelector('.menu-admin-error')?.focus(); }, [state.error]);
  const item = state.menu?.items.find(i => i.id === route?.id);
  const row = state.collections?.find(c => c.collection.id === route?.id);
  const title = !route ? 'Page not found' : route.id === 'new' ? `Create ${route.resource === 'items' ? 'item' : 'collection'}` : route.id ? item?.name || row?.collection.data.name || 'Menu resource' : route.resource === 'items' ? 'Menu items' : 'Menu collections';
  return <MenuAdminLayout route={route} title={title}>
    <header className="menu-admin-page-heading"><div><p className="menu-admin-eyebrow">MENU MANAGEMENT</p><h1 tabIndex={-1}>{title}</h1></div><button type="button" disabled={state.busy} onClick={() => { if (allowMenuNavigation()) state.reload(); }}>Refresh data</button></header>
    {state.error && <p className="menu-admin-error" role="alert" tabIndex={-1}>{state.error}</p>}
    {state.notice && <p className="menu-admin-success" role="status">{state.notice}</p>}
    {state.needsReload && <p role="status">Refresh data before making further changes.</p>}
    {state.loading ? <p className="menu-admin-empty" role="status">Loading menu administration…</p> : !route ? <EmptyState title="This menu view does not exist">Use Items or Collections in the navigation.</EmptyState> : state.menu && <div key={state.revision}>
      {!route.id ? route.resource === 'items' ? <MenuItemList menu={state.menu} /> : <MenuCollectionList rows={state.collections} />
        : route.resource === 'items' ? (item || route.id === 'new' ? <MenuItemEditor item={item} route={route} state={state} /> : <EmptyState title="Item not found">It may have been removed. Return to the Items list.</EmptyState>)
          : row || route.id === 'new' ? <MenuCollectionDetail row={row} route={route} state={state} /> : <EmptyState title="Collection not found">Return to the Collections list to choose a collection.</EmptyState>}
    </div>}
  </MenuAdminLayout>;
}
