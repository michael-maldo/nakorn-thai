import { useEffect } from 'react';
import { StaffEmptyState, StaffFormActions, StaffStatusBadge } from '../../staff/components/StaffPresentation';
import { menuAdminHref } from '../model/menuAdminNavigation';

export function StatusBadge({ value, positive }) {
  return <StaffStatusBadge variant={positive || value === 'PUBLISHED' ? 'success' : 'neutral'}>{value?.replaceAll('_', ' ')}</StaffStatusBadge>;
}
export function EmptyState({ title, children }) {
  return <StaffEmptyState title={title}>{children}</StaffEmptyState>;
}
export function OrderField({ value, onChange }) {
  return <label>Display order<input type="number" required min={0} max={2147483647} step={1} value={value} onChange={e => onChange(e.target.value === '' ? '' : Number(e.target.value))} /><small>Lower numbers appear first.</small></label>;
}
export function FormActions({ busy, disabled, dirty, onCancel, label = 'Save changes' }) {
  return <StaffFormActions busy={busy} disabled={disabled} dirty={dirty} onCancel={onCancel} label={label} />;
}
export function ConfirmRemoval({ title, children, busy, onConfirm, onCancel }) {
  return <section className="menu-admin-confirm" aria-label={title}><h3>{title}</h3><p>{children}</p><div className="menu-admin-actions"><button type="button" className="menu-admin-danger" disabled={busy} onClick={onConfirm}>Confirm removal</button><button type="button" disabled={busy} onClick={onCancel} autoFocus>Keep it</button></div></section>;
}
export default function MenuAdminLayout({ route, title, children }) {
  useEffect(() => { document.querySelector('.menu-admin h1')?.focus(); }, []);
  return <div className="menu-admin">
    <div className="menu-admin-shell">
      <main id="menu-admin-content" tabIndex={-1}>
        <nav className="menu-admin-breadcrumbs" aria-label="Breadcrumb"><a href="#home">Restaurant website</a><span>/</span><a href={menuAdminHref()}>Menu</a>{route && <><span>/</span>{route.id ? <a href={menuAdminHref(route.resource)}>{route.resource === 'items' ? 'Items' : 'Collections'}</a> : <span>{route.resource === 'items' ? 'Items' : 'Collections'}</span>}{route.id && <><span>/</span><span aria-current="page">{title}</span></>}</>}</nav>
        <nav className="menu-admin-subnav" aria-label="Menu navigation"><a href={menuAdminHref('items')} aria-current={route?.resource === 'items' ? 'page' : undefined}>Items</a><a href={menuAdminHref('collections')} aria-current={route?.resource === 'collections' ? 'page' : undefined}>Collections</a></nav>
        {children}
      </main>
    </div>
  </div>;
}
