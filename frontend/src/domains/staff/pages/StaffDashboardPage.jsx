export default function StaffDashboardPage() {
  return <main className="staff-menu page-width"><a href="#home">Restaurant website</a><h1>Restaurant staff</h1>
    <div className="order-queue"><a className="order-panel" href="#/staff/foh"><h2>Front of house</h2><p>Confirm orders, arrange pickup and record collection.</p></a>
    <a className="order-panel" href="#/staff/kitchen"><h2>Kitchen</h2><p>Prepare accepted orders and mark them ready.</p></a>
    <a className="order-panel" href="#/staff/menu"><h2>Menu administration</h2><p>Manage dishes, prices and photographs.</p></a></div>
  </main>;
}
