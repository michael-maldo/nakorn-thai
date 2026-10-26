/** Presentation only: callers retain ownership of requests and draft state. */
export function StaffPageHeading({ title, kicker, actions }) {
  return <header className="staff-page-heading"><div>{kicker && <p className="staff-kicker">{kicker}</p>}<h1 tabIndex={-1}>{title}</h1></div>{actions && <div className="staff-page-actions">{actions}</div>}</header>;
}

export function StaffNotice({ variant = 'error', children }) {
  return <div className={`staff-notice staff-notice-${variant}`} role={variant === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function StaffLoading({ children = 'Loading…' }) {
  return <p className="staff-loading" role="status">{children}</p>;
}

export function StaffStatusBadge({ variant = 'neutral', children }) {
  return <span className={`staff-status staff-status-${variant}`}>{children}</span>;
}

export function StaffEmptyState({ title, children }) {
  return <section className="staff-empty"><h2>{title}</h2>{children && <p>{children}</p>}</section>;
}

export function StaffUnsavedIndicator({ dirty }) {
  return <span className="staff-unsaved" role="status">{dirty ? 'Unsaved changes' : 'No unsaved changes'}</span>;
}

export function StaffFormActions({ busy, disabled, dirty, onCancel, label = 'Save changes' }) {
  return <div className="staff-form-actions"><button className="staff-button-primary" aria-busy={busy || undefined} disabled={disabled || busy || !dirty}>{busy ? 'Saving…' : label}</button><button type="button" disabled={busy} onClick={onCancel}>Cancel changes</button><StaffUnsavedIndicator dirty={dirty} /></div>;
}
