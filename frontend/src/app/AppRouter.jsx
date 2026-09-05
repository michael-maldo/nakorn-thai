import { useEffect, useState } from 'react';
import HomePage from '../website/pages/HomePage';
import StaffMenuPage from '../domains/staff/pages/StaffMenuPage';

export default function AppRouter() {
  const [hash, setHash] = useState(window.location.hash);
  useEffect(() => {
    const navigate = () => setHash(window.location.hash);
    window.addEventListener('hashchange', navigate);
    return () => window.removeEventListener('hashchange', navigate);
  }, []);
  return hash.startsWith('#/staff') ? <StaffMenuPage /> : <HomePage />;
}
