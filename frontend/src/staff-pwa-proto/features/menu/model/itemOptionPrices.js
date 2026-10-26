// Convert decimal AUD input without floating-point rounding or blank-to-zero coercion.
export function optionPriceMinor(value) {
  const text = String(value);
  if (!/^\d{1,7}(\.\d{1,2})?$/.test(text)) throw new Error('Enter an option price from $0 to $9,999,999.99 with at most two decimal places.');
  const [dollars, cents = ''] = text.split('.');
  return Number(dollars) * 100 + Number(cents.padEnd(2, '0'));
}
export function optionPriceDraft(group, assignment) {
  return Object.fromEntries(group.options.map(option => {
    const price = assignment?.data.prices.find(price => price.optionId === option.id);
    return [option.id, price ? (price.priceDeltaMinor / 100).toFixed(2) : ''];
  }));
}
export function itemOptionAssignment(group, resource, draft) {
  return {
    minSelections: Number(draft.minSelections), maxSelections: Number(draft.maxSelections),
    displayOrder: Number(draft.displayOrder), version: resource?.version ?? null, groupVersion: group.group.version,
    prices: Object.entries(draft.prices).filter(([, value]) => value !== '').map(([optionId, value]) => ({ optionId, priceDeltaMinor: optionPriceMinor(value) })),
  };
}
