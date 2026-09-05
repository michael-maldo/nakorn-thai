import { useState } from 'react';
import { dishes } from '../content/homeContent';
import SectionTitle from './SectionTitle';

export default function SignatureDishes() {
  const [openDish, setOpenDish] = useState(null);

  return (
    <section className="signature section" id="menu">
      <div className="page-width">
        <SectionTitle eyebrow="Chef’s recommendations">Signature Dishes</SectionTitle>
        <div className="dish-grid">
          {dishes.map((dish) => (
            <article className="dish-card" key={dish.name}>
              <button
                className="dish-image-trigger"
                type="button"
                aria-expanded={openDish === dish.name}
                aria-label={`Preview ${dish.name}`}
                onClick={() => setOpenDish(openDish === dish.name ? null : dish.name)}
              >
                <span className="dish-image">
                  <img
                    src={dish.image}
                    alt={dish.name}
                    style={{
                      objectPosition: dish.imagePosition,
                      transform: `scale(${dish.imageScale ?? 1}) rotate(${dish.imageRotation ?? '0deg'})`,
                    }}
                  />
                </span>
              </button>
              <div className={`dish-preview${openDish === dish.name ? ' is-open' : ''}`}>
                <img
                  src={dish.image}
                  alt=""
                  style={{ objectPosition: dish.imagePosition }}
                />
                <button className="dish-preview-close" type="button" onClick={() => setOpenDish(null)} aria-label={`Close ${dish.name} preview`}>×</button>
                <div className="dish-preview-actions">
                  <span>{dish.name}</span>
                  <a href="#order-online" aria-label={`Order ${dish.name} online`}>
                    <i aria-hidden="true" /> Available — Order online
                  </a>
                </div>
              </div>
              <h3>{dish.name}</h3>
              <p>{dish.description}</p>
              <a href="#menu">View dish <span aria-hidden="true">→</span></a>
            </article>
          ))}
        </div>
        <a className="button button-outline centered-button" href="#menu">View full menu</a>
      </div>
    </section>
  );
}
