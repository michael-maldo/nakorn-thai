import { useCart } from '../model/CartContext';
import { cartTotal, money } from '../model/cartReducer';
export default function Cart() {
  const { cart, dispatch } = useCart();
  return <section className="order-panel" aria-label="Your pickup order">
    <h2>Your pickup order</h2>
    {!cart.length ? <p>Your cart is empty. Add a dish to get started.</p> : <>
      {cart.map((line) => <div className="cart-line" key={line.variationId}>
        <div><strong>{line.dishName}</strong><p>{line.variationName}</p></div>
        <label>Quantity for {line.dishName}<input type="number" min="1" max="20" value={line.quantity} onChange={(event) => dispatch({ type: 'quantity', id: line.variationId, quantity: event.target.value })} /></label>
        <strong>{money(line.unitPriceMinor * line.quantity)}</strong>
        <button type="button" onClick={() => dispatch({ type: 'remove', id: line.variationId })}>Remove</button>
      </div>)}
      <p className="order-total">Total: {money(cartTotal(cart))}</p>
      <a className="button button-primary" href="#/checkout">Checkout for pickup</a>
    </>}
  </section>;
}
