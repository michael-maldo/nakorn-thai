import { getMenuCollection, getMenuCollections } from '../../menu/api/menuApi.js';
import { collectionAvailability } from '../../menu/model/menuCollections.js';
import { createCartLine, configurationKey, orderLines } from '../model/cartModel.js';
import { getOrder, submitOrder } from './orderApi.js';

export async function refreshCartPrices(cart) {
  const collections = await getMenuCollections();
  const selectedIds = new Set(cart.map((line) => line.collectionId));
  const menus = new Map(await Promise.all(collections.filter((collection) => selectedIds.has(collection.id)).map(async (collection) => {
    try {
      const menu = await getMenuCollection(collection.slug);
      if (menu.id !== collection.id || !Array.isArray(menu.items) || typeof menu.availability?.available !== 'boolean')
        throw new Error('The menu service returned an invalid response.');
      return [collection.id, menu];
    } catch (error) {
      if (error.status === 404) return [collection.id, null];
      throw error;
    }
  })));
  return cart.map((line) => {
    const collection = collections.find((entry) => entry.id === line.collectionId);
    const menu = menus.get(line.collectionId);
    const unavailable = (issue) => ({ ...line, issue });
    if (!collection || !menu) return unavailable('This collection is no longer offered.');
    if (!collection.availability.available || !menu.availability.available)
      return unavailable(collectionAvailability(!menu.availability.available ? menu : collection));
    const dish = menu.items.find((item) => item.variations.some((variation) => variation.id === line.variationId));
    if (!dish) return unavailable('This dish is no longer offered in this collection.');
    const variation = dish.variations.find((entry) => entry.id === line.variationId);
    if (!dish.available || !variation.available) return unavailable(`${dish.name} is currently unavailable.`);
    if (!Array.isArray(dish.optionGroups)) throw new Error('The menu service returned invalid option information.');
    try { return { ...createCartLine(menu, dish, variation, line.selectedOptions), quantity: line.quantity }; }
    catch (error) { return unavailable(error.message); }
  });
}

function reviewError(message, lines) {
  const error = new Error(message);
  error.lines = lines;
  return error;
}

function displayedTerms(line) {
  return JSON.stringify([line.collectionName, line.dishName, line.variationName, line.basePriceMinor, line.unitPriceMinor,
    line.selectedOptions.map(({ optionId, quantity, optionName, optionGroupName, priceDeltaMinor }) =>
      [optionId, quantity, optionName, optionGroupName, priceDeltaMinor])]);
}

export async function prepareCheckout(cart) {
  orderLines(cart); // Reject incompatible legacy configuration before making requests.
  const lines = await refreshCartPrices(cart);
  if (lines.some((line) => line.issue)) throw reviewError('Some cart items are unavailable or need different options. Review the marked items before ordering.', lines);
  if (lines.some((line, index) => displayedTerms(line) !== displayedTerms(cart[index])))
    throw reviewError('Prices or dish details changed. Review your updated cart and submit again.', lines);
  return { lines, items: orderLines(lines) };
}

// A pending attempt may already be committed. Never rewrite its payload or key.
export async function resumeOrder(payload, cart = []) {
  try { return await getOrder(payload); }
  catch (error) { if (error.status !== 404) throw error; }
  let pendingLines;
  try {
    pendingLines = payload.items.map((line) => {
      const key = configurationKey(line);
      const previous = cart.find((entry) => entry.key === key);
      return { ...previous, ...line, key, issue: '', unitPriceMinor: line.expectedUnitPriceMinor,
        dishName: previous?.dishName ?? 'Saved dish', selectedOptions: previous?.selectedOptions ?? line.selectedOptions ?? [] };
    });
    orderLines(pendingLines);
  } catch {
    const error = new Error('This saved submission uses the previous menu. No order was found. Please choose your dishes and options again.');
    error.clearPending = true;
    throw error;
  }
  const refreshed = await refreshCartPrices(pendingLines);
  if (refreshed.some((line, index) => line.issue || line.unitPriceMinor !== pendingLines[index].unitPriceMinor)) {
    // For rejected offers, retain valid display data if the original cart still exists.
    const error = reviewError('The saved order was not placed, and its menu offer changed. Review your cart before submitting a new order.', refreshed);
    error.clearPending = true;
    throw error;
  }
  return submitOrder(payload);
}

export async function recoverCheckoutFailure(failure, payload, cart) {
  if (!payload || ![400, 409, 503].includes(failure.status)) return { message: failure.message, clearPending: false };
  try { return { order: await getOrder(payload) }; }
  catch (lookup) {
    if (lookup.status !== 404) return { clearPending: false,
      message: `${failure.message} We could not confirm whether the order was received. Keep the saved submission and retry.` };
  }
  let lines;
  let refreshMessage = '';
  try { if (cart.length) lines = await refreshCartPrices(cart); }
  catch { refreshMessage = ' The menu could not be refreshed; try again before ordering.'; }
  return { clearPending: true, lines, message: `${failure.message} ${failure.status === 400
    ? 'Check your details and option selections before submitting again.'
    : 'Review the current cart and availability before submitting again.'}${refreshMessage}` };
}
