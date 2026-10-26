export const routes = [
  { path: '#/staff', label: 'Home', icon: 'home', roles: ['ADMIN','FOH','BOH'] },
  { path: '#/staff/orders', label: 'Orders', icon: 'orders', roles: ['ADMIN','FOH','BOH'] },
  { path: '#/staff/reservations', label: 'Reservations', icon: 'calendar', roles: ['ADMIN','FOH'] },
  { path: '#/staff/functions', label: 'Function enquiries', icon: 'people', roles: ['ADMIN','FOH'] },
  { path: '#/staff/ordering', label: 'Online ordering', icon: 'switch', roles: ['ADMIN','FOH'] },
  { path: '#/staff/menu', label: 'Menu', icon: 'menu', roles: ['ADMIN'] },
  { path: '#/staff/homepage', label: 'Home page collections', icon: 'home', roles: ['ADMIN'] },
  { path: '#/staff/restaurant', label: 'Restaurant schedule', icon: 'calendar', roles: ['ADMIN'] },
  { path: '#/staff/users', label: 'Staff accounts', icon: 'people', roles: ['ADMIN'] },
  { path: '#/staff/settings', label: 'Settings & integrations', icon: 'settings', roles: ['ADMIN'] },
];
export function resolveRoute(hash) {
  const path = (hash || '#/staff').split('?')[0].replace(/\/$/, '');
  return routes.find(route => path === route.path || route.path === '#/staff/menu' && path.startsWith(route.path + '/')) || null;
}
export const visibleRoutes = role => routes.filter(route => route.roles.includes(role));
export const canAccess = (hash, role) => !!resolveRoute(hash)?.roles.includes(role);
