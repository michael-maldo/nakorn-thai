import { useState } from 'react';
import { presentDish } from '../model/menuModel';
import { evaluateOptions } from '../model/menuOptions';
import MenuItemOptions from './MenuItemOptions';
import { createCartLine } from '../../ordering/model/cartModel';
import { money } from '../../ordering/model/cartReducer';

export default function MenuItemCard({ item, collection, enabled, cart, onAdd }) {
  const dish = presentDish(item);
  const [variationId, setVariationId] = useState(() => item.variations.find((entry) => entry.defaultVariation)?.id ?? item.variations[0]?.id ?? '');
  const [selections, setSelections] = useState([]);
  const variation = item.variations.find((entry) => entry.id === variationId);
  const evaluation = evaluateOptions(item.optionGroups, selections);
  const orderable = enabled && collection.availability.available && item.available && variation?.available;
  let line = null;
  let problem = evaluation.message;
  if (variation && evaluation.valid) {
    try { line = createCartLine(collection, item, variation, selections); }
    catch (error) { problem = error.message; }
  }
  const existing = line && cart.find((entry) => entry.key === line.key);
  const limit = existing?.quantity >= 20 ? 'This configuration has reached 20 dishes in your cart.'
    : !existing && cart.length >= 30 ? 'Your cart has reached 30 different configurations.' : '';
  return <article className="restaurant-menu-item">
    {dish.image && <div className="restaurant-menu-photo"><img src={dish.image} alt={dish.imageAlt} loading="lazy"
      style={{ objectPosition: dish.imagePosition, transformOrigin: dish.imageOrigin, transform: `scale(${dish.imageScale ?? 1})` }} /></div>}
    <div className="restaurant-menu-item-content">
      <h3>{dish.name}</h3><p>{dish.description}</p>
      {collection.availability.available && !item.available && <p className="dish-unavailable">Currently unavailable</p>}
      {!variation ? <p>Ask us for pricing.</p> : <>
        {item.variations.length > 1 && <label className="menu-variation">Variation
          <select value={variationId} onChange={(event) => setVariationId(event.target.value)}>
            {item.variations.map((entry) => <option key={entry.id} value={entry.id}>
              {entry.name} — {money(entry.priceMinor)}{collection.availability.available && !entry.available && ' — unavailable to order'}
            </option>)}
          </select>
        </label>}
        <MenuItemOptions groups={item.optionGroups} selections={selections} onChange={setSelections} />
        <p className="menu-unit-price" aria-live="polite" aria-atomic="true"><strong>{money(line?.unitPriceMinor ?? variation.priceMinor + evaluation.deltaMinor)}</strong></p>
        {problem && <p className="menu-choice-message">{problem}</p>}
        {limit && <p>{limit}</p>}
        <button className="button button-primary" type="button" disabled={!orderable || !line || !!limit} onClick={() => onAdd(line)}>Add to order</button>
      </>}
    </div>
  </article>;
}
