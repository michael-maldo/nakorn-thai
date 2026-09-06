import { menuCollections as collections } from '../model/menuCollections';
import { useCart } from '../../ordering/model/CartContext';
import { getOrderingOptions } from '../../ordering/api/orderApi';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import Header from '../../../website/components/Header';
import Footer from '../../../website/components/Footer';
import useMenu from '../hooks/useMenu';
import { presentDish } from '../model/menuModel';

const price = (minor, currency) => new Intl.NumberFormat('en-AU', { style: 'currency', currency }).format(minor / 100);

export default function MenuPage() {
  const [collection, setCollection] = useState('chefs-special-recommendations');
  const { items, loading, error, retry } = useMenu(collection);
  const [search, setSearch] = useState('');
  const { cart, dispatch } = useCart();
  const [enabled, setEnabled] = useState(false);
  const [added, setAdded] = useState('');
  const navigationRef = useRef(null);
  const [scrollable, setScrollable] = useState({ left: false, right: false });
  const changeCollection = (slug) => { setCollection(slug); setSearch(''); setAdded(''); };
  useLayoutEffect(() => {
    const navigation = navigationRef.current;
    const updateControls = () => setScrollable({ left: navigation.scrollLeft > 1, right: navigation.scrollLeft + navigation.clientWidth < navigation.scrollWidth - 1 });
    const observer = new ResizeObserver(updateControls);
    observer.observe(navigation);
    for (const child of navigation.children) observer.observe(child);
    navigation.addEventListener('scroll', updateControls, { passive: true });
    updateControls();
    return () => { observer.disconnect(); navigation.removeEventListener('scroll', updateControls); };
  }, []);
  useEffect(() => {
    const navigation = navigationRef.current;
    const selected = navigation.querySelector('[aria-current="true"]');
    const bounds = navigation.getBoundingClientRect();
    const item = selected.getBoundingClientRect();
    if (item.left < bounds.left) navigation.scrollLeft += item.left - bounds.left;
    else if (item.right > bounds.right) navigation.scrollLeft += item.right - bounds.right;
  }, [collection]);
  const scrollNavigation = (direction) => navigationRef.current.scrollBy({ left: direction * navigationRef.current.clientWidth * .75, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
  useEffect(() => { let active = true; getOrderingOptions().then((options) => { if (active) setEnabled(options.enabled); }).catch(() => {}); return () => { active = false; }; }, []);
  const dishes = items.filter((item) => `${item.name} ${item.description}`.toLowerCase().includes(search.trim().toLowerCase())).map(presentDish);
  return <>
    <Header currentPage="Menu" />
    <main className="restaurant-menu page-width">
      <div className="restaurant-menu-heading">
        <p className="restaurant-menu-eyebrow">Nakorn Thai Restaurant &amp; Bar</p>
        <h1>Our menu</h1>
        <h2>{collections[collection]}</h2>
        <p>Explore our dishes and drinks. All prices are in Australian dollars.</p>
      </div>
      <div className="restaurant-menu-navigation">
        <label className="restaurant-menu-search">Menu collection
          <select value={collection} onChange={(event) => changeCollection(event.target.value)}>
            {Object.entries(collections).map(([slug, name]) => <option key={slug} value={slug}>{name}</option>)}
          </select>
        </label>
        <nav className="restaurant-menu-categories" aria-label="Menu collections">
          <button type="button" className="menu-scroll-control" aria-label="Scroll menu collections left" aria-controls="menu-collections" disabled={!scrollable.left} onClick={() => scrollNavigation(-1)}><span aria-hidden="true">‹</span></button>
          <div ref={navigationRef} id="menu-collections" className="menu-collection-list">
            {Object.entries(collections).map(([slug, name]) => <button type="button" key={slug} aria-current={collection === slug ? 'true' : undefined} onClick={() => changeCollection(slug)}>{name}</button>)}
          </div>
          <button type="button" className="menu-scroll-control" aria-label="Scroll menu collections right" aria-controls="menu-collections" disabled={!scrollable.right} onClick={() => scrollNavigation(1)}><span aria-hidden="true">›</span></button>
        </nav>
      </div>
      {collection === 'lunch-specials' && <p>Lunch specials till 2:30 PM. Please contact the restaurant to order lunch; online lunch ordering is currently unavailable.</p>}
      <p className="sr-only" role="status">{added}</p>
      {loading && <p role="status">Loading our menu…</p>}
      {error && <div role="alert"><p>{error}</p><button className="button button-outline" onClick={retry}>Try again</button></div>}
      {!loading && !error && <>
        {items.length > 0 && <label className="restaurant-menu-search">Find a dish
          <input type="search" placeholder="Search dishes or descriptions" value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>}
        <p role="status">{items.length === 0 ? 'This menu is being updated. Please check back soon.' : `${dishes.length} ${dishes.length === 1 ? 'dish' : 'dishes'}${search ? ' found' : ' on the menu'}`}</p>
        {items.length > 0 && dishes.length === 0 && <button className="button button-outline" onClick={() => setSearch('')}>Clear search</button>}
        <div className="restaurant-menu-grid">
          {dishes.map((dish) => <article className="restaurant-menu-item" key={dish.id}>
            {dish.image && <div className="restaurant-menu-photo"><img src={dish.image} alt={dish.imageAlt} loading="lazy" style={{ objectPosition: dish.imagePosition, transformOrigin: dish.imageOrigin, transform: `scale(${dish.imageScale ?? 1})` }} /></div>}
            <div className="restaurant-menu-item-content">
              <h3>{dish.name}</h3>
              <p>{dish.description}</p>
              {!dish.available && <p className="dish-unavailable">Currently unavailable</p>}
              {dish.variations.length === 0 ? <p>Ask us for pricing.</p> : <ul className="restaurant-menu-prices" aria-label={`${dish.name} prices`}>
                {dish.variations.map((variation) => <li key={variation.id}>
                  <span>{variation.name === 'Standard' && dish.variations.length === 1 ? 'Price' : variation.name}{!variation.available && dish.available && ' — unavailable'}</span>
                  <strong>{price(variation.priceMinor, variation.currency)}</strong>
                  <button type="button" disabled={!enabled || !dish.available || !variation.available || (cart.find((line) => line.variationId === variation.id)?.quantity || 0) >= 20 || (cart.length >= 30 && !cart.some((line) => line.variationId === variation.id))} onClick={() => {
                    dispatch({ type: 'add', line: { variationId: variation.id, dishName: dish.name, variationName: variation.name, unitPriceMinor: variation.priceMinor } });
                    setAdded(`${dish.name} — ${variation.name} added to your cart.`);
                  }}>Add to order</button>
                </li>)}
              </ul>}
            </div>
          </article>)}
        </div>
      </>}
      <a className="button button-outline" href="#home">Back to home</a>
    </main>
    <Footer />
  </>;
}
