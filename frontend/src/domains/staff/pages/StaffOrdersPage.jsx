import { useEffect, useState } from 'react';
import { changeOrderStatus, getStaffOrders } from '../../ordering/api/orderApi';
import { money } from '../../ordering/model/cartReducer';

export default function StaffOrdersPage({ kitchen = false }) {
  const [auth, setAuth] = useState(''); const [orders, setOrders] = useState([]);
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const [history, setHistory] = useState(false); const [updated, setUpdated] = useState(null);
  const [action, setAction] = useState(null); const [minutes, setMinutes] = useState(30);
  const [reason, setReason] = useState(''); const [paid, setPaid] = useState(false);
  const [stale, setStale] = useState(false);
  async function login(event) {
    event.preventDefault(); setError(''); setBusy(true);
    const data = new FormData(event.currentTarget);
    const authorization = 'Basic ' + btoa(String.fromCharCode(...new TextEncoder().encode(`${data.get('username')}:${data.get('password')}`)));
    try { setOrders(await getStaffOrders(authorization, kitchen, history)); setAuth(authorization); setUpdated(new Date()); setStale(false); }
    catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  useEffect(() => {
    if (!auth || busy) return;
    let stopped = false, timer;
    async function poll() {
      try {
        const data = await getStaffOrders(auth, kitchen, history);
        if (!stopped) { setOrders(data); setUpdated(new Date()); setStale(false); setError(''); }
      } catch (failure) { if (!stopped) { setError(failure.message); setStale(true); } }
      if (!stopped) timer = setTimeout(poll, 5000);
    }
    poll(); return () => { stopped = true; clearTimeout(timer); };
  }, [auth, kitchen, history, busy]);
  async function apply(event) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await changeOrderStatus(action.order.id, { version: action.order.version, status: action.status, pickupMinutes: action.status === 'ACCEPTED' ? Number(minutes) : null, reason, paymentCollected: paid }, auth);
      setAction(null); setOrders(await getStaffOrders(auth, kitchen, history)); setUpdated(new Date()); setStale(false);
    } catch (failure) { setAction(null); setError(failure.message); setStale(true); }
    finally { setBusy(false); }
  }
  function choose(order, status) { setAction({ order, status }); setReason(''); setPaid(false); setMinutes(30); }
  return <main className="staff-menu page-width">
    <header className="staff-heading"><div><a href="#/staff">Staff home</a><h1>{kitchen ? 'Kitchen dashboard' : 'Front-of-house orders'}</h1></div>
      {auth && <button disabled={busy} onClick={() => { setAuth(''); setOrders([]); setAction(null); setError(''); }}>Sign out</button>}
    </header>
    {error && <p role="alert" className="staff-error">{error}</p>}
    {!auth ? <form className="staff-panel staff-login" onSubmit={login}>
      <h2>{kitchen ? 'Kitchen' : 'FOH'} sign in</h2><label>Username<input name="username" required autoComplete="username" /></label><label>Password<input type="password" name="password" required autoComplete="current-password" /></label><button disabled={busy}>Sign in</button>
    </form> : <>
      <div className="staff-toolbar">
        <p role="status">{orders.length} orders · refreshed {updated?.toLocaleTimeString()} · updates every 5 seconds</p>
        {!kitchen && <label className="staff-check"><input type="checkbox" checked={history} disabled={busy} onChange={(e) => { setHistory(e.target.checked); setAction(null); setStale(true); }} />Completed / cancelled in the last 24 hours</label>}
      </div>
      {orders.length >= 200 && <p>Showing the first 200 orders in this view.</p>}
      {action && <form className="staff-panel" onSubmit={apply}>
        <h2>{action.status} — {action.order.reference}</h2>
        <fieldset disabled={busy || stale}>
          {action.status === 'ACCEPTED' && <label>Estimated pickup in minutes<input type="number" required min="5" max="180" value={minutes} onChange={(e) => setMinutes(e.target.value)} /></label>}
          {action.status === 'CANCELLED' && <label>Reason shown to the customer<textarea required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} /></label>}
          {action.status === 'COMPLETED' && <label className="staff-check"><input type="checkbox" required checked={paid} onChange={(e) => setPaid(e.target.checked)} />Payment of {money(action.order.totalMinor)} has been collected and the order handed over.</label>}
          <div className="staff-toolbar"><button>Confirm</button><button type="button" onClick={() => setAction(null)}>Cancel</button></div>
        </fieldset>
      </form>}
      {!orders.length && <p>No orders in this queue.</p>}
      <div className="order-queue">{orders.map((order) => <article className="order-panel" key={order.id}>
        <h2>#{order.reference} · {order.status}</h2>
        <p>Received {new Date(order.createdAt).toLocaleString()}</p>
        {order.estimatedReadyAt && <p>Pickup estimate: {new Date(order.estimatedReadyAt).toLocaleTimeString()}</p>}
        {!kitchen && <p>{order.customerName} · <a href={`tel:${order.phone}`}>{order.phone}</a></p>}
        <ul>{order.items.map((line,index) => <li key={index}><strong>{line.quantity} × {line.dishName}</strong> — {line.variationName}</li>)}</ul>
        {order.notes && <p className="order-notes"><strong>Customer notes:</strong> {order.notes}</p>}
        {order.cancellationReason && <p>Cancelled: {order.cancellationReason}</p>}
        {!kitchen && <p>{money(order.totalMinor)} · {order.paidAt ? 'Payment recorded' : 'Pay at restaurant'}</p>}
        <div className="staff-toolbar">
          {!kitchen && order.status === 'NEW' && <button disabled={busy || stale} onClick={() => choose(order, 'ACCEPTED')}>Accept order</button>}
          {kitchen && order.status === 'ACCEPTED' && <button disabled={busy || stale} onClick={() => choose(order, 'PREPARING')}>Start preparing</button>}
          {kitchen && order.status === 'PREPARING' && <button disabled={busy || stale} onClick={() => choose(order, 'READY')}>Mark ready</button>}
          {!kitchen && order.status === 'READY' && <button disabled={busy || stale} onClick={() => choose(order, 'COMPLETED')}>Collect payment & hand over</button>}
          {!kitchen && !['COMPLETED','CANCELLED'].includes(order.status) && <button disabled={busy || stale} onClick={() => choose(order, 'CANCELLED')}>Cancel order</button>}
        </div>
      </article>)}</div>
    </>}
  </main>;
}
