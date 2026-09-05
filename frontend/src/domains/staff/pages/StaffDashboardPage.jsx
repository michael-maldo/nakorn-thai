import { useAuth } from '../../identity/model/AuthContext';
export default function StaffDashboardPage() {
  const { user, logout } = useAuth();
  return <main className="staff-menu page-width"><a href="#home">Restaurant website</a><header className="staff-heading"><div><h1>Restaurant staff</h1><p>Signed in as {user.username} · {user.role}</p></div><button onClick={logout}>Sign out</button></header>
    <div className="order-queue">
      {['ADMIN','FOH'].includes(user.role) && <a className="order-panel" href="#/staff/reservations"><h2>Reservations</h2><p>Review booking requests and manage guest arrivals.</p></a>}
      {['ADMIN','FOH'].includes(user.role) && <a className="order-panel" href="#/staff/foh"><h2>Front of house</h2><p>Confirm orders, arrange pickup and record collection.</p></a>}
      {['ADMIN','BOH'].includes(user.role) && <a className="order-panel" href="#/staff/kitchen"><h2>Kitchen</h2><p>Prepare accepted orders and mark them ready.</p></a>}
      {user.role === 'ADMIN' && <><a className="order-panel" href="#/staff/menu"><h2>Menu administration</h2><p>Manage dishes, prices and photographs.</p></a><a className="order-panel" href="#/staff/users"><h2>Staff accounts</h2><p>Create accounts, set roles and reset passwords.</p></a></>}
    </div>
  </main>;
}
