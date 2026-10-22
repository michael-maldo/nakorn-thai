import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../domains/identity/model/AuthContext';
import { staffIdentityContext } from '../domains/identity/api/identityApi';
import ReservationAdminPage from '../domains/reservation/pages/ReservationAdminPage';
import OrderBoard, { Summaries } from './features/orders/OrderBoard';
import OrderDetails from './features/orders/OrderDetails';
import useOrderMonitor from './features/orders/useOrderMonitor';
import { alertDefaults, normalizeAlertSettings, createSoundEngine, readAudioDiagnostics } from './features/notifications/alerts';
import { createPrinter, printerDefaults, printingEnabled } from './features/printing/epos';
import PrinterSettings from './features/printing/PrinterSettings';
import { readPreference, writePreference } from './preferences';
import { registerStaffWorker } from './pwa/register';
import Dialog from './components/Dialog';
import Icon from './components/Icon';
import { orderTime } from './features/orders/presentation';

export default function StaffApp() {
  const { user } = useAuth(), context = staffIdentityContext();
  // Clear all orders, dialogs, drafts, and alert state when the principal/session changes.
  return <Workspace key={`${user.id}:${user.role}:${context?.sessionId || 'unknown'}`} />;
}
function Workspace() {
  const { user, authorization, logout } = useAuth();
  const [page, setPage] = useState(() => location.hash.slice(2) || 'orders');
  const [expanded, setExpanded] = useState(() => readPreference('sidebar', true));
  const [drawer, setDrawer] = useState(false), [selectedOrder, setSelectedOrder] = useState(null), [manage, setManage] = useState(false);
  const [settings, setSettings] = useState(() => normalizeAlertSettings(readPreference('alerts', alertDefaults)));
  const [config, setConfig] = useState(() => ({ ...printerDefaults, ...readPreference('printer', {}) }));
  const [audioReady, setAudioReady] = useState(false), [notice, setNotice] = useState(''), [update, setUpdate] = useState(null);
  const [signingOut, setSigningOut] = useState(false);
  const [audioDiagnostics, setAudioDiagnostics] = useState(null);
  const settingsRef = useRef(settings); settingsRef.current = settings;
  const sound = useRef(null), printer = useRef(createPrinter()), content = useRef(null), toggle = useRef(null);
  const admin = user.role === 'ADMIN', front = user.role !== 'BOH';
  useEffect(() => {
    sound.current = createSoundEngine(window.AudioContext || window.webkitAudioContext, setAudioReady);
    return () => { sound.current?.close(); sound.current = null; };
  }, []);
  const monitor = useOrderMonitor(authorization, user.role, ids => {
    // Queue state is already published. Never await audio in the polling path.
    const engine = sound.current;
    engine?.notifyPending(ids, () => settingsRef.current).then(played => {
      if (!played && sound.current === engine && !engine.ready()) setAudioReady(false);
    });
  });
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
  async function enableSound() {
    const engine = sound.current;
    try {
      if (!await engine.enable() || sound.current !== engine) return;
      if (!engine.play(settingsRef.current)) throw new Error('Sound could not play. Tap to Enable Sound again.');
      setNotice('Test sound scheduled. Confirm that you heard it using the iPad volume controls.');
    }
    catch (error) { if (sound.current === engine) { setAudioReady(false); setNotice(error.message); } }
  }
  const go = destination => { location.hash = `/${destination}`; setManage(false); setDrawer(false); };
  const links = [['orders','▤','Orders'], ...(front ? [['reservations','▦','Reservations']] : []), ['analytics','▥','Analytics'], ...(admin ? [['printers','▣','Printer Management']] : []), ['settings','⚙','Settings']];
  const nav = <><p className="sp-nav-title">WORKSPACE</p>{links.map(([route, icon, label]) => <a key={route} href={`#/${route}`} title={label} aria-label={label} aria-current={page === route ? 'page' : undefined}><Icon name={route} /><span className="sp-nav-label">{label}</span></a>)}<button onClick={() => { setDrawer(false); setManage(true); }} title="Manage" aria-label="Manage"><Icon name="manage" /><span className="sp-nav-label">Manage</span></button></>;
  const restricted = page === 'printers' && !admin || page === 'reservations' && !front;
  return <div className={`sp-app ${expanded ? 'sp-expanded' : 'sp-collapsed'}`}>
    <a className="staff-skip" href="#content" onClick={event => { event.preventDefault(); content.current?.focus(); }}>Skip to content</a>
    <aside className="sp-sidebar"><div className="sp-brand">NAKORN THAI<small>Staff workspace</small></div><button aria-label={expanded ? 'Collapse sidebar' : 'Expand sidebar'} aria-expanded={expanded} aria-controls="sp-navigation" onClick={() => { setExpanded(!expanded); writePreference('sidebar', !expanded); }}>☰</button><nav id="sp-navigation" aria-label="Staff navigation">{nav}</nav><p className="sp-sidebar-identity">{user.username}<br />{user.role}</p></aside>
    <div className="sp-main"><header className="sp-topbar"><button ref={toggle} className="sp-drawer-toggle" aria-label="Open navigation" aria-expanded={drawer} onClick={() => setDrawer(true)}>☰</button><strong>Nakorn Thai Staff</strong><span className="sp-top-identity">{user.username} · {user.role}</span><button onClick={() => go('settings')}><Icon name="alerts" /> Alerts {monitor.unread > 0 ? `(${monitor.unread})` : ''}</button></header>
      {drawer && <Dialog title="Workspace" onClose={() => { setDrawer(false); toggle.current?.focus(); }}><nav className="sp-drawer" aria-label="Staff navigation">{nav}</nav></Dialog>}
      <div className="sp-monitor" role="status"><span className={monitor.error || !monitor.foreground ? 'sp-dot sp-dot-warning' : 'sp-dot'} />{!monitor.foreground ? 'Monitoring paused while hidden' : monitor.error ? 'Monitoring unavailable · stale data' : monitor.lastSync ? 'Foreground monitoring' : 'Waiting for first synchronization'}{monitor.lastSync && ` · Last sync ${orderTime(monitor.lastSync)} (Melbourne)`}<span className="sp-sound-status">{audioReady ? 'Sound enabled' : 'Sound not enabled'}</span><button onClick={enableSound}>{audioReady ? 'Test Sound' : 'Tap to Enable Sound'}</button></div>
      {monitor.unread > 0 && <div className="sp-alert" role="status">{monitor.unread} new pending order(s). Acknowledging does not accept an order.<button onClick={monitor.acknowledge}>Acknowledge</button></div>}
      {notice && <div className="sp-notice" role="status">{notice}<button aria-label="Dismiss notice" onClick={() => setNotice('')}>✕</button></div>}
      {update && <div className="sp-notice">A new staff app is ready. Finish or discard order drafts before updating. <button disabled={!!selectedOrder} onClick={() => {
        const changed = () => location.reload(); navigator.serviceWorker.addEventListener('controllerchange', changed, { once: true }); update();
      }}>Update app now</button></div>}
      <main ref={content} tabIndex={-1} className="sp-content">
        {restricted ? <section><h1>Access restricted</h1><p>This page is unavailable for your role.</p><button onClick={() => go('orders')}>Orders</button></section>
          : page === 'orders' ? <OrderBoard monitor={monitor} role={user.role} onOrder={setSelectedOrder} />
          : page === 'analytics' ? <section><h1>Analytics</h1><p>Live role-filtered queue counts, from the same order monitor.</p><Summaries orders={monitor.orders} onSelect={() => go('orders')} /><p>{monitor.error ? 'Monitoring unavailable — stale data' : 'Active order monitoring'}</p><button onClick={monitor.refresh} disabled={monitor.loading}>Refresh</button></section>
          : page === 'reservations' ? <ReservationAdminPage />
          : page === 'printers' ? <PrinterSettings config={config} printer={printer.current} onSave={next => { if (!writePreference('printer', next)) throw new Error('Device storage unavailable; profile not saved.'); setConfig(next); }} />
          : page === 'settings' ? <section><h1>Settings</h1><div className="sp-settings-card"><h2>Incoming-order alerts</h2><p>{audioReady ? 'Sound enabled for this session' : 'Tap to Enable Sound at shift start or if automatic recovery needs activation.'}</p><div className="sp-actions"><button onClick={enableSound}>Enable Sound</button><button onClick={enableSound}>Test Sound</button></div><label>Alert sound<select value={settings.sound} onChange={event => { const next = { ...settings, sound: event.target.value }; setSettings(next); if (!writePreference('alerts', next)) setNotice('Sound preference could not be saved.'); }}><option value="double">Double beep</option><option value="chime">Chime</option></select></label><label>Alert volume · {Math.round(settings.volume * 100)}%<input type="range" min="0" max="1" step="0.05" value={settings.volume} onChange={event => { const next = { ...settings, volume: Number(event.target.value) }; setSettings(next); if (!writePreference('alerts', next)) setNotice('Volume preference could not be saved.'); }} /></label><p>Keep this app visible and the iPad awake during service. The app cannot override device volume, Silent Mode, Focus or background suspension. Background push notifications are not configured.</p></div><div className="sp-settings-card"><h2>Audio diagnostics</h2><p>Local technical log only. Capture after unlocking and before tapping Enable Sound. No background alarms are supported.</p><button onClick={() => setAudioDiagnostics(readAudioDiagnostics())}>Refresh audio diagnostics</button>{audioDiagnostics && <textarea aria-label="Audio diagnostics" readOnly rows={14} style={{ width: '100%', boxSizing: 'border-box' }} value={JSON.stringify({ version: __STAFF_VERSION__, capturedAt: new Date().toISOString(), entries: audioDiagnostics }, null, 2)} />}</div><div className="sp-settings-card"><h2>Install on this iPad</h2><p>Open <a href="/staff/">/staff/</a> in Safari → Share → Add to Home Screen → enable Open as Web App → Add.</p><p>Portrait and landscape are supported. Sign in and Enable Sound after opening the app.</p><p>Online access is required. Customer, payment and order responses are never cached by this app.</p></div><div className="sp-settings-card"><h2>Session & application</h2><p>{user.username} · {user.role}</p><p>App version: {__STAFF_VERSION__}</p><p>Printing: {printingEnabled ? 'Experimental build; hardware verification required' : 'Disabled by build configuration'}</p><button disabled={signingOut} onClick={async () => { setSigningOut(true); try { await logout(); } finally { setSigningOut(false); } }}>Sign out</button></div></section>
          : <section><h1>Page unavailable</h1><button onClick={() => go('orders')}>Return to orders</button></section>}
      </main>
      <footer className="sp-footer"><button aria-current={page === 'orders' ? 'page' : undefined} onClick={() => go('orders')}><Icon name="home" /><span>Home</span></button><button aria-current={page === 'analytics' ? 'page' : undefined} onClick={() => go('analytics')}><Icon name="analytics" /><span>Analytics</span></button><button onClick={() => setManage(true)}><Icon name="manage" /><span>Manage</span></button></footer>
    </div>
    {manage && <Dialog title="Manage" onClose={() => setManage(false)}><div className="sp-manage">{links.map(([route, , label]) => <button key={route} onClick={() => go(route)}>{label}</button>)}<a href="/#/staff" target="_blank" rel="noreferrer">Full staff management ↗</a><p>Menu, staff accounts, restaurant settings and existing management workflows open in the website workspace. Timekeeping currently remains in the Android app.</p><button disabled={signingOut} onClick={async () => { setSigningOut(true); try { await logout(); } finally { setSigningOut(false); } }}>Sign out</button></div></Dialog>}
    {selectedOrder && <OrderDetails key={selectedOrder} id={selectedOrder} onClose={() => setSelectedOrder(null)} onChanged={monitor.refresh} monitoringError={monitor.error} printer={printer.current} config={user.role === 'BOH' ? { ...config, layout: 'kitchen', autoPrint: false } : config} canPrint={printingEnabled && !!config.endpoint} />}
  </div>;
}
