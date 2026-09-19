import { allowMenuNavigation, hasMenuEdits } from '../domains/menu/model/menuAdminNavigation';
import RestaurantSchedulePage from '../domains/restaurant/pages/RestaurantSchedulePage';
import OrderTrackingPage from '../domains/ordering/pages/OrderTrackingPage';
import FunctionsPage from '../website/pages/FunctionsPage';
import FunctionEnquiriesPage from '../domains/staff/pages/FunctionEnquiriesPage';
import ReservationPage from '../domains/reservation/pages/ReservationPage';
import ReservationAdminPage from '../domains/reservation/pages/ReservationAdminPage';
import ProtectedRoute from '../domains/identity/components/ProtectedRoute';
import UsersPage from '../domains/identity/pages/UsersPage';
import CheckoutPage from '../domains/ordering/pages/CheckoutPage';
import OrderConfirmationPage from '../domains/ordering/pages/OrderConfirmationPage';
import StaffDashboardPage from '../domains/staff/pages/StaffDashboardPage';
import StaffOrdersPage from '../domains/staff/pages/StaffOrdersPage';
import KitchenDashboardPage from '../domains/staff/pages/KitchenDashboardPage';
import { useEffect, useState } from 'react';
import MenuPage from '../domains/menu/pages/MenuPage';
import HomePage from '../website/pages/HomePage';
import StaffMenuPage from '../domains/staff/pages/StaffMenuPage';

export default function AppRouter() {
  const [hash, setHash] = useState(window.location.hash.split('?')[0]);
  useEffect(() => {
    let currentUrl = window.location.href;
    let approvedUrl = null;
    let position = window.history.state?.menuPosition ?? 0;
    let restoring = false;
    window.history.replaceState({ ...window.history.state, menuPosition: position }, '');
    const beforeLink = event => {
      const link = event.target.closest?.('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || link.target === '_blank') return;
      if (link.href === window.location.href) return;
      if (!allowMenuNavigation()) event.preventDefault();
      else approvedUrl = link.href;
    };
    const navigate = () => {
      if (restoring && window.location.href === currentUrl) { restoring = false; return; }
      const targetPosition = window.history.state?.menuPosition;
      if (approvedUrl !== window.location.href && !allowMenuNavigation()) {
        // Undo Back/Forward without replacing the entry the user may revisit.
        if (Number.isInteger(targetPosition) && targetPosition !== position) {
          restoring = true; window.history.go(position - targetPosition);
        } else window.history.replaceState({ ...window.history.state, menuPosition: position }, '', currentUrl);
        return;
      }
      position = Number.isInteger(targetPosition) ? targetPosition : position + 1;
      window.history.replaceState({ ...window.history.state, menuPosition: position }, '');
      approvedUrl = null; currentUrl = window.location.href;
      setHash(window.location.hash.split('?')[0]);
    };
    const beforeUnload = event => { if (hasMenuEdits()) { event.preventDefault(); event.returnValue = ''; } };
    document.addEventListener('click', beforeLink);
    window.addEventListener('beforeunload', beforeUnload);
    window.addEventListener('hashchange', navigate);
    return () => { window.removeEventListener('hashchange', navigate); document.removeEventListener('click', beforeLink); window.removeEventListener('beforeunload', beforeUnload); };
  }, []);
  useEffect(() => {
    if (hash.startsWith('#/')) window.scrollTo(0, 0);
    else if (!hash.startsWith('#/')) document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);
  if (hash === '#/functions') return <FunctionsPage />;
  if (hash === '#/staff/functions') return <ProtectedRoute roles={['ADMIN','FOH']}><FunctionEnquiriesPage /></ProtectedRoute>;
  if (hash === '#/reservations') return <ReservationPage />;
  if (hash === '#/staff/reservations') return <ProtectedRoute roles={['ADMIN','FOH']}><ReservationAdminPage /></ProtectedRoute>;
  if (hash === '#/menu') return <MenuPage />;
  if (hash === '#/track-order') return <OrderTrackingPage />;
  if (hash === '#/checkout') return <CheckoutPage />;
  if (hash === '#/order-confirmation') return <OrderConfirmationPage />;
  if (hash === '#/staff/restaurant') return <ProtectedRoute roles={['ADMIN']}><RestaurantSchedulePage /></ProtectedRoute>;
  if (hash === '#/staff/users') return <ProtectedRoute roles={['ADMIN']}><UsersPage /></ProtectedRoute>;
  if (hash === '#/staff/menu' || hash.startsWith('#/staff/menu/')) return <ProtectedRoute roles={['ADMIN']}><StaffMenuPage hash={hash} /></ProtectedRoute>;
  if (hash === '#/staff/foh') return <ProtectedRoute roles={['ADMIN', 'FOH']}><StaffOrdersPage /></ProtectedRoute>;
  if (hash === '#/staff/kitchen') return <ProtectedRoute roles={['ADMIN', 'BOH']}><KitchenDashboardPage /></ProtectedRoute>;
  if (hash.startsWith('#/staff')) return <ProtectedRoute><StaffDashboardPage /></ProtectedRoute>;
  return <HomePage />;
}
