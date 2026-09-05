import { useState } from 'react';
import useMenu from '../../domains/menu/hooks/useMenu';
import { presentDish } from '../../domains/menu/model/menuModel';
import SectionTitle from './SectionTitle';

export default function SignatureDishes() {
  const [openDish, setOpenDish] = useState(null);
  const { items, loading, error, retry } = useMenu();
  const dishes = items.map(presentDish);

  return (
    <section className="signature section" id="menu">
      <div className="page-width">
        <SectionTitle eyebrow="Chef’s recommendations">Signature Dishes</SectionTitle>
        {loading && <p role="status">Loading signature dishes…</p>}
        {error && <div role="alert"><p>{error}</p><button type="button" onClick={retry}>Try again</button></div>}
        {!loading && !error && dishes.length === 0 && <p>Our signature menu is being updated. Please check back soon.</p>}
        <div className="dish-grid">
          {dishes.map((dish) => (
            <article className="dish-card" key={dish.id}>
              {dish.image ? <button
                className="dish-image-trigger"
                type="button"
                aria-expanded={openDish === dish.id}
                aria-label={`Preview ${dish.name}`}
                onClick={() => setOpenDish(openDish === dish.id ? null : dish.id)}
              >
                <span className="dish-image">
                  <img
                    src={dish.image}
                    alt={dish.imageAlt}
                    style={{
                      objectPosition: dish.imagePosition,
                      transform: `scale(${dish.imageScale ?? 1}) rotate(${dish.imageRotation ?? '0deg'})`,
                    }}
                  />
                </span>
              </button> : <div className="dish-photo-placeholder">Photo coming soon</div>}
              <div className={`dish-preview${openDish === dish.id ? ' is-open' : ''}`}>
                {dish.image && <img
                  src={dish.image}
                  alt=""
                  style={{ objectPosition: dish.imagePosition }}
                />}
                <button className="dish-preview-close" type="button" onClick={() => setOpenDish(null)} aria-label={`Close ${dish.name} preview`}>×</button>
                <div className="dish-preview-actions">
                  <span>{dish.name}</span>
                  {dish.available ? <a href="#order-online" aria-label={`Order ${dish.name} online`}>
                    <i aria-hidden="true" /> Available — Order online
                  </a> : <span>Currently unavailable</span>}
                </div>
              </div>
              <h3>{dish.name}</h3>
              <p>{dish.description}</p>
              {!dish.available && <p className="dish-unavailable">Currently unavailable</p>}
              <a href="#menu">View dish <span aria-hidden="true">→</span></a>
            </article>
          ))}
        </div>
        <a className="button button-outline centered-button" href="#menu">View full menu</a>
      </div>
    </section>
  );
}
