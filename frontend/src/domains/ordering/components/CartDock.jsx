import { useEffect, useRef, useState } from 'react';
import { useCart } from '../model/CartContext';
import { cartTotal, money } from '../model/cartReducer';
import { getOrderingOptions } from '../api/orderApi';
import Cart from './Cart';

export default function CartDock() {
  const { cart, notice, dismissNotice } = useCart();
  const dialog = useRef(null);
  const trigger = useRef(null);
  const [enabled, setEnabled] = useState(null);
  const count = cart.reduce((sum, line) => sum + line.quantity, 0);
  useEffect(() => {
    document.body.classList.toggle('has-cart-dock', count > 0);
    if (!count) dialog.current?.close();
    return () => document.body.classList.remove('has-cart-dock');
  }, [count]);
  useEffect(() => {
    const close = () => dialog.current?.close();
    window.addEventListener('hashchange', close);
    return () => window.removeEventListener('hashchange', close);
  }, []);
  async function open() {
    dialog.current?.showModal();
    setEnabled(null);
    try { setEnabled((await getOrderingOptions()).enabled); } catch { setEnabled(false); }
  }
  return <>
    {notice && <aside className="cart-notice" role="status"><p>{notice}</p><a href="#/menu">Choose dishes</a> <a href="#/checkout">Check saved submission</a> <button type="button" onClick={dismissNotice}>Dismiss</button></aside>}
    {count > 0 && <div className="cart-dock"><button ref={trigger} className="button button-primary" onClick={open} aria-haspopup="dialog"><span>View cart · {count} {count === 1 ? 'item' : 'items'}</span><strong>{money(cartTotal(cart))}</strong></button></div>}
    <dialog ref={dialog} className="cart-dialog" aria-labelledby="cart-dialog-title" onClose={() => trigger.current?.focus()} onClick={(event) => { if (event.target === dialog.current) dialog.current.close(); }}>
      <div className="cart-dialog-heading"><h2 id="cart-dialog-title">Your shopping cart</h2><button type="button" autoFocus onClick={() => dialog.current.close()} aria-label="Close shopping cart">Close ×</button></div>
      <p>{enabled === null ? 'Checking ordering availability…' : enabled ? 'Order for pickup. Staff will confirm your order. Choose a payment option after placing it.' : 'Online ordering is closed or unavailable. Your cart is saved.'}</p>
      <Cart checkoutEnabled={enabled === true} />
    </dialog>
  </>;
}
