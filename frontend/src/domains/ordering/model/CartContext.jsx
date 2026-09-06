import { createContext, useContext, useEffect, useReducer, useState } from 'react';
import { cartReducer } from './cartReducer';
import { CART_STORAGE_KEY, restoreCart, serializeCart } from './cartModel';
const CartContext = createContext(null);
export function CartProvider({ children }) {
  const [restored] = useState(() => {
    try { return restoreCart(sessionStorage.getItem(CART_STORAGE_KEY)); }
    catch { return { lines: [], notice: 'Your saved cart could not be read. Please choose your dishes again.' }; }
  });
  const [notice, setNotice] = useState(restored.notice);
  const [cart, dispatch] = useReducer(cartReducer, restored.lines);
  useEffect(() => {
    try { sessionStorage.setItem(CART_STORAGE_KEY, serializeCart(cart)); }
    catch { setNotice('Your cart could not be saved in this browser tab. Keep this page open while ordering.'); }
  }, [cart]);
  return <CartContext.Provider value={{ cart, dispatch, notice, dismissNotice: () => setNotice('') }}>{children}</CartContext.Provider>;
}
export const useCart = () => useContext(CartContext);
