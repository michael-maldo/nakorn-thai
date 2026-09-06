import { useEffect, useState } from 'react';
import { useCart } from '../model/CartContext';
import { cartTotal, money } from '../model/cartReducer';

function QuantityInput({ quantity, onCommit }) {
  const [value, setValue] = useState(String(quantity));

  useEffect(() => {
    setValue(String(quantity));
  }, [quantity]);

  function commit(nextValue = value) {
    const parsed = Math.trunc(Number(nextValue));

    const finalValue = Number.isFinite(parsed)
      ? Math.max(1, Math.min(20, parsed))
      : 1;

    setValue(String(finalValue));
    onCommit(finalValue);
  }

  function decrement() {
    const current = Math.max(1, Number(value) || 1);
    commit(current - 1);
  }

  function increment() {
    const current = Math.max(1, Number(value) || 1);
    commit(current + 1);
  }

  return (
    <div className="quantity-control">
      <button
        type="button"
        className="quantity-button"
        onClick={decrement}
        aria-label="Decrease quantity"
      >
        −
      </button>

      <input
        className="quantity-input"
        type="number"
        min="1"
        max="20"
        inputMode="numeric"
        value={value}
        onChange={(event) => {
          const next = event.target.value;

          if (next === '' || /^\d+$/.test(next)) {
            setValue(next);
          }
        }}
        onBlur={() => commit()}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.currentTarget.blur();
          }
        }}
      />

      <button
        type="button"
        className="quantity-button"
        onClick={increment}
        aria-label="Increase quantity"
      >
        +
      </button>
    </div>
  );
}



export default function Cart({ checkoutEnabled = true }) {
  const { cart, dispatch } = useCart();
  return <section className="order-panel" aria-label="Your pickup order">
    <h2>Your pickup order</h2>
    {!cart.length ? <p>Your cart is empty. Add a dish to get started.</p> : <>
      {cart.map((line) => <div className="cart-line" key={line.variationId}>
        <div><strong>{line.dishName}</strong><p>{line.variationName}</p></div>
        <label>Quantity for {line.dishName}
            <QuantityInput
                quantity={line.quantity}
                onCommit={(quantity) =>
                  dispatch({
                    type: 'quantity',
                    id: line.variationId,
                    quantity
                  })
                }
  />
        </label>
        <strong>{money(line.unitPriceMinor * line.quantity)}</strong>
        <button type="button" onClick={() => dispatch({ type: 'remove', id: line.variationId })}>Remove</button>
      </div>)}
      <p className="order-total">Total: {money(cartTotal(cart))}</p>
      {checkoutEnabled && <a className="button button-primary" href="#/checkout">Checkout for pickup</a>}
    </>}
  </section>;
}
