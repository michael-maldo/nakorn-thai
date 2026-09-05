import { useState } from 'react';
import { useAuth } from '../model/AuthContext';
export default function LoginForm() {
  const { login } = useAuth();
  const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); const form = event.currentTarget; const data = new FormData(form);
    setBusy(true); setError('');
    try { await login(data.get('username'), data.get('password')); form.reset(); }
    catch (failure) { setError(failure.message); } finally { setBusy(false); }
  }
  return <form className="staff-panel staff-login" onSubmit={submit}>
    <h2>Staff sign in</h2><p>Sign in once to access your restaurant dashboards.</p>
    {error && <p role="alert" className="staff-error">{error}</p>}
    <label>Username<input name="username" required maxLength={50} autoComplete="username" disabled={busy} /></label>
    <label>Password<input name="password" type="password" required maxLength={72} autoComplete="current-password" disabled={busy} /></label>
    <button className="button button-primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
  </form>;
}
