import { evaluateOptions, normalizeSelectedOptions } from '../../menu/model/menuOptions.js';

export const CART_STORAGE_KEY = 'nakorn-pickup-cart';
export const CART_VERSION = 2;
export const LEGACY_CART_MESSAGE = 'The menu has changed. Some saved cart items could not be restored. Please choose those dishes and options again. Any saved order submission is still available at checkout.';
const validMinor = (amount) => Number.isSafeInteger(amount) && amount >= 0;

export function configurationKey(line) {
  if (typeof line.collectionId !== 'string' || !line.collectionId || typeof line.variationId !== 'string' || !line.variationId)
    throw new Error('Choose this dish from the current menu again.');
  return JSON.stringify([line.collectionId.toLowerCase(), line.variationId.toLowerCase(),
    normalizeSelectedOptions(line.selectedOptions).map(({ optionId, quantity }) => [optionId, quantity])]);
}

export function createCartLine(collection, dish, variation, selections) {
  const options = evaluateOptions(dish.optionGroups, selections);
  if (!options.valid) throw new Error(options.message);
  const unitPriceMinor = variation.priceMinor + options.deltaMinor;
  if (!validMinor(variation.priceMinor) || !validMinor(unitPriceMinor)) throw new Error('This dish cannot be priced. Please reload the menu.');
  const line = { collectionId: collection.id, collectionSlug: collection.slug, collectionName: collection.name,
    dishId: dish.id, dishName: dish.name, variationId: variation.id, variationName: variation.name,
    basePriceMinor: variation.priceMinor, selectedOptions: options.selectedOptions, unitPriceMinor, quantity: 1, issue: '' };
  return { ...line, key: configurationKey(line) };
}

export function orderLines(cart) {
  if (!cart.length || cart.length > 30) throw new Error('Choose between 1 and 30 configured dishes.');
  const seen = new Set();
  let totalMinor = 0;
  return cart.map((line) => {
    const key = configurationKey(line);
    if (seen.has(key) || line.issue || !validMinor(line.unitPriceMinor) || !Number.isInteger(line.quantity)
      || line.quantity < 1 || line.quantity > 20) throw new Error(line.issue || 'Review your cart quantities and options.');
    totalMinor += line.unitPriceMinor * line.quantity;
    if (!Number.isSafeInteger(totalMinor)) throw new Error('This cart total cannot be displayed accurately. Please reduce your order.');
    seen.add(key);
    return { collectionId: line.collectionId, variationId: line.variationId, quantity: line.quantity,
      expectedUnitPriceMinor: line.unitPriceMinor, selectedOptions: normalizeSelectedOptions(line.selectedOptions) };
  });
}

export function restoreCart(raw) {
  if (!raw) return { lines: [], notice: '' };
  try {
    const saved = JSON.parse(raw);
    // A known configuration can be retained; collection identity is never inferred.
    const candidates = Array.isArray(saved) ? saved : saved?.version === CART_VERSION ? saved.lines : null;
    if (!Array.isArray(candidates)) throw new Error('Unknown cart format');
    const lines = [];
    let discarded = false;
    for (const line of candidates) {
      try {
        const key = configurationKey(line);
        if (![line.dishName, line.variationName, line.collectionName, line.collectionSlug].every((value) => typeof value === 'string')
          || !validMinor(line.basePriceMinor) || !validMinor(line.unitPriceMinor)
          || !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 20
          || !Array.isArray(line.selectedOptions)
          || line.selectedOptions.some((option) => !validMinor(option.priceDeltaMinor)
            || typeof option.optionName !== 'string' || typeof option.optionGroupName !== 'string')
          || line.basePriceMinor + line.selectedOptions.reduce((sum, option) => sum + option.priceDeltaMinor * option.quantity, 0) !== line.unitPriceMinor)
          throw new Error('Invalid cart line');
        const existing = lines.find((entry) => entry.key === key);
        if (existing) {
          const quantity = existing.quantity + line.quantity;
          if (quantity > 20) discarded = true;
          existing.quantity = Math.min(20, quantity);
        } else if (lines.length < 30) lines.push({ ...line, key, issue: typeof line.issue === 'string' ? line.issue : '' });
        else discarded = true;
      } catch { discarded = true; }
    }
    return { lines, notice: discarded ? LEGACY_CART_MESSAGE : '' };
  } catch { return { lines: [], notice: LEGACY_CART_MESSAGE }; }
}

export function serializeCart(lines) { return JSON.stringify({ version: CART_VERSION, lines }); }
