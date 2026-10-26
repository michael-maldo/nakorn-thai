import { StaffPageHeading, StaffNotice, StaffLoading } from '../../staff/components/StaffPresentation';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { staffIdentityContext } from '../../identity/api/identityApi';
import { useAuth } from '../../identity/model/AuthContext';
import { changeOrderStatus, getStaffOrders, getStaffOrder, verifyStaffPayment } from '../api/orderApi';
import { needsPayment, orderActions, orderStates } from '../model/orderModel';
import { money } from '../model/cartReducer';
import OrderItemOptions from '../components/OrderItemOptions';
import PaymentReviewPanel from '../../payment/components/PaymentReviewPanel';

import { createOrderDraft, orderDraftDirty, orderDraftConflict, orderDraftCommand, reconcileOrderQueue } from '../model/staffOrderDrafts';

export {StaffOrderCard} from '../../../shared/staff-orders/components/StaffOrderCard';
import {StaffOrderCard} from '../../../shared/staff-orders/components/StaffOrderCard';
export default function OrdersAdminPage({ linked = { orderId: null, invalid: false } }) {
  const { user, authorization } = useAuth();
  if (!user || !authorization || user.enabled === false || !['ADMIN', 'FOH', 'BOH'].includes(user.role)) return null;
  const context = staffIdentityContext();
  const principal = context && context.id === user.id && context.role === user.role ? `${context.id}:${context.role}:${context.sessionId}` : `${authorization}:${user.role}`;
  return <OrdersWorkspace key={principal} linked={linked} />;
}

