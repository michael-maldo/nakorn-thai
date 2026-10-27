// An absent result means acceptance failed, was uncertain, or its authoritative reload failed.
// Refresh/polling never invokes this function. A manual reprint is a separate operator action.
export async function printConfirmedAcceptance({ order, target, enabled, deviceEnabled, autoPrint, current, send }) {
  if (!enabled || deviceEnabled !== true || !autoPrint || target !== 'ACCEPTED' || order?.status !== 'ACCEPTED' || !current()) return false;
  await send(order);
  return true;
}
