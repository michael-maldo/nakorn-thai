import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider, useAuth } from '../domains/identity/model/AuthContext';
import LoginForm from '../domains/identity/components/LoginForm';
import StaffApp from './StaffApp';
import '../styles/variables.css';
import '../styles/globals.css';
import '../styles/responsive.css';
import '../styles/staff.css';
import './staff.css';
function StaffAccess() {
  const { user, loading } = useAuth();
  if (loading) return <main className="sp-login"><p role="status">Checking your staff session…</p></main>;
  if (!user) return <main className="sp-login staff-login-page"><h1>NAKORN THAI</h1><p>Staff workspace</p><LoginForm /><a href="/">Restaurant website</a></main>;
  if (!user.enabled || !['ADMIN','FOH','BOH'].includes(user.role)) return <main className="sp-login"><h1>Access restricted</h1></main>;
  return <StaffApp />;
}
createRoot(document.getElementById('root')).render(<StrictMode><AuthProvider><StaffAccess /></AuthProvider></StrictMode>);
