import { useEffect, useState } from 'react';
import { useAuth } from '../../identity/model/AuthContext';
import { functionRequest } from '../../reservation/api/functionApi';

const statuses = ['NEW', 'CONTACTED', 'CONFIRMED', 'DECLINED', 'CANCELLED', 'COMPLETED'];
const transitions = {
  NEW: ['NEW', 'CONTACTED', 'CONFIRMED', 'DECLINED', 'CANCELLED'],
  CONTACTED: ['CONTACTED', 'CONFIRMED', 'DECLINED', 'CANCELLED'],
  CONFIRMED: ['CONFIRMED', 'COMPLETED', 'CANCELLED'],
};

export default function FunctionEnquiriesPage() {
  const { authorization } = useAuth();
  const [status, setStatus] = useState('NEW');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [queue, setQueue] = useState({ items: [], hasNext: false });
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setBusy(true); setError(''); setQueue({ items: [], hasNext: false });
    functionRequest(`/api/staff/functions?status=${status}&page=${page}`, { authorization, signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setQueue(data); })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setBusy(false); });
    return () => controller.abort();
  }, [status, page, reload, authorization]);

  async function save(event, enquiry) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setError(''); setNotice('');
    try {
      await functionRequest(`/api/staff/functions/${enquiry.id}`, {
        method: 'PATCH', authorization,
        body: { version: enquiry.version, status: form.get('status'), arrangedDate: form.get('date') || null, staffNote: form.get('note') },
      });
      setNotice('Enquiry updated. Contact the customer directly; no notification was sent.');
      setReload((value) => value + 1);
    } catch (failure) { setError(failure.message); setBusy(false); }
  }

  return <main className="staff-menu page-width">
    <a href="#/staff">Staff home</a><h1>Functions &amp; venue enquiries</h1>
    <p>Follow up with the customer, check availability and agree arrangements before confirming. Record the agreed date and details below. No automatic email or SMS is sent.</p>
    <div className="staff-toolbar">
      <label>Status<select value={status} disabled={busy} onChange={(event) => { setStatus(event.target.value); setPage(0); setNotice(''); }}>
        {['ALL', ...statuses].map((value) => <option key={value}>{value}</option>)}
      </select></label>
      <button disabled={busy} onClick={() => setReload((value) => value + 1)}>Refresh enquiries</button>
    </div>
    {error && <p className="staff-error" role="alert">{error}</p>}
    {notice && <p role="status">{notice}</p>}
    {busy && <p role="status">Updating enquiries…</p>}
    {!busy && !error && !queue.items.length && <p>No enquiries in this view.</p>}
    <div className="order-queue">{queue.items.map((enquiry) => <article className="order-panel" key={`${enquiry.id}-${enquiry.version}`}>
      <h2>{enquiry.eventType} · {enquiry.customerName}</h2>
      <p>{enquiry.guestCount} estimated guests · {enquiry.status}</p>
      <p>Preferred date: {enquiry.preferredDate || 'Flexible'} · {enquiry.preferredTime || 'Time to discuss'}</p>
      <p>Phone: {enquiry.phone}<br />Email: {enquiry.email}</p>
      <p className="function-message">{enquiry.message}</p>
      <small>Reference: {enquiry.id}</small>
      <form onSubmit={(event) => save(event, enquiry)}><fieldset disabled={busy}>
        <legend>Staff follow-up</legend>
        <label>Status<select name="status" defaultValue={enquiry.status}>{(transitions[enquiry.status] || [enquiry.status]).map((value) => <option key={value}>{value}</option>)}</select></label>
        <label>Agreed event date<input name="date" type="date" defaultValue={enquiry.arrangedDate || ''} /></label>
        <label>Staff notes<textarea name="note" rows={4} maxLength={2000} defaultValue={enquiry.staffNote} /></label>
        <button>Save enquiry</button>
      </fieldset></form>
      {enquiry.updatedBy && <small>Last updated by {enquiry.updatedBy} · {new Date(enquiry.updatedAt).toLocaleString('en-AU', { timeZone: 'Australia/Melbourne' })} Melbourne time</small>}
    </article>)}</div>
    <div className="staff-toolbar"><button disabled={busy || page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={busy || !queue.hasNext} onClick={() => setPage((value) => value + 1)}>Next</button></div>
  </main>;
}
