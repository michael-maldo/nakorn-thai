import { money } from '../model/cartReducer';

// Both cart display data and order snapshots have these fields. No menu lookup.
export default function OrderItemOptions({ options = [] }) {
  if (!options.length) return null;
  return <ul className="order-selected-options" aria-label="Selected options per dish">
    {options.map((option) => <li key={option.optionId}>
      {option.optionGroupName}: {option.optionName} × {option.quantity} per dish (+{money(option.priceDeltaMinor)} each)
    </li>)}
  </ul>;
}
