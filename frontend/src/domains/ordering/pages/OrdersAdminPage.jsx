import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { changeOrderStatus, getStaffOrders, verifyStaffPayment } from '../api/orderApi';
import { needsPayment, orderActions, orderStates } from '../model/orderModel';
import { money } from '../model/cartReducer';
import OrderItemOptions from '../components/OrderItemOptions';

const actionLabels = { ACCEPTED: 'Accept order', PREPARING: 'Start preparing', READY: 'Mark ready', COMPLETED: 'Complete handover', CANCELLED: 'Cancel order' };
const time = value => value ? new Date(value).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne', dateStyle: 'short', timeStyle: 'short' }) : '—';

export function StaffOrderCard({ order, role, busy, onAction, onPayment }) {
  const [action, setAction] = useState('');
  const front = role !== 'BOH';
  const blocked = needsPayment(order);
  return <article className="order-panel staff-order-card">
    <header className="staff-toolbar"><h2>#{order.reference}</h2><strong className="staff-order-state">{orderStates[order.status]}</strong></header>
    <p>Placed {time(order.createdAt)}{order.estimatedReadyAt && <> · Pickup {time(order.estimatedReadyAt)}</>}</p>
    {front && <p><strong>{order.customerName}</strong> · <a href={`tel:${order.phone}`}>{order.phone}</a></p>}
    <ul>{order.items.map((item, index) => <li key={item.id || index}><strong>{item.quantity} × {item.dishName}</strong>{item.variationName && <> — {item.variationName}</>}<OrderItemOptions options={item.selectedOptions || []} /></li>)}</ul>
    {order.notes && <p className="staff-order-notes"><strong>Customer notes:</strong> {order.notes}</p>}
    {order.cancellationReason && <p>Cancellation: {order.cancellationReason}</p>}
    {front && <p>{money(order.totalMinor)} · {order.paymentMethod?.replaceAll('_', ' ')} · {order.paidAt ? 'Paid' : 'Unpaid'}</p>}
    <fieldset disabled={busy}>
      <legend className="sr-only">Order actions for {order.reference}</legend>
      {front && blocked && !['COMPLETED', 'CANCELLED'].includes(order.status) && <div>
        <p>Verify payment before accepting or handing over this order.</p>
        {order.paymentMethod === 'PAYID' && <form onSubmit={event => { event.preventDefault(); const data = new FormData(event.currentTarget); onPayment(order, { version: order.version, bankReference: data.get('bankReference').trim() }); }}>
          <label>Bank transaction reference<input name="bankReference" required maxLength={150} pattern=".*\S.*" /></label>
          <label><span><input type="checkbox" required /> I verified receipt of {money(order.totalMinor)} in the bank account.</span></label>
          <button>Confirm PayID payment</button>
        </form>}
        {order.paymentMethod === 'PAYPAL' && <button type="button" onClick={() => onPayment(order)}>Check PayPal payment</button>}
      </div>}
      <div className="staff-toolbar">{orderActions(role, order.status).map(status => <button type="button" key={status} disabled={blocked && ['ACCEPTED', 'COMPLETED'].includes(status)} onClick={() => setAction(status)}>{actionLabels[status]}</button>)}</div>
      {action && <form className="staff-order-action" onSubmit={async event => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const success = await onAction(order, { version: order.version, status: action,
          ...(action === 'ACCEPTED' ? { pickupMinutes: Number(data.get('pickupMinutes')) } : {}),
          ...(action === 'CANCELLED' ? { reason: data.get('reason').trim() } : {}),
          paymentCollected: data.get('paymentCollected') === 'on' });
        if (success) setAction('');
      }}>
        <p><strong>{actionLabels[action]} #{order.reference}</strong></p>
        {action === 'ACCEPTED' && <label>Ready for pickup in (minutes)<input type="number" name="pickupMinutes" min={5} max={180} step={1} defaultValue={20} required /></label>}
        {action === 'CANCELLED' && <><label>Cancellation reason<input name="reason" maxLength={500} pattern=".*\S.*" required /></label>{order.paidAt && <p>Cancellation does not refund payment. Arrange the refund separately.</p>}</>}
        {action === 'COMPLETED' && <label><span><input type="checkbox" name="paymentCollected" required /> Payment is collected and the customer is receiving this order.</span></label>}
        <div className="staff-toolbar"><button>Confirm {actionLabels[action].toLowerCase()}</button><button type="button" onClick={() => setAction('')}>Back</button></div>
      </form>}
    </fieldset>
  </article>;
}

