import { useState } from 'react';
import useHomepageMenu from '../../domains/menu/hooks/useHomepageMenu';
import { presentDish } from '../../domains/menu/model/menuModel';
import SectionTitle from './SectionTitle';

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
            <article className="dish-card" key={dish.id}>
              {dish.image ? <button
                className="dish-image-trigger"
                type="button"
                aria-expanded={openDish === `${collection.id}:${dish.id}`}
                aria-label={`Preview ${dish.name}`}
                onClick={() => setOpenDish(openDish === `${collection.id}:${dish.id}` ? null : `${collection.id}:${dish.id}`)}
              >
                <span className="dish-image">
                  <img
                    src={dish.image}
                    alt={dish.imageAlt}
                    style={{
                      objectPosition: dish.imagePosition,
                      transformOrigin: dish.imageOrigin,
                      transform: `scale(${dish.imageScale ?? 1}) rotate(${dish.imageRotation ?? '0deg'})`,
                    }}
                  />
                </span>
              </button> : <div className="dish-photo-placeholder">Photo coming soon</div>}
              <div className={`dish-preview${openDish === `${collection.id}:${dish.id}` ? ' is-open' : ''}`}>
                {dish.image && <img
                  src={dish.image}
                  alt=""
                  style={{ objectPosition: dish.imagePosition, transformOrigin: dish.imageOrigin, ...(dish.imageOrigin ? { transform: `scale(${dish.imageScale})` } : {}) }}
                />}
                <button className="dish-preview-close" type="button" onClick={() => setOpenDish(null)} aria-label={`Close ${dish.name} preview`}>×</button>
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
          ))}
        </div>
        </div>)}
        <a className="button button-outline centered-button" href="#/menu">View full menu</a>
      </div>
    </section>
  );
}
