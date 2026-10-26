import OrderMonitorStatus from '../shared/staff-orders/components/OrderMonitorStatus';
import {OrdersRuntimeProvider} from '../shared/staff-orders/runtime';
import useOrdersController from '../shared/staff-orders/hooks/useOrdersController';
import AudioSettings from '../shared/staff-orders/components/AudioSettings';
import * as orderApi from '../domains/ordering/api/orderApi';
import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../domains/identity/model/AuthContext';
import { staffIdentityContext } from '../domains/identity/api/identityApi';
import ReservationAdminPage from '../domains/reservation/pages/ReservationAdminPage';
import OrderBoard, { Summaries } from './features/orders/OrderBoard';
import OrderDetails from './features/orders/OrderDetails';
import { printingEnabled } from './features/printing/epos';
import PrinterSettings from './features/printing/PrinterSettings';
import { readPreference, writePreference } from './preferences';
import { registerStaffWorker } from './pwa/register';
import Dialog from './components/Dialog';
import Icon from './components/Icon';
import { orderTime } from './features/orders/presentation';

export default function StaffApp() {
  const auth=useAuth(),{user}=auth,context=staffIdentityContext();
  // Clear all orders, dialogs, drafts, and alert state when the principal/session changes.
  return <OrdersRuntimeProvider value={{...auth,identity:staffIdentityContext,api:orderApi,readPreference,writePreference}}><Workspace key={`${user.id}:${user.role}:${context?.sessionId || 'unknown'}`} /></OrdersRuntimeProvider>;
}
function Workspace() {
  const controller=useOrdersController();
  const { user, authorization, logout } = useAuth();
  const [page, setPage] = useState(() => location.hash.slice(2) || 'orders');
  const [expanded, setExpanded] = useState(() => readPreference('sidebar', true));
  const [drawer, setDrawer] = useState(false), [selectedOrder, setSelectedOrder] = useState(null), [manage, setManage] = useState(false);
  const [update,setUpdate]=useState(null),[signingOut,setSigningOut]=useState(false);
  const content=useRef(null),toggle=useRef(null);
  const {monitor,settings,setSettings,config,setConfig,audioReady,notice,setNotice,enableSound,printer}=controller;
  const admin=user.role==='ADMIN',front=user.role!=='BOH';
  useEffect(() => {
    const change = () => { setPage(location.hash.slice(2) || 'orders'); setDrawer(false); setSelectedOrder(null); setManage(false); content.current?.focus(); };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  useEffect(() => {
    let disposed = false, cleanup;
    registerStaffWorker(activate => { if (!disposed) setUpdate(() => activate); }).then(stop => { if (disposed) stop(); else cleanup = stop; }).catch(() => setNotice('App update check unavailable. Reload when connectivity returns.'));
    return () => { disposed = true; cleanup?.(); };
  }, []);
  const go = destination => { location.hash = `/${destination}`; setManage(false); setDrawer(false); };
  const links = [['orders','▤','Orders'], ...(front ? [['reservations','▦','Reservations']] : []), ['analytics','▥','Analytics'], ['printers','▣','Printer Management'], ['settings','⚙','Settings']];
  const nav = <><p className="sp-nav-title">WORKSPACE</p>{links.map(([route, icon, label]) => <a key={route} href={`#/${route}`} title={label} aria-label={label} aria-current={page === route ? 'page' : undefined}><Icon name={route} /><span className="sp-nav-label">{label}</span></a>)}<button onClick={() => { setDrawer(false); setManage(true); }} title="Manage" aria-label="Manage"><Icon name="manage" /><span className="sp-nav-label">Manage</span></button></>;
  const restricted = page === 'reservations' && !front;
  return <div className={`sp-app ${expanded ? 'sp-expanded' : 'sp-collapsed'}`}>
    <a className="staff-skip" href="#content" onClick={event => { event.preventDefault(); content.current?.focus(); }}>Skip to content</a>
    <aside className="sp-sidebar"><div className="sp-brand">NAKORN THAI<small>Staff workspace</small></div><button aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'} aria-expanded={expanded} aria-controls="sp-navigation" onClick={() => { setExpanded(!expanded); writePreference('sidebar', !expanded); }}>☰</button><nav id="sp-navigation" aria-label="Staff navigation">{nav}</nav><p className="sp-sidebar-identity">{user.username}<br />{user.role}</p></aside>
    <div className="sp-main"><header className="sp-topbar"><button ref={toggle} className="sp-drawer-toggle" aria-label="Open navigation" aria-expanded={drawer} onClick={() => setDrawer(true)}>☰</button><strong>Nakorn Thai Staff</strong><span className="sp-top-identity">{user.username} · {user.role}</span><button onClick={() => go('settings')}><Icon name="alerts" /> Alerts {monitor.unread > 0 ? `(${monitor.unread})` : ''}</button></header>
      {drawer && <Dialog title="Workspace" onClose={() => { setDrawer(false); toggle.current?.focus(); }}><nav className="sp-drawer" aria-label="Staff navigation">{nav}</nav></Dialog>}
      <OrderMonitorStatus controller={controller}/>
      {update && <div className="sp-notice">A new staff app is ready. Finish or discard order drafts before updating. <button disabled={!!selectedOrder} onClick={() => {
        const changed = () => location.reload(); navigator.serviceWorker.addEventListener('controllerchange', changed, { once: true }); update();
      }}>Update app now</button></div>}
      <main ref={content} tabIndex={-1} className="sp-content">
        {restricted ? <section><h1>Access restricted</h1><p>This page is unavailable for your role.</p><button onClick={() => go('orders')}>Orders</button></section>
          : page === 'orders' ? <OrderBoard monitor={monitor} role={user.role} onOrder={setSelectedOrder} />
          : page === 'analytics' ? <section><h1>Analytics</h1><p>Live role-filtered queue counts, from the same order monitor.</p><Summaries orders={monitor.orders} onSelect={() => go('orders')} /><p>{monitor.error ? 'Monitoring unavailable — stale data' : 'Active order monitoring'}</p><button onClick={monitor.refresh} disabled={monitor.loading}>Refresh</button></section>
          : page === 'reservations' ? <ReservationAdminPage />
          : page === 'printers' ? <PrinterSettings config={config} printer={printer} onSave={next => { if (!writePreference('printer', next)) throw new Error('Device storage unavailable; profile not saved.'); setConfig(next); }} />
          : page === 'settings' ? <section><h1>Settings</h1><AudioSettings controller={controller} version={__STAFF_VERSION__}/><div className="sp-settings-card"><h2>Install on this iPad</h2><p>Open <a href="/staff/">/staff/</a> in Safari → Share → Add to Home Screen → enable Open as Web App → Add.</p><p>Portrait and landscape are supported. Sign in and Enable Sound after opening the app.</p><p>Online access is required. Customer, payment and order responses are never cached by this app.</p></div><div className="sp-settings-card"><h2>Session & application</h2><p>{user.username} · {user.role}</p><p>App version: {__STAFF_VERSION__}</p><p>Printing: {printingEnabled ? 'Experimental build; hardware verification required' : 'Disabled by build configuration'}</p><button disabled={signingOut} onClick={async () => { setSigningOut(true); try { await logout(); } finally { setSigningOut(false); } }}>Sign out</button></div></section>
          : <section><h1>Page unavailable</h1><button onClick={() => go('orders')}>Return to orders</button></section>}
      </main>
      <footer className="sp-footer"><button aria-current={page === 'orders' ? 'page' : undefined} onClick={() => go('orders')}><Icon name="home" /><span>Home</span></button><button aria-current={page === 'analytics' ? 'page' : undefined} onClick={() => go('analytics')}><Icon name="analytics" /><span>Analytics</span></button><button onClick={() => setManage(true)}><Icon name="manage" /><span>Manage</span></button></footer>
    </div>
    {manage && <Dialog title="Manage" onClose={() => setManage(false)}><div className="sp-manage">{links.map(([route, , label]) => <button key={route} onClick={() => go(route)}>{label}</button>)}<a href="/#/staff" target="_blank" rel="noreferrer">Full staff management ↗</a><p>Menu, staff accounts, restaurant settings and existing management workflows open in the website workspace. Timekeeping currently remains in the Android app.</p><button disabled={signingOut} onClick={async () => { setSigningOut(true); try { await logout(); } finally { setSigningOut(false); } }}>Sign out</button></div></Dialog>}
    {selectedOrder && <OrderDetails key={selectedOrder} id={selectedOrder} onClose={() => setSelectedOrder(null)} onChanged={monitor.refresh} monitoringError={monitor.error} printer={printer} config={user.role === 'BOH' ? { ...config, layout: 'kitchen', autoPrint: false } : config} canPrint={printingEnabled && !!config.endpoint} />}
  </div>;
}
