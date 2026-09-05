import { createContext, useContext, useEffect, useState } from 'react';
import * as identity from '../api/identityApi';
const AuthContext = createContext(null);
export function AuthProvider({ children }) {
  const [session, setSession] = useState(identity.identityState);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    const unsubscribe = identity.subscribeIdentity(setSession);
    let active = true;
    identity.refreshAccess().catch(() => {}).finally(() => { if (active) setLoading(false); });
    return () => { active = false; unsubscribe(); };
  }, []);
  useEffect(() => {
    if (!session.accessToken) return;
    const timer = setTimeout(() => identity.refreshAccess().catch(() => {}), Math.max(1000, Date.parse(session.expiresAt) - Date.now() - 60000));
    return () => clearTimeout(timer);
  }, [session.accessToken, session.expiresAt]);
  async function logout() {
    setError('');
    try { await identity.logout(); } catch { setError('Sign out could not reach the server. Please retry.'); }
  }
  return <AuthContext.Provider value={{ user: session.user, authorization: session.accessToken ? `Bearer ${session.accessToken}` : '', loading, login: identity.login, logout }}>
    {error && <p role="alert" className="staff-error">{error}</p>}{children}
  </AuthContext.Provider>;
}
export const useAuth = () => useContext(AuthContext);
