import { money } from '../model/cartReducer';
import OrderItemOptions from './OrderItemOptions';

export default function CheckoutSummary({ line, historical = false }) {
  return <div className="checkout-line-summary">
    <strong>{line.quantity} × {line.dishName} ({line.variationName})</strong>
    {line.collectionName && <p>{line.collectionName}</p>}
    <OrderItemOptions options={line.selectedOptions ?? []} />
    {!historical && <p>Base {money(line.basePriceMinor)} + options {money(line.selectedOptions.reduce((sum, option) => sum + option.priceDeltaMinor * option.quantity, 0))} = {money(line.unitPriceMinor)} per dish</p>}
    {historical && <p>{money(line.unitPriceMinor)} per dish</p>}
    <p>Line total: <strong>{money(line.unitPriceMinor * line.quantity)}</strong></p>
    {line.issue && <p role="alert" className="dish-unavailable">{line.issue} <a href="#/menu">Choose this dish again</a>, then remove this cart line.</p>}
  </div>;
}
