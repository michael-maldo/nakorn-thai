import { useCallback } from 'react';
import { getReadOnlyOrder } from '../api/orderApi';
import OrderTrackingStatus from '../components/OrderTrackingStatus';
import TrackingLayout from '../components/TrackingLayout';
import useTrackedOrder from '../hooks/useTrackedOrder';

const money = (minor) => 'AUD ' + (minor / 100).toFixed(2);

export default function ReadOnlyOrderTrackingPage({ token }) {
  const load = useCallback((signal) => getReadOnlyOrder(token, signal), [token]);
  const { order, error, expired, retry } = useTrackedOrder(token ? load : null);
  return <TrackingLayout>
    {!token && <p role="alert">This order tracking link is missing or invalid. Use the private link from your confirmation email if you received one.</p>}
    {expired && <p role="alert">This order tracking link has expired or is invalid. Contact Nakorn Thai with your order reference for help.</p>}
    {error && <div className="tracking-card"><p role="alert">{error}</p><button className="button button-outline" type="button" onClick={retry}>Try again</button></div>}
    {token && !order && !error && !expired && <p role="status">Loading your order…</p>}
    {order && <>
      <OrderTrackingStatus order={order} />
      <section className="tracking-card" aria-labelledby="your-order">
        <h2 id="your-order">Your order</h2>
        <p>{order.fulfilment === 'PICKUP' ? 'Pickup' : order.fulfilment}</p>
        {order.items.map((item, index) => <article className="tracking-item" key={index}>
          <h3>{item.quantity} × {item.name}</h3>
          <p>{item.variation}</p><p>Unit price (including options): {money(item.unitPriceMinor)}</p>
          {item.options.length > 0 && <ul>{item.options.map((option, optionIndex) => <li key={optionIndex}>
            {option.groupName}: {option.name} × {option.quantity}<br />
            Adjustment per item: {money(option.priceDeltaMinor * option.quantity)} ({money(option.priceDeltaMinor)} each)
          </li>)}</ul>}
          <p>Line total: {money(item.lineTotalMinor)}</p>
        </article>)}
        <p><strong>Order total: {money(order.totalMinor)}</strong></p>
        {order.customerInstructions && <><h3>Your instructions</h3><p className="tracking-instructions">{order.customerInstructions}</p></>}
      </section>
    </>}
    {token && <p>This private link provides read-only order tracking for seven days. Keep it private.</p>}
    <a href="#contact">Contact Nakorn Thai for order help</a>
  </TrackingLayout>;
}
