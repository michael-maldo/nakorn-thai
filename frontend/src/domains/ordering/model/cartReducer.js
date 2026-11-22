import { configurationKey } from './cartModel.js';

export function cartReducer(cart, action) {
  switch (action.type) {
    case 'add': {
      const key = configurationKey(action.line);
      const existing = cart.find((line) => line.key === key);
      const requested = action.quantity ?? 1;
      if (!Number.isInteger(requested) || requested < 1 || requested > remainingCartQuantity(cart, key)) return cart;
      const quantity = (existing?.quantity ?? 0) + requested;
      return existing ? cart.map((line) => line === existing ? { ...action.line, key, quantity } : line)
        : [...cart, { ...action.line, key, quantity }];
    }
    case 'quantity': return cart.map((line) => line.key === action.id ? { ...line, quantity: Math.max(1, Math.min(20, Math.trunc(Number(action.quantity)) || 1)) } : line);
    case 'remove': return cart.filter((line) => line.key !== action.id);
    case 'replace': return action.lines.map((line) => ({ ...line, key: configurationKey(line) }));
    case 'clear': return [];
    default: return cart;
  }
}
export function remainingCartQuantity(cart, key) {
  const existing = cart.find((line) => line.key === key);
  return existing ? 20 - existing.quantity : cart.length < 30 ? 20 : 0;
}

export const cartTotal = (cart) => cart.reduce((sum, line) => sum + line.unitPriceMinor * line.quantity, 0);
export const money = (minor) => new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(minor / 100);
