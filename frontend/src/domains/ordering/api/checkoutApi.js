import { getMenuCollection } from '../../menu/api/menuApi.js';
import { menuCollections } from '../../menu/model/menuCollections.js';

export async function refreshCartPrices(cart) {
  const menus = await Promise.all(Object.keys(menuCollections).map(async (slug) => {
    try { return await getMenuCollection(slug); }
    catch (error) {
      // Archived or expired collections are no longer part of the visible catalog.
      if (error.status === 404) return { items: [] };
      throw error;
    }
  }));
  const current = new Map();
  for (const menu of menus) {
    if (!Array.isArray(menu.items)) throw new Error('The menu service returned an invalid response.');
    for (const dish of menu.items) {
      for (const variation of dish.variations) {
        current.set(variation.id, { dish, variation });
      }
    }
  }
  return cart.map((line) => {
    const match = current.get(line.variationId);
    if (!match || !match.dish.available || !match.variation.available) {
      throw new Error(`${line.dishName} is no longer available. Remove it from your cart before ordering.`);
    }
    return { ...line, unitPriceMinor: match.variation.priceMinor };
  });
}
