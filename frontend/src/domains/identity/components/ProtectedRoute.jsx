import { useAuth } from '../model/AuthContext';
import LoginPage from '../pages/LoginPage';
export default function ProtectedRoute({ roles, children }) {
  const { user, loading, logout } = useAuth();
  if (loading) return <main className="staff-menu page-width"><p role="status">Checking your staff session…</p></main>;
  if (!user) return <LoginPage />;
  if (roles && !roles.includes(user.role)) return <main className="staff-menu page-width"><h1>Access restricted</h1><p>This dashboard is not available for your staff role.</p><a href="#/staff">Staff home</a><button onClick={logout}>Sign out</button></main>;
  return children;
}
