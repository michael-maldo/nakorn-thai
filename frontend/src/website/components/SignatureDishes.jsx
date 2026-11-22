import { useRef, useState } from 'react';
import useHomepageMenu from '../../domains/menu/hooks/useHomepageMenu';
import { presentDish } from '../../domains/menu/model/menuModel';
import SectionTitle from './SectionTitle';
import MenuPhoto from '../../domains/menu/components/MenuPhoto';
import QuickOrderDialog from '../../domains/menu/components/QuickOrderDialog';
import { menuPhotoPresentation } from '../../domains/menu/model/menuPhotoLayout';

export default function SignatureDishes() {
  const [featured, setFeatured] = useState(null);
  const [added, setAdded] = useState(null);
  const trigger = useRef(null);
  const { collections, loading, error, retry } = useHomepageMenu();
  const sections = collections.filter((collection) => collection.items.length);
  if (!loading && !error && !sections.length) return null;

  return (
    <section className="signature section" id="menu">
      <div className="page-width">
        <SectionTitle eyebrow="Explore our dishes">From our menu</SectionTitle>
        {loading && <p role="status">Loading dishes…</p>}
        {error && <div role="alert"><p>{error}</p><button type="button" onClick={retry}>Try again</button></div>}
        <p className="sr-only" role="status" aria-atomic="true">{added?.message}</p>
        {sections.map(collection => <div className="homepage-menu-collection" key={collection.id}>
          <h3 className="homepage-menu-collection-title">{collection.name}</h3>
          <div className="dish-grid">
            {collection.items.slice(0, 4).map((item) => (
              <FeaturedDishCard key={item.id} dish={presentDish(item)}
                available={item.available && collection.availability?.available}
                addedMessage={added?.collectionId === collection.id && added?.dishId === item.id ? added.message : ''}
                onOpen={(event) => {
                  trigger.current = event.currentTarget;
                  setAdded(null);
                  setFeatured({ collection, item });
                }} />
            ))}
          </div>
        </div>)}
        <a className="button button-outline centered-button" href="#/menu">View full menu</a>
      </div>
      {featured && <QuickOrderDialog featured={featured}
        onAdded={(line) => setAdded({ collectionId: line.collectionId, dishId: line.dishId, message: `${line.dishName} added to your cart.` })}
        onClose={() => {
          setFeatured(null);
          trigger.current?.focus();
        }} />}
    </section>
  );
}

export function FeaturedDishCard({ dish, available, addedMessage, onOpen }) {
  const action = available ? 'Quick order' : 'View details';
  return (
    <article className="dish-card">
      {dish.image ? <button className="dish-image-trigger" type="button"
        aria-haspopup="dialog" aria-label={`${action}: ${dish.name}`} onClick={onOpen}>
        <MenuPhoto src={dish.image} alt={dish.imageAlt} className="dish-image" loading="lazy"
          {...menuPhotoPresentation(dish)} />
        <span className="dish-image-action" aria-hidden="true">{action}</span>
      </button> : <div className="dish-photo-placeholder">Photo coming soon</div>}
      <h3>{dish.name}</h3>
      <p>{dish.description}</p>
      {!available && <p className="dish-unavailable">Currently unavailable</p>}
      <button className="button button-outline dish-order-button" type="button" aria-haspopup="dialog"
        aria-label={`${action}: ${dish.name}`} onClick={onOpen}>{action}</button>
      {addedMessage && <p className="featured-order-status" aria-hidden="true">{addedMessage}</p>}
    </article>
  );
}
