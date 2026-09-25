import { useEffect, useMemo, useRef, useState } from 'react';
import Header from '../../../website/components/Header';
import Footer from '../../../website/components/Footer';
import useMenu from '../hooks/useMenu';
import { collectionAvailability, menuSections } from '../model/menuCollections';
import MenuItemCard from '../components/MenuItemCard';
import MenuCategoryNavigation from '../components/MenuCategoryNavigation';
import { useCart } from '../../ordering/model/CartContext';
import { getOrderingOptions } from '../../ordering/api/orderApi';

export default function MenuPage() {
  const navigationRef = useRef(null);
  const [selectedId, setSelectedId] = useState(null);
  const { collections, menu, loading, error, retry } = useMenu(selectedId);
  const [search, setSearch] = useState('');
  const [enabled, setEnabled] = useState(false);
  const [orderingMessage, setOrderingMessage] = useState('Online ordering is currently closed or unavailable.');
  const [orderingAttempt, setOrderingAttempt] = useState(0);
  const [added, setAdded] = useState('');
  const { cart, dispatch } = useCart();
  useEffect(() => {
    let active = true;
    getOrderingOptions().then((options) => { if (active) { setEnabled(options.enabled === true); setOrderingMessage(options.message || 'Online ordering is currently closed or unavailable.'); } }).catch(() => { if (active) { setEnabled(false); setOrderingMessage('Ordering availability could not be checked. Please refresh and try again.'); } });
    return () => { active = false; };
  }, [orderingAttempt]);
  const sections = useMemo(() => menu ? menuSections(menu, search).filter(section => section.items.length > 0) : [], [menu, search]);
  const count = sections.reduce((sum, section) => sum + section.items.length, 0);
  function reload() { setEnabled(false); setOrderingAttempt((value) => value + 1); retry(); }
  function select(id) { setSelectedId(id); setSearch(''); setAdded(''); }
  return <>
    <Header currentPage="Menu" />
    <main className="restaurant-menu page-width">
      <div className="restaurant-menu-heading">
        <p className="restaurant-menu-eyebrow">Nakorn Thai Restaurant &amp; Bar</p><h1>Our menu</h1>
        <p>Explore our dishes. All prices are in Australian dollars.</p>
      </div>
      {collections.length > 0 && <div ref={navigationRef} className="restaurant-menu-navigation">
        <label className="restaurant-menu-search">Menu collection
          <select value={menu?.id ?? selectedId ?? ''} onChange={(event) => select(event.target.value)}>
            {!menu && <option value="">Choose a collection</option>}
            {collections.map((collection) => <option key={collection.id} value={collection.id}>
              {collection.name}{!collection.availability.available && ' — unavailable to order'}
            </option>)}
          </select>
        </label>
        {sections.length > 0 && <MenuCategoryNavigation key={menu.id} categories={sections} navigationRef={navigationRef} />}
      </div>}
      <p className="sr-only" role="status">{added}</p>
      {loading && <p role="status">Loading our menu…</p>}
      {error && <div role="alert"><p>{error}</p><button className="button button-outline" onClick={reload}>Reload menu</button></div>}
      {!loading && !error && !menu && <p>No public menus are available right now. Please check back soon.</p>}
      {menu && <>
        <h2>{menu.name}</h2>{menu.description && <p>{menu.description}</p>}
        {!menu.availability.available && <p className="dish-unavailable" role="status">{collectionAvailability(menu)} You can still browse the dishes.</p>}
        {!enabled && <p>{orderingMessage} You can still browse the menu.</p>}
        <button type="button" className="button button-outline" onClick={reload}>Refresh menu and availability</button>
        <label className="restaurant-menu-search">Find a dish<input type="search" value={search} placeholder="Search dishes or descriptions" onChange={(event) => setSearch(event.target.value)} /></label>
        <p role="status">{count} {count === 1 ? 'dish' : 'dishes'}{search && ' found'}</p>
        {sections.map((section) => <section key={section.id} id={`menu-section-${section.id}`} aria-labelledby={`menu-category-${section.id}`}>
          <h2 id={`menu-category-${section.id}`}>{section.name}</h2>
          <div className="restaurant-menu-grid">{section.items.map((item) => <MenuItemCard
            key={`${menu.id}:${item.id}:${menu.availability.evaluatedAt}`} item={item} collection={menu} enabled={enabled} cart={cart}
            onAdd={(line) => { dispatch({ type: 'add', line }); setAdded(`${line.dishName} added to your cart.`); }} />)}</div>
        </section>)}
        {search && !count && <button type="button" onClick={() => setSearch('')}>Clear search</button>}
      </>}
    </main><Footer />
  </>;
}
