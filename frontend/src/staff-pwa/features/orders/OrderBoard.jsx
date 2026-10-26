import { useState } from 'react';
import { queues, orderTime, paymentText, money } from './presentation';
export function Summaries({ orders, selected, onSelect }) {
  return <div className="sp-summaries">{queues.map(([status, label]) => <button key={status} className={`sp-summary sp-${status}`} aria-pressed={selected === status} onClick={() => onSelect?.(status)}><span>{label}</span><strong>{orders.filter(order => order.status === status).length}</strong><small>orders</small></button>)}</div>;
}
export default function OrderBoard({ monitor, role, onOrder }) {
  const [selected, setSelected] = useState(role === 'BOH' ? 'ACCEPTED' : 'NEW');
  return <section className="sp-board"><header className="sp-page-heading"><div><h1>Orders Dashboard</h1><p>Manage incoming orders and keep service moving.</p></div><button onClick={monitor.refresh} disabled={monitor.loading}>↻ Refresh</button></header>
    <Summaries orders={monitor.orders} selected={selected} onSelect={setSelected} />
    {monitor.loading && <p role="status">Synchronizing orders…</p>}
    {monitor.error && <p className="sp-error" role="alert">{monitor.error} Displayed orders may be stale. Actions are disabled until a successful refresh.</p>}
    {monitor.orders.length === 200 && <p className="sp-notice">Only the first 200 active orders are shown, matching the server queue limit.</p>}
    {role === 'BOH' && <p className="sp-notice">All non-cancellation order actions are available. Customer contact details remain private.</p>}
    <nav className="sp-tabs" aria-label="Order queues">{queues.map(([status, label]) => <button key={status} aria-pressed={selected === status} onClick={() => setSelected(status)}>{label} ({monitor.orders.filter(order => order.status === status).length})</button>)}</nav>
    <div className="sp-queues">{queues.map(([status, label]) => {
      const orders = monitor.orders.filter(order => order.status === status);
      return <section key={status} className={`sp-column sp-${status} ${selected === status ? 'sp-selected-column' : ''}`} aria-label={`${label} orders`}><header><h2>{label}</h2><span className="sp-count">{orders.length}</span></header>
        {!orders.length && <p className="sp-empty">{`No ${label.toLowerCase()} orders`}</p>}
        {orders.map(order => <button className="sp-order-card" key={order.id} onClick={() => onOrder(order.id)}><strong>#{order.reference}</strong><span className={`sp-badge sp-${status}`}>{label}</span><small>Placed {orderTime(order.createdAt)}<br />Pickup {orderTime(order.estimatedReadyAt)} · Melbourne</small><span>{order.items.map((item, i) => <span className="sp-item" key={item.id || i}>{item.quantity} × {item.dishName}{item.variationName ? ` · ${item.variationName}` : ''}{(item.selectedOptions || []).map((option, n) => <small className="sp-item" key={n}>+ {option.quantity} × {option.optionName} ({option.optionGroupName})</small>)}</span>)}</span>{order.notes && <span>Notes: {order.notes}</span>}{role !== 'BOH' && <><span>{order.customerName}</span><b>{money(order.totalMinor)} · {paymentText(order)}</b></>}<span className="sp-card-link">View order & actions →</span></button>)}
      </section>;
    })}</div>
  </section>;
}
