import ReadOnlyOrderTrackingPage from '../domains/ordering/pages/ReadOnlyOrderTrackingPage';
import {trackingToken,orderLink} from '../domains/ordering/model/orderLinks.js';
import IntegrationSettingsPage from '../domains/restaurant/pages/IntegrationSettingsPage';
import HomepageSettingsPage from '../domains/menu/pages/HomepageSettingsPage';
import OrdersAdminPage from '../domains/ordering/pages/OrdersAdminPage';
import OnlineOrderingPage from '../domains/restaurant/pages/OnlineOrderingPage';
import StaffShell from '../domains/staff/components/StaffShell';
import StaffDashboardPage from '../domains/staff/pages/StaffDashboardPage';
import { allowMenuNavigation, hasMenuEdits, cancelPendingMenuNavigation, createMenuNavigationApproval } from '../domains/menu/model/menuAdminNavigation';
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
import { useEffect, useState } from 'react';
import MenuPage from '../domains/menu/pages/MenuPage';
import HomePage from '../website/pages/HomePage';
import StaffMenuPage from '../domains/staff/pages/StaffMenuPage';

export default function AppRouter() {
  const [location, setLocation] = useState(window.location.hash);
  const hash=location.split('?')[0], linked=orderLink(location);
  useEffect(() => {
    let currentUrl = window.location.href;
    let approvedUrl = null;
    let position = window.history.state?.menuPosition ?? 0;
    let restoring = false;
    let pendingTransition = null;
    let mounted = true;
    let observedUrl = currentUrl, observedPosition = position;
    const resume = (targetUrl, targetPosition, ticket) => {
      ticket ??= createMenuNavigationApproval();
      pendingTransition = { targetUrl, targetPosition, ticket };
      if (!restoring) finishTransition();
      return true;
    };
    const finishTransition = () => {
      const transition = pendingTransition;
      pendingTransition = null;
      if (!mounted || !transition?.ticket.complete()) return;
      approvedUrl = transition.targetUrl;
      if (Number.isInteger(transition.targetPosition) && transition.targetPosition !== position) {
        window.history.go(transition.targetPosition - position);
      } else window.location.assign(transition.targetUrl);
    };
    window.history.replaceState({ ...window.history.state, menuPosition: position }, '');
    const beforeLink = event => {
      const link = event.target.closest?.('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || link.target === '_blank') return;
      if (link.href === window.location.href) return;
      pendingTransition?.ticket.cancel(); pendingTransition = null;
      approvedUrl = null;
      if (!allowMenuNavigation(undefined, undefined, ticket => resume(link.href, undefined, ticket))) event.preventDefault();
      else if (restoring) { event.preventDefault(); resume(link.href); }
      else approvedUrl = link.href;
    };
    const navigate = () => {
      const targetPosition = window.history.state?.menuPosition;
      const targetUrl = window.location.href;
      // A traversal emits popstate and often hashchange. Process each physical entry once.
      if (targetUrl === observedUrl && targetPosition === observedPosition) return;
      observedUrl = targetUrl; observedPosition = targetPosition;
      if (restoring && targetUrl === currentUrl && targetPosition === position) {
        restoring = false; finishTransition(); return;
      }
      pendingTransition?.ticket.cancel(); pendingTransition = null;
      const allowed = approvedUrl === targetUrl || allowMenuNavigation(undefined, undefined, ticket => resume(targetUrl, targetPosition, ticket));
      if (!allowed || restoring) {
        if (allowed) resume(targetUrl, targetPosition);
        // A new native traversal can interrupt restoration. Re-target the latest
        // observed entry to the editor, without ever resuming approval before arrival.
        // Undo Back/Forward without replacing the entry the user may revisit.
        if (Number.isInteger(targetPosition) && targetPosition !== position) {
          restoring = true; window.history.go(position - targetPosition);
        } else if (!restoring) window.history.replaceState({ ...window.history.state, menuPosition: position }, '', currentUrl);
        return;
      }
      position = Number.isInteger(targetPosition) ? targetPosition : position + 1;
      window.history.replaceState({ ...window.history.state, menuPosition: position }, '');
      approvedUrl = null; currentUrl = window.location.href;
      observedUrl = currentUrl; observedPosition = position;
      setLocation(window.location.hash);
    };
    const beforeUnload = event => { if (hasMenuEdits()) { event.preventDefault(); event.returnValue = ''; } };
    document.addEventListener('click', beforeLink);
    window.addEventListener('beforeunload', beforeUnload);
    window.addEventListener('hashchange', navigate);
    window.addEventListener('popstate', navigate);
    return () => { mounted = false; pendingTransition?.ticket.cancel(); pendingTransition = null; cancelPendingMenuNavigation(); window.removeEventListener('hashchange', navigate); window.removeEventListener('popstate', navigate); document.removeEventListener('click', beforeLink); window.removeEventListener('beforeunload', beforeUnload); };
  }, []);
  useEffect(() => {
    if (hash.startsWith('#/')) window.scrollTo(0, 0);
    else if (!hash.startsWith('#/')) document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);
  const staffPage = (page, roles) => <ProtectedRoute roles={roles}><StaffShell hash={hash}>{page}</StaffShell></ProtectedRoute>;
  if (hash === '#/staff' || hash === '#/staff/') return staffPage(<StaffDashboardPage />);
  if (hash === '#/functions') return <FunctionsPage />;
  if (hash === '#/staff/functions') return staffPage(<FunctionEnquiriesPage />, ['ADMIN','FOH']);
  if (hash === '#/reservations') return <ReservationPage />;
  if (hash === '#/staff/reservations') return staffPage(<ReservationAdminPage />, ['ADMIN','FOH']);
  if (hash === '#/menu') return <MenuPage />;
  if (hash === '#/recover-order' || hash === '#/track-order' && new URLSearchParams(location.split('?')[1]||'').has('order') && !new URLSearchParams(location.split('?')[1]||'').has('t')) return <OrderTrackingPage key={location} linked={linked} />;
  if (hash === '#/track-order') return <ReadOnlyOrderTrackingPage key={location} token={trackingToken(location)} />;
  if (hash === '#/checkout') return <CheckoutPage />;
  if (hash === '#/order-confirmation') return <OrderConfirmationPage />;
  if (hash === '#/staff/homepage') return staffPage(<HomepageSettingsPage />, ['ADMIN']);
  if (hash === '#/staff/orders') return staffPage(<OrdersAdminPage key={location} linked={linked} />, ['ADMIN', 'FOH', 'BOH']);
  if (hash === '#/staff/ordering') return staffPage(<OnlineOrderingPage />, ['ADMIN', 'FOH']);
  if (hash === '#/staff/settings') return staffPage(<IntegrationSettingsPage />, ['ADMIN']);
  if (hash === '#/staff/restaurant') return staffPage(<RestaurantSchedulePage />, ['ADMIN']);
  if (hash === '#/staff/users') return staffPage(<UsersPage />, ['ADMIN']);
  if (hash === '#/staff/menu' || hash.startsWith('#/staff/menu/')) return staffPage(<StaffMenuPage hash={hash} />, ['ADMIN']);
  return <HomePage />;
}
