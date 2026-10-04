import { useState } from 'react';
import useHomepageMenu from '../../domains/menu/hooks/useHomepageMenu';
import { presentDish } from '../../domains/menu/model/menuModel';
import SectionTitle from './SectionTitle';
import MenuPhoto from '../../domains/menu/components/MenuPhoto';
import { menuPhotoPresentation } from '../../domains/menu/model/menuPhotoLayout';

export default function SignatureDishes() {
  const [openDish, setOpenDish] = useState(null);
  const { collections, loading, error, retry } = useHomepageMenu();
  const sections = collections.map(collection => ({ ...collection, dishes: collection.items.slice(0, 4).map(presentDish) }))
    .filter(collection => collection.dishes.length > 0);
  if (!loading && !error && !sections.length) return null;

  return (
    <section className="signature section" id="menu">
      <div className="page-width">
        <SectionTitle eyebrow="Explore our dishes">From our menu</SectionTitle>
        {loading && <p role="status">Loading dishes…</p>}
        {error && <div role="alert"><p>{error}</p><button type="button" onClick={retry}>Try again</button></div>}
        {sections.map(collection => <div className="homepage-menu-collection" key={collection.id}>
        <h3 className="homepage-menu-collection-title">{collection.name}</h3>
        <div className="dish-grid">
          {collection.dishes.map((dish) => (
            <FeaturedDishCard key={dish.id} dish={dish} open={openDish === `${collection.id}:${dish.id}`}
              onToggle={() => setOpenDish(openDish === `${collection.id}:${dish.id}` ? null : `${collection.id}:${dish.id}`)}
              onClose={() => setOpenDish(null)} />
          ))}
        </div>
        </div>)}
        <a className="button button-outline centered-button" href="#/menu">View full menu</a>
      </div>
    </section>
  );
}

// Keep the card independently renderable while the homepage hook owns discovery.
export function FeaturedDishCard({ dish, open, onToggle, onClose }) {
  return (
    <article className="dish-card">
      {dish.image ? <button
        className="dish-image-trigger"
        type="button"
        aria-expanded={open}
        aria-label={`Preview ${dish.name}`}
        onClick={onToggle}
      >
        <MenuPhoto src={dish.image} alt={dish.imageAlt} className="dish-image" loading="lazy"
          {...menuPhotoPresentation(dish)} />
      </button> : <div className="dish-photo-placeholder">Photo coming soon</div>}
      <div className={`dish-preview${open ? ' is-open' : ''}`}>
        {dish.image && <MenuPhoto src={dish.image} alt="" className="dish-preview-photo"
          {...menuPhotoPresentation(dish)} />}
        <button className="dish-preview-close" type="button" onClick={onClose} aria-label={`Close ${dish.name} preview`}>×</button>
        <div className="dish-preview-actions">
          <span>{dish.name}</span>
          {dish.available ? <a href="#/menu" aria-label={`Order ${dish.name} online`}>
            <i aria-hidden="true" /> Available — Order online
          </a> : <span>Currently unavailable</span>}
        </div>
      </div>
      <h3>{dish.name}</h3>
      <p>{dish.description}</p>
      {!dish.available && <p className="dish-unavailable">Currently unavailable</p>}
      <a href="#/menu">View dish <span aria-hidden="true">→</span></a>
    </article>
  );
}
