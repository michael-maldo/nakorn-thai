import { money, paymentText } from '../orders/presentation.js';
const present = value => value !== null && value !== undefined && String(value).trim() !== '';
const printable = value => String(value ?? '').replace(/[^\x20-\x7e]/g, '?');
const time = value => value && Number.isFinite(new Date(value).getTime()) ? new Date(value).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne', day: '2-digit', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase() : 'Unavailable';
export function wrapDocket(value, width, indent = '') {
  const lines = []; let remaining = printable(value), prefix = '';
  while (remaining.length > width - prefix.length) {
    const capacity = width - prefix.length;
    let split = remaining.lastIndexOf(' ', capacity);
    if (split <= 0) split = capacity;
    lines.push(prefix + remaining.slice(0, split));
    remaining = remaining.slice(split).replace(/^ +/, ''); prefix = indent.slice(0, width - 1);
  }
  lines.push(prefix + remaining); return lines;
}
// Port of Android OrderDocketFormatter / OnlineOrderDocketFormatter content hierarchy.
// Text and XML are generated from the same styled lines; ESC/POS bytes never enter XML.
export function docketLines(order, layout, { paperWidth = 58, pickupMinutes } = {}) {
  if (!['kitchen', 'detailed'].includes(layout)) throw new Error('Choose kitchen or detailed layout.');
  if (!order?.id || !['ACCEPTED', 'PREPARING', 'READY', 'COMPLETED'].includes(order.status)) throw new Error('Only server-confirmed accepted orders can be printed.');
  const width = Number(paperWidth) === 80 ? 48 : 32, lines = [];
  const line = (text = '', style = {}, indent = '') => {
    for (const chunk of wrapDocket(text, style.large ? Math.floor(width / 2) : width, indent)) lines.push({ text: chunk, ...style });
  };
  const divider = () => line('-'.repeat(width));
  const section = name => { divider(); line(name, { bold: true }); divider(); };
  const field = (label, value) => { if (present(value)) line(`${label} : ${value}`, {}, label === 'Email' ? '' : '        '); };
  const moneyRow = (label, amount, bold = false) => {
    if (!Number.isSafeInteger(amount)) return;
    const value = money(amount); line(`${label}${' '.repeat(Math.max(1, width - label.length - value.length))}${value}`, { bold });
  };
  if (layout === 'kitchen') {
    line('NAKORN THAI'); line('KITCHEN ORDER'); line(); line(`#${order.reference}`);
    line(`Order time: ${time(order.createdAt)}`);
    if (present(pickupMinutes)) line(`Pickup estimate: ${pickupMinutes} min`);
    else if (order.estimatedReadyAt) line(`Pickup: ${time(order.estimatedReadyAt)}`);
    line();
    for (const item of order.items || []) {
      line(`${item.quantity} x ${item.dishName}${item.variationName ? ` / ${item.variationName}` : ''}`);
      for (const option of item.selectedOptions || []) line(`  + ${option.quantity} x ${option.optionName}${option.optionGroupName ? ` (${option.optionGroupName})` : ''}`);
    }
    if (present(order.notes)) { line(); line(`Order notes: ${order.notes}`); }
    line(); line(); return lines;
  }
  line('NAKORN THAI', { center: true, bold: true }); line('ONLINE ORDER', { center: true }); divider();
  line(`ORDER #${order.reference}`, { center: true, bold: true, large: true });
  line(time(order.createdAt), { center: true }); divider();
  section('CUSTOMER INFORMATION'); field('Name', order.customerName); field('Phone', order.phone); field('Email', order.email);
  section('FULFILLMENT'); field('Type', order.fulfilment === 'PICKUP' ? 'PICKUP / TAKEAWAY' : order.fulfilment); field('Order Time', time(order.createdAt));
  if (order.estimatedReadyAt) field('Estimated Ready', time(order.estimatedReadyAt));
  if (present(pickupMinutes)) field('Prep Estimate', `${pickupMinutes} min`);
  field('Status', order.status);
  section('PAYMENT INFORMATION');
  field('Method', { PAYPAL: 'PAYPAL ONLINE', PAYID: 'PAYID ONLINE', PAY_AT_RESTAURANT: 'PAY AT PICKUP' }[order.paymentMethod] || order.paymentMethod);
  // Review/refund flags always take precedence over historical paidAt or a conflicting status.
  field('Status', order.paymentReviewRequired || order.refundStatus && order.refundStatus !== 'NONE' ? paymentText(order) : order.paymentStatus?.replaceAll('_', ' ') || paymentText(order));
  field('Reference', order.paymentReference); field('Currency', order.currency);
  section('ORDER ITEMS');
  for (const item of order.items || []) {
    line(`${item.quantity} x ${item.dishName}`, { bold: true });
    if (present(item.variationName)) line(`    ${item.variationName}`, {}, '    ');
    for (const option of item.selectedOptions || []) {
      const group = option.optionGroupName || '', value = option.optionName || '';
      const bold = /allergy|instruction/i.test(group) || /^(no |without )/i.test(value.trim());
      line(`    ${group ? `${group}: ` : ''}${value}${option.quantity > 1 ? ` x ${option.quantity} per dish` : ''}`, { bold }, '    ');
    }
    if (Number.isSafeInteger(item.lineTotalMinor) && item.quantity > 0) { moneyRow('Unit Price', item.unitPriceMinor); moneyRow('Line Total', item.lineTotalMinor); }
    divider();
  }
  section('ORDER SUMMARY'); moneyRow('Subtotal', order.subtotalMinor); moneyRow('TOTAL', order.totalMinor, true);
  if (present(order.notes)) { section('CUSTOMER INSTRUCTIONS'); for (const note of order.notes.split('\n')) line(note); }
  divider(); line('END OF ORDER', { center: true, bold: true }); divider(); line();
  return lines;
}
export const docketText = (order, layout, options) => docketLines(order, layout, options).map(line => line.text).join('\n') + '\n';
