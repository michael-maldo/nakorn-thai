import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { allowMenuNavigation } from '../../menu/model/menuAdminNavigation';

const links = [
  { href: '#/staff', label: 'Overview' },
  { href: '#/staff/menu', label: 'Menu', roles: ['ADMIN'] },
  { href: '#/staff/reservations', label: 'Reservations', roles: ['ADMIN', 'FOH'] },
  { href: '#/staff/functions', label: 'Function enquiries', roles: ['ADMIN', 'FOH'] },
  { href: '#/staff/restaurant', label: 'Restaurant settings', roles: ['ADMIN'] },
  { href: '#/staff/users', label: 'Staff accounts', roles: ['ADMIN'] },
];

export default function StaffShell({ hash, children }) {
  const { user, logout } = useAuth();
  const [expanded, setExpanded] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const content = useRef(null);
  const toggle = useRef(null);
  useEffect(() => { setExpanded(false); content.current?.focus(); }, [hash]);
  async function signOut() {
    if (!allowMenuNavigation()) return;
    setSigningOut(true);
    try { await logout(); } finally { setSigningOut(false); }
  }
  return <div className="staff-workspace">
    <a className="staff-skip" href="#staff-content" onClick={event => { event.preventDefault(); content.current?.focus(); }}>Skip to content</a>
    <header className="staff-topbar">
      <a className="staff-brand" href="#/staff">NAKORN THAI<span>Staff workspace</span></a>
      <nav aria-label="Staff account" className="staff-account-nav"><a href="#home">Restaurant website</a><span className="staff-user">{user.username}<small>{user.role}</small></span><button type="button" disabled={signingOut} onClick={signOut}>{signingOut ? 'Signing out…' : 'Sign out'}</button></nav>
    </header>
    <div className="staff-workspace-body">
      <aside className="staff-sidebar" onKeyDown={event => { if (event.key === 'Escape' && expanded) { setExpanded(false); toggle.current?.focus(); } }}>
        <button ref={toggle} type="button" className="staff-nav-toggle" aria-expanded={expanded} aria-controls="staff-navigation" onClick={() => setExpanded(value => !value)}>Workspace navigation <span aria-hidden="true">{expanded ? '−' : '+'}</span></button>
        <nav id="staff-navigation" aria-label="Staff navigation" className={expanded ? 'is-expanded' : ''}>
          <p className="staff-nav-label">WORKSPACE</p>
          {links.filter(link => !link.roles || link.roles.includes(user.role)).map(link => <a key={link.href} href={link.href} aria-current={(hash.replace(/\/$/, '') === link.href || (link.href !== '#/staff' && hash.startsWith(`${link.href}/`))) ? 'page' : undefined}>{link.label}</a>)}
        </nav>
      </aside>
      <div className="staff-workspace-content" id="staff-content" tabIndex={-1} ref={content}>{children}</div>
    </div>
  </div>;
}
