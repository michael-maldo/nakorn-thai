import { useState } from 'react';
import Header from '../../../website/components/Header';
import Footer from '../../../website/components/Footer';
import useMenu from '../hooks/useMenu';
import { presentDish } from '../model/menuModel';

const price = (minor, currency) => new Intl.NumberFormat('en-AU', { style: 'currency', currency }).format(minor / 100);

export default function MenuPage() {
  const { items, loading, error, retry } = useMenu('chefs-special-recommendations');
  const [search, setSearch] = useState('');
  const dishes = items.filter((item) => `${item.name} ${item.description}`.toLowerCase().includes(search.trim().toLowerCase())).map(presentDish);
  return <>
    <Header currentPage="Menu" />
    <main className="restaurant-menu page-width">
      <div className="restaurant-menu-heading">
        <p className="restaurant-menu-eyebrow">Nakorn Thai Restaurant &amp; Bar</p>
        <h1>Our menu</h1>
        <h2>Chef’s Special Recommendations</h2>
        <p>Explore our entrées and main dishes. All prices are in Australian dollars.</p>
      </div>
      {loading && <p role="status">Loading our menu…</p>}
      {error && <div role="alert"><p>{error}</p><button className="button button-outline" onClick={retry}>Try again</button></div>}
      {!loading && !error && <>
        {items.length > 0 && <label className="restaurant-menu-search">Find a dish
          <input type="search" placeholder="Search dishes or descriptions" value={search} onChange={(event) => setSearch(event.target.value)} />
        </label>}
        <p role="status">{items.length === 0 ? 'Our chef’s menu is being updated. Please check back soon.' : `${dishes.length} ${dishes.length === 1 ? 'dish' : 'dishes'}${search ? ' found' : ' on the menu'}`}</p>
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
