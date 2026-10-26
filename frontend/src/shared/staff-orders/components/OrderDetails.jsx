import { useEffect, useRef, useState } from 'react';
import Dialog from './Dialog';
import { printConfirmedAcceptance } from '../printing/acceptancePrinting';
import {StaffOrderCard} from './StaffOrderCard';
import {useOrdersRuntime} from '../runtime';
import { createOrderDraft } from '../model/staffOrderDrafts';


export default function OrderDetails({ id, onClose, onChanged, printer, config, canPrint, monitoringError, initialDrafts, onDrafts }) {
  const {user,authorization,identity:staffIdentityContext,api:{getStaffOrder,changeOrderStatus,verifyStaffPayment}}=useOrdersRuntime();
  const [order, setOrder] = useState(null), [drafts, setDrafts] = useState(initialDrafts||{}), [busy, setBusy] = useState(false), [blocked, setBlocked] = useState(false), [message, setMessage] = useState('');
  const live = useRef(false), pending = useRef(false), readSequence = useRef(0);
  const detailController = useRef(null);
  const owner = useRef(staffIdentityContext());
  const current = () => {
    const context = staffIdentityContext(), initial = owner.current;
    return live.current && !!context && context.id === initial?.id && context.sessionId === initial?.sessionId && context.role === initial?.role;
  };
  async function fetchDetails() {
    const controller = new AbortController(); detailController.current = controller;
    const timeout = setTimeout(() => controller.abort(), 15000);
    try { return await getStaffOrder(id, authorization, controller.signal); }
    finally { clearTimeout(timeout); if (detailController.current === controller) detailController.current = null; }
  }
  useEffect(()=>{onDrafts?.(drafts);},[drafts]);
  async function read(discard=true) {
    if (pending.current) return;
    const sequence = ++readSequence.current;
    setBusy(true);
    try {
      const next = await fetchDetails();
      if (current() && sequence === readSequence.current) { setOrder(next); if(discard)setDrafts({}); setBlocked(false); setMessage(''); }
    } catch (error) { if (current()) { setMessage(error.message); setBlocked(true); } }
    finally { if (current() && sequence === readSequence.current) setBusy(false); }
  }
  useEffect(() => { live.current = true; read(!initialDrafts); return () => { live.current = false; detailController.current?.abort(); readSequence.current++; }; }, [id]);
  async function mutate(operation) {
    if (pending.current || busy || blocked || monitoringError || !current()) return;
    pending.current = true; setBusy(true); setMessage('');
    let confirmed = false;
    try {
      await operation(); confirmed = true;
      if (!current()) return;
      setBlocked(true); setDrafts({});
      const next = await fetchDetails();
      if (!current()) return;
      setOrder(next); setBlocked(false); setMessage('Server confirmed the update.'); onChanged();
      return next;
    } catch (error) {
      if (current()) {
        setBlocked(true);
        setMessage(confirmed ? `Update was confirmed, but details could not reload. ${error.message} Refresh; do not resubmit.` : `${error.message} Refresh and inspect the order before another action. An interrupted response may have committed. No automatic retry.`);
        onChanged();
      }
    } finally { pending.current = false; if (current()) setBusy(false); }
  }
  async function print(next, automatic = false, pickupMinutes) {
    if (!canPrint || pending.current || !current()) return;
    pending.current = true; setBusy(true);
    try { const result = await printer.printOrder(config, next, pickupMinutes); if (current()) setMessage(`${automatic ? 'Acceptance confirmed. ' : ''}${result.message}`); }
    catch (error) { if (current()) setMessage(`Order remains ${next.status}. ${error.message}`); }
    finally { pending.current = false; if (current()) setBusy(false); }
  }
  return <Dialog title={order ? `Order #${order.reference}` : 'Order details'} onClose={() => { if (!pending.current) onClose(); }}>
    {!order && <p role="status">{busy ? 'Loading order…' : 'Order unavailable.'}</p>}
    {message && <p className="sp-notice" role="status">{message}</p>}
    {monitoringError && <p role="alert" className="sp-error">Monitoring unavailable. Refresh the dashboard before changing orders.</p>}
    <button disabled={busy} onClick={()=>read()}>Reload order / discard drafts</button>
    {order && <p><strong>Fulfillment:</strong> {order.fulfilment || 'Unavailable'}{user.role !== 'BOH' && <>{order.email && <><br /><strong>Email:</strong> {order.email}</>}{order.paymentStatus && <><br /><strong>Server payment status:</strong> {order.paymentStatus.replaceAll('_', ' ')}</>}{order.paymentReference && <><br /><strong>Payment reference:</strong> {order.paymentReference}</>}</>}</p>}
    {order && <StaffOrderCard order={order} role={user.role} busy={busy || !!monitoringError} drafts={drafts} awaitingReload={blocked} onDraft={(snapshot, operation, field, value) => setDrafts(previous => {
      const draft = previous[operation] || createOrderDraft(snapshot, operation === 'payment' ? 'PAYID' : value);
      return { ...previous, [operation]: field === 'operation' ? draft : { ...draft, values: { ...draft.values, [field]: value } } };
    })} onDiscard={(_id, operation) => setDrafts(previous => { const next = { ...previous }; if (operation) delete next[operation]; else return {}; return next; })}
      onAction={async (snapshot, command) => {
        const next = await mutate(() => changeOrderStatus(snapshot.id, command, authorization, current));
        // A successful PATCH plus authoritative detail read must precede any print.
        await printConfirmedAcceptance({ order: next, target: command.status, enabled: canPrint, autoPrint: config.autoPrint, current, send: order => print(order, true, command.pickupMinutes) });
      }} onPayment={(snapshot, command) => mutate(() => verifyStaffPayment(snapshot.id, command, authorization, current))} />}
    {order && canPrint && ['ACCEPTED', 'PREPARING', 'READY'].includes(order.status) && <button disabled={busy || blocked || !!monitoringError} onClick={async () => {
      if (!window.confirm('Inspect existing paper first. Send one deliberate reprint of this order?')) return;
      // Re-read before reprinting; never print from an earlier queue snapshot.
      if (pending.current) return;
      pending.current = true; setBusy(true);
      let latest;
      try { latest = await fetchDetails(); if (current()) setOrder(latest); }
      catch (error) { if (current()) setMessage(error.message); }
      finally { pending.current = false; if (current()) setBusy(false); }
      if (latest && current()) await print(latest);
    }}>Manual reprint · {config.layout}</button>}
    {!canPrint && <p>Browser printing disabled. Order acceptance remains available.</p>}
  </Dialog>;
}
