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
    if (hash === '#/menu') window.scrollTo(0, 0);
    else if (!hash.startsWith('#/')) document.getElementById(hash.slice(1))?.scrollIntoView();
  }, [hash]);
  if (hash === '#/menu') return <MenuPage />;
  return hash.startsWith('#/staff') ? <StaffMenuPage /> : <HomePage />;
}
