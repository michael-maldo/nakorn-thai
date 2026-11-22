import { useEffect, useRef, useState } from 'react';
import { evaluateOptions } from '../model/menuOptions';
import MenuItemOptions from './MenuItemOptions';
import { createCartLine } from '../../ordering/model/cartModel';
import { remainingCartQuantity, money } from '../../ordering/model/cartReducer';

function configuredDish(item, collection, enabled, variationId, selections) {
  const variation = item.variations.find((entry) => entry.id === variationId);
  const evaluation = evaluateOptions(item.optionGroups, selections);
  const orderable = enabled && collection.availability?.available === true && item.available && variation?.available;
  let line = null;
  let problem = evaluation.message;
  if (variation && evaluation.valid) {
    try { line = createCartLine(collection, item, variation, selections); }
    catch (error) { problem = error.message; }
  }
  return { variation, evaluation, orderable, line, problem };
}

export default function MenuItemOrder({ item, collection, enabled, cart, dispatch, onAdded, verify }) {
  const [selectedVariation, setVariationId] = useState('');
  const variationId = item.variations.find((entry) => entry.id === selectedVariation)?.id
    ?? item.variations.find((entry) => entry.defaultVariation)?.id ?? item.variations[0]?.id ?? '';
  const [selections, setSelections] = useState([]);
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(null);
  useEffect(() => () => pending.current?.abort(), []);
  const { variation, evaluation, orderable, line, problem } = configuredDish(item, collection, enabled, variationId, selections);
  const remaining = line ? remainingCartQuantity(cart, line.key) : 0;
  const limit = !line || quantity <= remaining ? ''
    : remaining ? `You can add up to ${remaining} more of this configuration.`
      : cart.some((entry) => entry.key === line.key) ? 'This configuration has reached 20 dishes in your cart.'
        : 'Your cart has reached 30 different configurations.';
  const canAdd = orderable && line && !limit;

  async function add() {
    if (!canAdd || pending.current) return;
    setError('');
    let confirmedLine = line;
    if (verify) {
      const controller = new AbortController();
      pending.current = controller;
      setBusy(true);
      try {
        const fresh = await verify(controller.signal);
        if (controller.signal.aborted) return;
        const choice = configuredDish(fresh.item, fresh.collection, fresh.enabled, variationId, selections);
        if (!choice.orderable) throw new Error('This dish is no longer available to order.');
        if (!choice.line || choice.line.basePriceMinor !== line.basePriceMinor
          || choice.line.selectedOptions.some((option, index) => option.priceDeltaMinor !== line.selectedOptions[index]?.priceDeltaMinor)) {
          throw new Error('The price or options have changed. Review your choices and try again.');
        }
        confirmedLine = choice.line;
      } catch (failure) {
        if (!controller.signal.aborted) setError(failure.message);
        return;
      } finally {
        pending.current = null;
        if (!controller.signal.aborted) setBusy(false);
      }
    }
    dispatch({ type: 'add', line: confirmedLine, quantity });
    onAdded?.(confirmedLine);
  }

  if (!variation) return <p>Ask us for pricing.</p>;
  return <>
    {item.variations.length > 1 && <label className="menu-variation">Variation
      <select disabled={busy} value={variationId} onChange={(event) => setVariationId(event.target.value)}>
        {item.variations.map((entry) => <option key={entry.id} value={entry.id}>
          {entry.name} — {money(entry.priceMinor)}{collection.availability?.available && !entry.available && ' — unavailable to order'}
        </option>)}
      </select>
    </label>}
    <MenuItemOptions groups={item.optionGroups} selections={selections} onChange={setSelections} disabled={busy} />
    <p className="menu-unit-price" aria-live="polite" aria-atomic="true"><strong>{money(line?.unitPriceMinor ?? variation.priceMinor + evaluation.deltaMinor)}</strong>{verify && ' per dish'}</p>
    {verify && collection.availability?.available && item.available && !variation.available && <p>Selected variation is unavailable.</p>}
    {problem && <p className="menu-choice-message">{problem}</p>}
    {verify && !evaluation.valid && selections.length > 0 && <button className="button button-outline" type="button"
      disabled={busy} onClick={() => setSelections([])}>Clear options</button>}
    <div className={verify ? 'quick-order-action' : undefined}>
      {verify && <div className="quick-order-total">
        <label>Quantity <select value={quantity} disabled={busy} onChange={(event) => setQuantity(Number(event.target.value))}>
          {Array.from({ length: Math.max(remaining, quantity) }, (_, index) => index + 1).map((value) => (
            <option key={value} value={value} disabled={value > remaining && value !== quantity}>{value}</option>
          ))}
        </select></label>
        <strong aria-live="polite">Subtotal {line ? money(line.unitPriceMinor * quantity) : '—'}</strong>
      </div>}
      {limit && <p>{limit}</p>}
      {error && <p role="alert">{error}</p>}
      <button className="button button-primary" type="button" disabled={!canAdd || busy} onClick={add}>{busy ? 'Checking availability…' : 'Add to order'}</button>
    </div>
  </>;
}
