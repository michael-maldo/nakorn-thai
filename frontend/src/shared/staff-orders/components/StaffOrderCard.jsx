import {useRef} from 'react';
import {StaffNotice,StaffLoading} from '../../../domains/staff/components/StaffPresentation';
import {needsPayment,orderActions,orderStates} from '../model/orderModel';
import {createOrderDraft,orderDraftConflict,orderDraftCommand,orderDraftDirty} from '../model/staffOrderDrafts';
import {money} from '../../../domains/ordering/model/cartReducer';
import OrderItemOptions from '../../../domains/ordering/components/OrderItemOptions';
const actionLabels = { ACCEPTED: 'Accept order', PREPARING: 'Start preparing', READY: 'Mark ready', COMPLETED: 'Complete handover', CANCELLED: 'Cancel order' };
const time = value => value ? new Date(value).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne', dateStyle: 'short', timeStyle: 'short' }) : '—';

export function StaffOrderCard({ order, role, busy, onAction, onPayment, drafts = {}, onDraft = () => {}, onDiscard = () => {}, missing = false, feedback, awaitingReload = false, hidden = false }) {
  const heading = useRef(null);
  const statusDraft = drafts.status;
  const paymentDraft = drafts.payment;
  const action = statusDraft?.operation || '';
  const statusConflict = orderDraftConflict(statusDraft, order);
  const paymentConflict = orderDraftConflict(paymentDraft, order);
  const paymentValues = paymentDraft?.values || { bankReference: '', bankReceiptChecked: false };
  const discard = operation => { onDiscard(order.id, operation); heading.current?.focus(); };
  const draftField = (operation, name, value) => onDraft(order, operation, name, value);
  const conflictNotice = (operation, conflict) => conflict && <StaffNotice>Order changed since this {operation} draft began. Your draft is retained; saving is blocked until you discard it and use the latest order. <button type="button" disabled={busy} onClick={() => discard(operation)}>Discard {operation} draft and use latest order</button></StaffNotice>;
  const front = role !== 'BOH';
  const blocked = needsPayment(order);
  return <article className="order-panel staff-order-card" data-order-id={order.id} hidden={hidden}>
    <header className="staff-toolbar"><h2 ref={heading} tabIndex={-1}>#{order.reference}</h2><strong className="staff-order-state">{orderStates[order.status]}</strong></header>
    <p>Placed {time(order.createdAt)}{order.estimatedReadyAt && <> · Pickup {time(order.estimatedReadyAt)}</>}</p>
    {front && <p><strong>{order.customerName}</strong> · <a href={`tel:${order.phone}`}>{order.phone}</a></p>}
    <ul>{order.items.map((item, index) => <li key={item.id || index}><strong>{item.quantity} × {item.dishName}</strong>{item.variationName && <> — {item.variationName}</>}<OrderItemOptions options={item.selectedOptions || []} /></li>)}</ul>
    {order.notes && <p className="staff-order-notes"><strong>Customer notes:</strong> {order.notes}</p>}
    {order.cancellationReason && <p>Cancellation: {order.cancellationReason}</p>}
    {front && <p>{money(order.totalMinor)} · {order.paymentMethod?.replaceAll('_', ' ')} · {order.paidAt ? 'Paid' : ['PAYPAL','PAYID'].includes(order.paymentMethod) ? 'Payment pending' : 'Unpaid'}</p>}
    {!front && blocked && <StaffNotice>Payment must be verified by ADMIN or FOH before acceptance or handover.</StaffNotice>}
    {front && order.paymentReviewRequired && <StaffNotice>Financial review required. A historical capture does not confirm that funds are still available.</StaffNotice>}
    {front && order.refundStatus && order.refundStatus !== 'NONE' && <p>Refund obligation: {order.refundStatus.replaceAll('_', ' ')}. Original payment history is retained.</p>}
    {missing && <StaffNotice>This order is no longer in this queue. Unsaved drafts are retained, but cannot be submitted. <button type="button" onClick={() => onDiscard(order.id)}>Discard drafts for this order</button></StaffNotice>}
    {feedback && <StaffNotice variant={feedback.kind === 'error' ? 'error' : 'info'}>{feedback.text}</StaffNotice>}
    {awaitingReload && <StaffNotice variant="info">{awaitingReload === 'conflict' ? 'The server reports that this order changed.' : awaitingReload === 'checked' ? 'Payment check finished.' : 'This order was updated.'} Displayed details are from an earlier load; refresh the queue before another action.</StaffNotice>}
    {busy && <StaffLoading>Saving order #{order.reference}…</StaffLoading>}
    <fieldset disabled={busy || missing || awaitingReload}>
      <legend className="sr-only">Order actions for {order.reference}</legend>
      {front && (paymentDraft || blocked && !['COMPLETED', 'CANCELLED'].includes(order.status)) && <div>
        <p>Verify payment before accepting or handing over this order.</p>
        {(paymentDraft || order.paymentMethod === 'PAYID') && <form onSubmit={event => { event.preventDefault(); if(paymentConflict)return; onPayment(order, orderDraftCommand(paymentDraft || createOrderDraft(order, 'PAYID'))); }}>
          <label>Bank transaction reference<input name="bankReference" value={paymentValues.bankReference} onChange={event => draftField('payment', 'bankReference', event.target.value)} required maxLength={150} pattern=".*\S.*" /></label>
          <label><span><input type="checkbox" name="bankReceiptChecked" checked={paymentValues.bankReceiptChecked} onChange={event => draftField('payment', 'bankReceiptChecked', event.target.checked)} required /> I verified receipt of {money(order.totalMinor)} in the bank account.</span></label>
          <p role="status">{orderDraftDirty(paymentDraft) ? 'Unsaved payment draft' : 'No unsaved payment changes'}</p>
          {conflictNotice('payment', paymentConflict)}
          <button disabled={paymentConflict}>Confirm PayID payment</button>
          {paymentDraft && !paymentConflict && <button type="button" onClick={() => discard('payment')}>Discard payment draft</button>}
        </form>}
        {order.paymentMethod === 'PAYPAL' && <button type="button" onClick={() => onPayment(order)}>Check PayPal payment</button>}
      </div>}
      <div className="staff-toolbar">{orderActions(role, order.status).map(status => <button type="button" key={status} onClick={() => onDraft(order, 'status', 'operation', status)} disabled={!!statusDraft || blocked && ['ACCEPTED', 'COMPLETED'].includes(status)}>{actionLabels[status]}</button>)}</div>
      {action && <form className="staff-order-action" onSubmit={async event => {
        event.preventDefault();
        if(statusConflict || !orderActions(role, order.status).includes(action))return;
        await onAction(order, orderDraftCommand(statusDraft));
      }}>
        <p><strong>{actionLabels[action]} #{order.reference}</strong></p>
        {action === 'ACCEPTED' && <label>Ready for pickup in (minutes)<input type="number" name="pickupMinutes" min={5} max={180} step={1} value={statusDraft.values.pickupMinutes} onChange={event => draftField('status', 'pickupMinutes', event.target.value)} required /></label>}
        {action === 'CANCELLED' && <><label>Cancellation reason<input name="reason" value={statusDraft.values.reason} onChange={event => draftField('status', 'reason', event.target.value)} maxLength={500} pattern=".*\S.*" required /></label>{order.paidAt && <p>Cancellation does not refund payment. Arrange the refund separately.</p>}</>}
        {action === 'COMPLETED' && <label><span><input type="checkbox" name="paymentCollected" checked={statusDraft.values.paymentCollected} onChange={event => draftField('status', 'paymentCollected', event.target.checked)} required /> Payment is collected and the customer is receiving this order.</span></label>}
        <p role="status">Unsaved status draft</p>{conflictNotice('status', statusConflict)}
        <div className="staff-toolbar"><button disabled={statusConflict || !orderActions(role, order.status).includes(action) || blocked && ['ACCEPTED','COMPLETED'].includes(action)}>Confirm {actionLabels[action].toLowerCase()}</button><button type="button" onClick={() => discard('status')}>Discard status draft</button></div>
      </form>}
    </fieldset>
  </article>;
}