export default function OrdersAdminPage() {
  const { user, authorization } = useAuth();
  const [history, setHistory] = useState(false);
  const [filter, setFilter] = useState('ALL');
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const saving = useRef(false);
  const kitchen = user.role === 'BOH';
  useEffect(() => {
    let live = true;
    let timer;
    async function load() {
      try {
        const result = await getStaffOrders(authorization, kitchen, history);
        if (live) { setOrders(result); setError(''); }
      } catch (failure) { if (live) setError(failure.message); }
      finally { if (live) timer = setTimeout(load, 15000); }
    }
    if (!busy) load();
    return () => { live = false; clearTimeout(timer); };
  }, [authorization, kitchen, history, busy, revision]);
  async function mutate(action, message) {
    if (saving.current) return false;
    saving.current = true; setBusy(true); setError(''); setNotice('');
    try { const result = await action(); setNotice(typeof message === 'function' ? message(result) : message); return true; }
    catch (failure) { setNotice(failure.message); return false; }
    finally { saving.current = false; setBusy(false); setOrders(null); }
  }
  const states = history ? ['COMPLETED', 'CANCELLED'] : kitchen ? ['ACCEPTED', 'PREPARING', 'READY'] : ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];
  const visible = orders?.filter(order => filter === 'ALL' || order.status === filter);
  return <main className="staff-menu page-width">
    <p className="staff-kicker">{kitchen ? 'KITCHEN' : 'ORDER MANAGEMENT'}</p><h1>{kitchen ? 'Kitchen orders' : 'Orders'}</h1>
    <p>{kitchen ? 'Accepted → Preparing → Ready for pickup. FOH handles customer handover.' : 'New → Accepted → Preparing → Ready for pickup → Completed. Orders can be cancelled before completion.'}</p>
    {user.role === 'FOH' && <p>The kitchen updates preparation and ready states.</p>}
    <div className="staff-toolbar">
      {!kitchen && <label>Queue <select disabled={busy} value={history ? 'history' : 'active'} onChange={event => { setHistory(event.target.value === 'history'); setFilter('ALL'); setOrders(null); }}><option value="active">Active orders</option><option value="history">History (placed in the last 24 hours)</option></select></label>}
      <label>Status <select value={filter} onChange={event => setFilter(event.target.value)}><option value="ALL">All states</option>{states.map(state => <option key={state} value={state}>{orderStates[state]} ({orders?.filter(order => order.status === state).length || 0})</option>)}</select></label>
      <button type="button" disabled={busy} onClick={() => setRevision(value => value + 1)}>Refresh</button>
    </div>
    <p>Refreshes every 15 seconds. Times shown in Melbourne time.</p>
    {notice && <p role="status">{notice}</p>}{error && <p className="staff-error" role="alert">{error} Use Refresh to retry.</p>}
    {!orders && !error && <p role="status">{busy ? 'Saving…' : 'Loading orders…'}</p>}
    {orders?.length === 200 && <p>Showing the first 200 orders in this queue.</p>}
    {visible?.length === 0 && <p>No orders in this view.</p>}
    <div className="order-queue">{visible?.map(order => <StaffOrderCard key={`${order.id}-${order.version}`} order={order} role={user.role} busy={busy}
      onAction={(current, command) => mutate(() => changeOrderStatus(current.id, command, authorization), `Order #${current.reference}: ${orderStates[command.status]}.`)}
      onPayment={(current, command) => mutate(() => verifyStaffPayment(current.id, command, authorization), result => result.paid ? 'Payment verified.' : 'Payment is not yet completed. Ask the customer to complete payment.')} />)}</div>
  </main>;
}
