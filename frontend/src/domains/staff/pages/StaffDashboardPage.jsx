import { useAuth } from '../../identity/model/AuthContext';

export default function StaffDashboardPage() {
  const { user } = useAuth();
  return <main className="staff-overview">
    <p className="staff-kicker">OVERVIEW</p><h1>Welcome, {user.username}.</h1>
    <p className="staff-intro">Your restaurant workspace, ready for the day.</p>
    <section className="staff-shell-welcome" aria-labelledby="workspace-title"><span className="staff-shell-mark" aria-hidden="true">NT</span><h2 id="workspace-title">You’re signed in.</h2><p>Use the workspace navigation to open the tools available to your role.</p><p>This overview is ready for the next step. Dashboard content will be added here.</p></section>
  </main>;
}
