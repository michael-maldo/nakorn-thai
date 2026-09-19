const root = '#/staff/menu';
export const itemSections = ['overview', 'pricing', 'collections', 'images'];
export const collectionSections = ['overview', 'items', 'categories', 'availability'];
export function menuAdminHref(resource = 'items', id, section) {
  return `${root}/${resource}${id ? `/${encodeURIComponent(id)}` : ''}${section ? `/${section}` : ''}`;
}
export function parseMenuAdminRoute(hash) {
  const path = hash.split('?')[0].replace(/\/$/, '');
  if (path === root) return { resource: 'items', id: null, section: 'overview' };
  if (!path.startsWith(`${root}/`)) return null;
  const parts = path.slice(root.length + 1).split('/');
  const [resource, encodedId, section = 'overview'] = parts;
  const sections = resource === 'items' ? itemSections : resource === 'collections' ? collectionSections : [];
  if (!sections.length || parts.length > 3 || !sections.includes(section)) return null;
  let id;
  try { id = encodedId ? decodeURIComponent(encodedId) : null; } catch { return null; }
  if (id === 'new' && section !== 'overview') return null;
  return { resource, id, section };
}

// Only active menu forms register here. The existing hash router owns navigation.
const guards = new Set();
export function registerMenuGuard(readState) { guards.add(readState); return () => guards.delete(readState); }
export function hasMenuEdits() { return [...guards].some(read => { const state = read(); return state.dirty || state.busy; }); }
export function allowMenuNavigation(confirm = message => window.confirm(message), alert = message => window.alert(message)) {
  const states = [...guards].map(read => read());
  if (states.some(state => state.busy)) { alert('Please wait for the current save to finish.'); return false; }
  return !states.some(state => state.dirty) || confirm('Discard your unsaved changes and leave this view?');
}
export function availabilityLabel(result) {
  if (!result) return 'Not evaluated';
  const labels = { AVAILABLE: 'Available now', NOT_PUBLISHED: 'Not published', INACTIVE: 'Inactive', NOT_STARTED: 'Not started', ENDED: 'Ended', OUTSIDE_SCHEDULE: 'Outside collection schedule', INVALID_TIMEZONE: 'Check schedule timezone', RESTAURANT_CLOSED: 'Restaurant closed', AFTER_CUTOFF: 'Daily cutoff reached' };
  return labels[result.reason] || (result.available ? 'Available now' : 'Unavailable');
}
export const money = minor => new Intl.NumberFormat('en-AU', { style: 'currency', currency: 'AUD' }).format(minor / 100);

// Broad validity bounds are instants. Explicit UTC pickers avoid browser-local
// timezone assumptions; cutoff and schedule fields retain their existing zones.
export const instantToInput = instant => instant ? new Date(instant).toISOString().slice(0, 23) : '';
export const instantFromInput = value => value ? new Date(`${value}Z`).toISOString() : null;
