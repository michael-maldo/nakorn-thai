import { configurationKey } from './cartModel.js';

export function cartReducer(cart, action) {
  switch (action.type) {
    case 'add': {
      const key = configurationKey(action.line);
      const existing = cart.find((line) => line.key === key);
      if (!existing && cart.length >= 30) return cart;
      return existing ? cart.map((line) => line === existing
        ? { ...action.line, key, quantity: Math.min(20, line.quantity + 1) } : line)
        : [...cart, { ...action.line, key, quantity: 1 }];
    }
    case 'quantity': return cart.map((line) => line.key === action.id ? { ...line, quantity: Math.max(1, Math.min(20, Math.trunc(Number(action.quantity)) || 1)) } : line);
    case 'remove': return cart.filter((line) => line.key !== action.id);
    case 'replace': return action.lines.map((line) => ({ ...line, key: configurationKey(line) }));
    case 'clear': return [];
    default: return cart;
  }
}
export const cartTotal = (cart) => cart.reduce((sum, line) => sum + line.unitPriceMinor * line.quantity, 0);
export const money = (minor) => new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(minor / 100);
