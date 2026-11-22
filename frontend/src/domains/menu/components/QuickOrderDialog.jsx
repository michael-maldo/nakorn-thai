import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { getMenuCollection } from '../api/menuApi';
import { collectionAvailability } from '../model/menuCollections';
import { presentDish } from '../model/menuModel';
import { menuPhotoPresentation } from '../model/menuPhotoLayout';
import { getOrderingOptions } from '../../ordering/api/orderApi';
import { useCart } from '../../ordering/model/CartContext';
import { observeOptions } from '../../restaurant/model/observeOptions';
import MenuItemOrder from './MenuItemOrder';
import MenuPhoto from './MenuPhoto';

export default function QuickOrderDialog({ featured, onClose, onAdded }) {
  const dialog = useRef(null);
  const titleId = useId();
  const { cart, dispatch } = useCart();
  const [current, setCurrent] = useState(null);
  const [error, setError] = useState('');

  useLayoutEffect(() => {
    const element = dialog.current;
    element.showModal();
    return () => element.close();
  }, []);

  useEffect(() => observeOptions(
    (signal) => loadDish(featured, signal),
    (result) => { setCurrent(result); setError(''); },
    () => { setCurrent(null); setError('Ordering availability could not be checked. Please try again shortly.'); },
  ), [featured]);

  async function verify(signal) {
    const latest = await loadDish(featured, AbortSignal.any([signal, AbortSignal.timeout(5000)]));
    if (!dialog.current?.open || signal.aborted) throw new Error('Quick order was closed.');
    setCurrent(latest);
    return latest;
  }

  const collection = current?.collection ?? featured.collection;
  const item = current?.item ?? featured.item;
  const dish = presentDish(item);
  const message = error || (!current ? 'Checking ordering availability…'
    : !current.enabled ? current.message
      : !collection.availability.available ? collectionAvailability(collection)
        : !item.available || !item.variations.some((variation) => variation.available) ? 'Currently unavailable'
          : 'Available for pickup ordering');

  return <dialog ref={dialog} className="quick-order-dialog" aria-labelledby={titleId} onClose={() => {
    // StrictMode can close and immediately reopen the same native dialog.
    if (!dialog.current?.open) onClose();
  }}>
    <button className="quick-order-close" type="button" autoFocus aria-label="Close quick order" onClick={() => dialog.current.close()}>×</button>
    <div className="quick-order-scroll">
      <div className="quick-order-layout">
        {dish.image && <MenuPhoto src={dish.image} alt={dish.imageAlt} className="quick-order-photo"
          {...menuPhotoPresentation(dish)} />}
        <div className="quick-order-details">
          <h2 id={titleId}>{dish.name}</h2>
          <p>{dish.description}</p>
          <p role="status">{message}</p>
          <MenuItemOrder {...{ item, collection, cart, dispatch, verify }} enabled={current?.enabled === true}
            onAdded={(line) => {
              onAdded(line);
              dialog.current.close();
            }} />
        </div>
      </div>
    </div>
  </dialog>;
}

async function loadDish(featured, signal) {
  const [collection, options] = await Promise.all([
    getMenuCollection(featured.collection.slug, signal).catch((error) => {
      if (error.status !== 404) throw error;
      return null;
    }),
    getOrderingOptions(signal),
  ]);
  if (collection && (collection.id !== featured.collection.id || !Array.isArray(collection.items)
    || typeof collection.availability?.available !== 'boolean')) throw new Error('The current menu could not be verified.');
  const item = collection?.items.find((entry) => entry.id === featured.item.id);
  return {
    collection: collection ?? { ...featured.collection, availability: { available: false } },
    item: item ?? { ...featured.item, available: false },
    enabled: options.enabled === true,
    message: options.message || 'Online ordering is currently closed or unavailable.',
  };
}
