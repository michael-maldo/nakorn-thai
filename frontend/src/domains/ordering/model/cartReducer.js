export function cartReducer(cart, action) {
  switch (action.type) {
    case 'add': {
      const existing = cart.find((line) => line.variationId === action.line.variationId);
      if (!existing && cart.length >= 30) return cart;
      return existing ? cart.map((line) => line === existing ? { ...line, quantity: Math.min(20, line.quantity + 1) } : line) : [...cart, { ...action.line, quantity: 1 }];
    }
    case 'quantity': return cart.map((line) => line.variationId === action.id ? { ...line, quantity: Math.max(1, Math.min(20, Math.trunc(Number(action.quantity)) || 1)) } : line);
    case 'remove': return cart.filter((line) => line.variationId !== action.id);
    case 'replace': return action.lines;
    case 'clear': return [];
    default: return cart;
  }
}
export const cartTotal = (cart) => cart.reduce((sum, line) => sum + line.unitPriceMinor * line.quantity, 0);
export const money = (minor) => new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(minor / 100);
