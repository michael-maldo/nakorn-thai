import { createContext, useContext, useEffect, useReducer } from 'react';
import { cartReducer } from './cartReducer';
const CartContext = createContext(null);
export function CartProvider({ children }) {
  const [cart, dispatch] = useReducer(cartReducer, [], () => {
    try {
      const saved = JSON.parse(sessionStorage.getItem('nakorn-pickup-cart') || '[]');
      return Array.isArray(saved) ? saved.filter((line) => typeof line.variationId === 'string' && typeof line.dishName === 'string' && typeof line.variationName === 'string' && Number.isSafeInteger(line.unitPriceMinor) && line.unitPriceMinor >= 0 && Number.isInteger(line.quantity) && line.quantity >= 1 && line.quantity <= 20).slice(0, 30) : [];
    } catch { return []; }
  });
  useEffect(() => { try { sessionStorage.setItem('nakorn-pickup-cart', JSON.stringify(cart)); } catch { /* Checkout reports storage failures before submitting. */ } }, [cart]);
  return <CartContext.Provider value={{ cart, dispatch }}>{children}</CartContext.Provider>;
}
export const useCart = () => useContext(CartContext);
