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
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const navigate = () => setHash(window.location.hash);
    window.addEventListener('hashchange', navigate);
    return () => window.removeEventListener('hashchange', navigate);
  }, []);
  useEffect(() => {
    if (hash.startsWith('#/')) window.scrollTo(0, 0);
    else if (!hash.startsWith('#/')) document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);
  if (hash === '#/menu') return <MenuPage />;
  if (hash === '#/checkout') return <CheckoutPage />;
  if (hash === '#/order-confirmation') return <OrderConfirmationPage />;
  if (hash === '#/staff/menu') return <StaffMenuPage />;
  if (hash === '#/staff/foh') return <StaffOrdersPage />;
  if (hash === '#/staff/kitchen') return <KitchenDashboardPage />;
  if (hash.startsWith('#/staff')) return <StaffDashboardPage />;
  return <HomePage />;
}