function OrdersWorkspace({ linked }) {
  const { user, authorization } = useAuth();
  const [history, setHistory] = useState(false);
  const [filter, setFilter] = useState('ALL');
  const [entries, setEntries] = useState([]);
  const [drafts, setDrafts] = useState({});
  const [feedback, setFeedback] = useState({});
  const [savingIds, setSavingIds] = useState({});
  const [awaitingReload, setAwaitingReload] = useState({});
  const [read, setRead] = useState({ status: 'loading', error: '', loaded: false });
  const live = useRef(false), generation = useRef(0), pendingRead = useRef(false), timer = useRef(null), saving = useRef(new Set());
  const draftRef = useRef(drafts); draftRef.current = drafts;
  const entriesRef = useRef(entries); entriesRef.current = entries;
  const currentAuthorization = useRef(authorization); currentAuthorization.current = authorization;
  const loadRef = useRef(null), principalContext = useRef(staffIdentityContext());
  const workspace = useRef(null), restoreFocus = useRef(null), reloadRequired = useRef({});
  const focusIntent = useRef(0), restoringFocus = useRef(false);
  useEffect(() => {
    const changed = () => { if (!restoringFocus.current) focusIntent.current++; };
    // Pointer/keyboard intent also covers deliberate blur onto non-focusable content.
    document.addEventListener('pointerdown', changed, true);
    document.addEventListener('keydown', changed, true);
    document.addEventListener('focusin', changed, true);
    window.addEventListener('blur', changed);
    return () => {
      focusIntent.current++; restoreFocus.current = null;
      document.removeEventListener('pointerdown', changed, true);
      document.removeEventListener('keydown', changed, true);
      document.removeEventListener('focusin', changed, true);
      window.removeEventListener('blur', changed);
    };
  }, []);
  function captureFocus(orderId) {
    const element = document.activeElement, card = element?.closest('.staff-order-card');
    restoreFocus.current = card && workspace.current?.contains(card) && (!orderId || card.dataset.orderId === orderId)
      ? { element, id: card.dataset.orderId, intent: focusIntent.current, start: element.selectionStart ?? null, end: element.selectionEnd ?? null } : null;
  }
  useLayoutEffect(() => {
    const captured = restoreFocus.current; restoreFocus.current = null;
    // Restore only focus captured before this DOM update, with no intervening user intent.
    if (!captured || captured.intent !== focusIntent.current || document.activeElement !== document.body && document.activeElement !== captured.element) return;
    const element = captured.element;
    const target = element.isConnected && !element.closest('[hidden],fieldset:disabled') && !element.disabled ? element
      : workspace.current?.querySelector(`[data-order-id="${captured.id}"]:not([hidden]) h2`) || workspace.current?.querySelector('h1');
    restoringFocus.current = true;
    try {
      target?.focus({ preventScroll: true });
      if (target === element && captured.start !== null) { try { element.setSelectionRange(captured.start, captured.end); } catch { /* Number inputs have no text selection API. */ } }
    } finally { restoringFocus.current = false; }
  }, [entries, drafts, savingIds, awaitingReload]);
  const kitchen = user.role === 'BOH';
  const changeDrafts = next => { captureFocus(); draftRef.current = next; setDrafts(next); };
  function edit(order, operation, field, value) {
    if (saving.current.has(order.id)) return;
    const previous = draftRef.current[order.id] || {};
    const draft = previous[operation] || createOrderDraft(order, operation === 'payment' ? 'PAYID' : value);
    if (field === 'operation' && previous.status) return;
    const updated = field === 'operation' ? draft : { ...draft, values: { ...draft.values, [field]: value } };
    const next = { ...previous, [operation]: updated };
    if (!orderDraftDirty(updated)) delete next[operation];
    changeDrafts({ ...draftRef.current, [order.id]: next });
  }
  function discard(id, operation) {
    const next = { ...draftRef.current };
    if (operation) { next[id] = { ...next[id] }; delete next[id][operation]; }
    else delete next[id];
    changeDrafts(next);
    captureFocus();
    setEntries(previous => previous.filter(entry => !entry.missing || Object.values(next[entry.order.id] || {}).some(orderDraftDirty)));
  }
  async function load() {
    if (!live.current || pendingRead.current || saving.current.size) return;
    clearTimeout(timer.current);
    pendingRead.current = true;
    const request = ++generation.current, token = currentAuthorization.current;
    setRead(previous => ({ ...previous, status: 'loading', error: '' }));
    try {
      const result = linked.orderId ? [await getStaffOrder(linked.orderId, token)] : await getStaffOrders(token, kitchen, history);
      if (!live.current || request !== generation.current || token !== currentAuthorization.current) return;
      captureFocus();
      setEntries(previous => reconcileOrderQueue(previous, result, draftRef.current));
      reloadRequired.current = {}; setAwaitingReload({});
      setRead({ status: 'ready', error: '', loaded: true });
    } catch (failure) {
      if (!live.current || request !== generation.current || token !== currentAuthorization.current) return;
      if (failure.status === 404 && linked.orderId) setEntries(previous => reconcileOrderQueue(previous, [], draftRef.current));
      setRead(previous => ({ ...previous, status: 'error', error: failure.status === 404 && linked.orderId
        ? 'This order is not available for your staff role or no longer exists.' : failure.message }));
    } finally {
      if (live.current && request === generation.current) {
        pendingRead.current = false;
        timer.current = setTimeout(() => loadRef.current(), 15000);
      }
    }
  }
  loadRef.current = load;
  useEffect(() => {
    live.current = true;
    loadRef.current();
    return () => { live.current = false; generation.current++; pendingRead.current = false; clearTimeout(timer.current); };
  }, [authorization, kitchen, history, linked.orderId]);

  async function mutate(order, operation, command) {
    const entry = entriesRef.current.find(item => item.order.id === order.id);
    const draft = draftRef.current[order.id]?.[operation];
    if (!entry || entry.missing || saving.current.has(order.id) || reloadRequired.current[order.id] || orderDraftConflict(draft, entry.order)) return false;
    captureFocus(order.id);
    saving.current.add(order.id);
    clearTimeout(timer.current); generation.current++; pendingRead.current = false;
    setRead(previous => ({ ...previous, status: previous.loaded ? 'ready' : 'idle' }));
    setSavingIds(previous => ({ ...previous, [order.id]: true }));
    setFeedback(previous => ({ ...previous, [order.id]: null }));
    const isCurrent = () => {
      const current = staffIdentityContext(), initiating = principalContext.current;
      return live.current && current && initiating && current.id === initiating.id
        && current.sessionId === initiating.sessionId && current.role === initiating.role;
    };
    try {
      const result = operation === 'status' ? await changeOrderStatus(order.id, command, currentAuthorization.current, isCurrent)
        : await verifyStaffPayment(order.id, command, currentAuthorization.current, isCurrent);
      if (!isCurrent()) return false;
      const text = operation === 'status' ? `Order #${order.reference}: ${orderStates[command.status]}.`
        : result.paid ? 'Payment verified.' : 'Payment is not yet completed. Ask the customer to complete payment.';
      setFeedback(previous => ({ ...previous, [order.id]: { kind: 'success', text } }));
      if (draftRef.current[order.id]?.[operation] === draft) {
        const next = { ...draftRef.current, [order.id]: { ...draftRef.current[order.id] } };
        delete next[order.id][operation]; changeDrafts(next);
      }
      const reloadReason = operation === 'payment' && !result.paid ? 'checked' : 'saved';
      reloadRequired.current[order.id] = reloadReason;
      setAwaitingReload(previous => ({ ...previous, [order.id]: reloadReason }));
      return true;
    } catch (failure) {
      if (live.current) {
        setFeedback(previous => ({ ...previous, [order.id]: { kind: 'error', text: failure.message } }));
        if (failure.status === 409) { reloadRequired.current[order.id] = 'conflict'; setAwaitingReload(previous => ({ ...previous, [order.id]: 'conflict' })); }
        if (failure.status === 409 && draftRef.current[order.id]?.[operation]) {
          changeDrafts({ ...draftRef.current, [order.id]: { ...draftRef.current[order.id],
            [operation]: { ...draftRef.current[order.id][operation], conflict: true } } });
        }
      }
      return false;
    } finally {
      saving.current.delete(order.id);
      if (live.current) {
        setSavingIds(previous => { const next = { ...previous }; delete next[order.id]; return next; });
        if (!saving.current.size) loadRef.current();
      }
    }
  }
  const states = history ? ['COMPLETED', 'CANCELLED'] : ['NEW', 'ACCEPTED', 'PREPARING', 'READY'];
  const current = entries.filter(entry => !entry.missing);
  const visible = entries.filter(({ order }) => filter === 'ALL' || order.status === filter);
  return <main ref={workspace} className="staff-menu page-width">
    <StaffPageHeading title={kitchen ? 'Kitchen orders' : 'Orders'} />
    <p>New → Accepted → Preparing → Ready for pickup → Completed. Only ADMIN can cancel orders.</p>
    {linked.invalid && <StaffNotice>This order link is invalid. Choose an order from the queue.</StaffNotice>}
    {linked.orderId && <p><a href="#/staff/orders">Back to all orders</a></p>}
    <div className="staff-toolbar">
      {!linked.orderId && !kitchen && <label>Queue <select disabled={!!Object.keys(savingIds).length} value={history ? 'history' : 'active'} onChange={event => {
        generation.current++; pendingRead.current = false; clearTimeout(timer.current);
        setHistory(event.target.value === 'history'); setFilter('ALL');
        setEntries(previous => previous.filter(({ order }) => Object.values(draftRef.current[order.id] || {}).some(orderDraftDirty)).map(entry => ({ ...entry, missing: true })));
        setRead({ status: 'loading', error: '', loaded: false });
      }}><option value="active">Active orders</option><option value="history">History (placed in the last 24 hours)</option></select></label>}
      {!linked.orderId && <label>Status <select value={filter} onChange={event => setFilter(event.target.value)}><option value="ALL">All states</option>{states.map(state => <option key={state} value={state}>{orderStates[state]} ({current.filter(({ order }) => order.status === state).length})</option>)}</select></label>}
      <button type="button" disabled={read.status === 'loading' || !!Object.keys(savingIds).length} onClick={() => loadRef.current()}>Refresh</button>
    </div>
    <p>Refreshes every 15 seconds. Times shown in Melbourne time.</p>
    {!kitchen && <PaymentReviewPanel key={authorization} authorization={authorization} />}
    {read.status === 'loading' && <StaffLoading>{read.loaded ? 'Refreshing orders… Displayed orders are from an earlier load.' : 'Loading orders…'}</StaffLoading>}
    {read.status === 'error' && <StaffNotice>{read.error} {read.loaded && 'Displayed orders are from an earlier load and may be out of date. '}Use Refresh to retry.</StaffNotice>}
    {Object.entries(feedback).filter(([id, message]) => message && !entries.some(entry => entry.order.id === id)).map(([id, message]) => <StaffNotice key={id} variant={message.kind === 'error' ? 'error' : 'info'}>{message.text}</StaffNotice>)}
    {current.length === 200 && <p>Showing the first 200 orders in this queue.</p>}
    {read.status === 'ready' && visible.length === 0 && <p>No orders in this view.</p>}
    <div className="order-queue">{entries.map(({ order, missing }) => <StaffOrderCard key={order.id} order={order} role={user.role}
      hidden={filter !== 'ALL' && order.status !== filter} missing={missing} busy={!!savingIds[order.id]} drafts={drafts[order.id]}
      feedback={feedback[order.id]} awaitingReload={awaitingReload[order.id]} onDraft={edit} onDiscard={discard}
      onAction={(currentOrder, command) => mutate(currentOrder, 'status', command)}
      onPayment={(currentOrder, command) => mutate(currentOrder, 'payment', command)} />)}</div>
  </main>;
}
