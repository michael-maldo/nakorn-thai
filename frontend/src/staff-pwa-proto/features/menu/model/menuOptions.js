export function normalizeSelectedOptions(selections = []) {
  if (!Array.isArray(selections) || selections.length > 100) throw new Error('Check your option selections.');
  const seen = new Set();
  return selections.map(({ optionId, quantity }) => {
    if (typeof optionId !== 'string' || !optionId || !Number.isInteger(quantity) || quantity < 1 || quantity > 20
      || seen.has(optionId.toLowerCase())) throw new Error('Check your option selections.');
    optionId = optionId.toLowerCase();
    seen.add(optionId);
    return { optionId, quantity };
  }).sort((a, b) => a.optionId < b.optionId ? -1 : a.optionId > b.optionId ? 1 : 0);
}

// UX validation only. Checkout must refresh and the server validates again.
export function evaluateOptions(groups = [], selections = []) {
  let normalized;
  try { normalized = normalizeSelectedOptions(selections); }
  catch (error) { return { valid: false, message: error.message, selectedOptions: [], deltaMinor: 0 }; }
  const remaining = new Map(normalized.map((option) => [option.optionId, option.quantity]));
  const selectedOptions = [];
  let deltaMinor = 0;
  let message = '';
  for (const group of groups) {
    let count = 0;
    for (const option of group.options) {
      const quantity = remaining.get(option.id.toLowerCase());
      if (quantity === undefined) continue;
      remaining.delete(option.id.toLowerCase());
      count += quantity;
      if (!group.active || !option.available) message ||= `${option.name} is unavailable. Choose another option.`;
      if (group.selectionType === 'SINGLE' && quantity !== 1) message ||= `Choose one ${group.name} option.`;
      selectedOptions.push({ optionId: option.id.toLowerCase(), quantity, optionName: option.name,
        optionGroupName: group.name, priceDeltaMinor: option.priceDeltaMinor });
      deltaMinor += option.priceDeltaMinor * quantity;
    }
    if (count < group.minSelections) message ||= `Choose at least ${group.minSelections} from ${group.name}.`;
    if (count > group.maxSelections || (group.selectionType === 'SINGLE' && count > 1))
      message ||= `Choose no more than ${group.selectionType === 'SINGLE' ? 1 : group.maxSelections} from ${group.name}.`;
  }
  if (remaining.size) message ||= 'An option is no longer offered with this dish. Choose your options again.';
  if (!Number.isSafeInteger(deltaMinor) || deltaMinor < 0) message ||= 'This configuration cannot be priced. Please choose your options again.';
  selectedOptions.sort((a, b) => a.optionId < b.optionId ? -1 : a.optionId > b.optionId ? 1 : 0);
  return { valid: !message, message, selectedOptions, deltaMinor };
}

export function changeOptionSelection(groups, selections, groupId, optionId, quantity) {
  const group = groups.find((entry) => entry.id === groupId);
  const option = group?.options.find((entry) => entry.id === optionId);
  if (!group || !option || !Number.isInteger(quantity) || quantity < 0 || quantity > 20
    || (quantity > 0 && (!group.active || !option.available))) return selections;
  if (group.selectionType === 'SINGLE' && quantity > 1) return selections;
  const groupIds = new Set(group.options.map((entry) => entry.id));
  const next = selections.filter((entry) => entry.optionId !== optionId
    && !(group.selectionType === 'SINGLE' && groupIds.has(entry.optionId)));
  if (quantity > 0) next.push({ optionId, quantity });
  const total = next.filter((entry) => groupIds.has(entry.optionId)).reduce((sum, entry) => sum + entry.quantity, 0);
  if (total > group.maxSelections) return selections;
  return normalizeSelectedOptions(next);
}
