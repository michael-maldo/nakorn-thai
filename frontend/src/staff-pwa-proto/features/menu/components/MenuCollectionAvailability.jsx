import { useState } from 'react';
import { removeCollectionChild, saveCollectionConfiguration } from '../api/menuApi';
import { allowMenuNavigation, availabilityLabel, instantFromInput, instantToInput, menuAdminHref } from '../model/menuAdminNavigation';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { ConfirmRemoval, EmptyState, FormActions, StatusBadge } from './MenuAdminLayout';
import MenuScheduleEditor, { weekdays } from './MenuScheduleEditor';

export default function MenuCollectionAvailability({ row, state }) {
  const form = useMenuAdminForm(row.collection.data, state.busy);
  const { draft, field } = form;
  const [editor, setEditor] = useState(null), [pending, setPending] = useState(null);
  const edit = resource => { if (allowMenuNavigation()) { form.reset(); setPending(null); setEditor(resource); } };
  if (editor) return <MenuScheduleEditor resource={editor} row={row} state={state} onCancel={() => { setEditor(null); state.clearFeedback(); }} />;
  return <>
    <section className="menu-admin-availability" aria-label="Effective availability"><div><h2>Effective availability now</h2><StatusBadge value={availabilityLabel(row.orderingAvailability)} positive={row.orderingAvailability?.available} /><p>Checked {row.availability?.evaluatedAt ? new Date(row.availability.evaluatedAt).toLocaleString('en-AU', { timeZone: row.restaurantTimezone }) : '—'} ({row.restaurantTimezone})</p></div><dl><div><dt>Collection rules</dt><dd>{availabilityLabel(row.availability)}</dd></div><div><dt>Restaurant</dt><dd>{row.restaurantOpen ? 'Open' : 'Closed'}</dd></div><div><dt>Configuration</dt><dd>{row.collection.data.status} · {row.collection.data.active ? 'Active' : 'Inactive'}</dd></div></dl></section>
    <p>Published and active collections can still be unavailable. <a className="menu-admin-text-link" href={menuAdminHref('collections', row.collection.id, 'overview')}>Edit publication settings in Overview</a>.</p>
    <form className="menu-admin-form" onSubmit={event => { event.preventDefault(); state.mutate(() => saveCollectionConfiguration({ ...row.collection, data: draft }, state.authorization), 'Availability settings saved.', form.committed); }}><fieldset disabled={state.disabled}><legend>Availability settings</legend>
      <h3>Broad validity window</h3><p className="menu-admin-muted">Optional start and end instants limit the entire collection. Leave blank for no limit.</p><div className="menu-admin-fields">
        {['startsAt', 'endsAt'].map(key => <label key={key}>{key === 'startsAt' ? 'Starts at (UTC)' : 'Ends at (UTC)'}<input type="datetime-local" step="0.001" value={instantToInput(draft[key])} onChange={e => field(key, instantFromInput(e.target.value))} /><small>UTC date and time. Starts inclusive; ends exclusive.</small></label>)}
      </div><h3>Daily cutoff</h3><div className="menu-admin-fields"><label>Cutoff time<input type="time" step={1} value={draft.dailyCutoffTime || ''} onChange={e => field('dailyCutoffTime', e.target.value || null)} /><small>Restaurant local time: {row.restaurantTimezone}. Blank means no cutoff.</small></label></div><p className="menu-admin-hint">Ordering stops at this time. Restaurant opening hours and closed dates also apply and are managed under <a href="#/staff/restaurant">Restaurant scheduling</a>.</p>
      <h3>Collection schedule timezone</h3><label className="menu-admin-search">Timezone<input required maxLength={64} value={draft.timezone} onChange={e => field('timezone', e.target.value)} /><small>IANA timezone for the schedule rules below only. It does not change the restaurant timezone or daily cutoff timezone.</small></label>
      <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty} onCancel={() => { form.reset(); state.clearFeedback(); }} label="Save availability" />
    </fieldset></form>
    <section className="menu-admin-schedules"><div className="menu-admin-section-heading"><div><h2>Collection schedule</h2><p>Additional collection windows in {row.collection.data.timezone}.</p></div><button className="menu-admin-primary" disabled={state.disabled} onClick={() => edit({ version: null, data: { ruleType: 'WEEKLY', dayOfWeek: 1, specificDate: null, startTime: null, endTime: null, active: true, displayOrder: 0 } })}>Add schedule</button></div>
      <p className="menu-admin-muted">No rules means unrestricted by collection scheduling. If rules exist but none are active, the collection is unavailable.</p>
      {pending && <ConfirmRemoval title="Remove collection schedule?" busy={state.disabled || form.dirty} onCancel={() => setPending(null)} onConfirm={() => state.mutate(() => removeCollectionChild(row.collection.id, 'schedules', pending, state.authorization), 'Schedule removed.', () => setPending(null))}>Removing the last rule makes collection scheduling unrestricted. Restaurant hours and the daily cutoff remain unchanged.</ConfirmRemoval>}
      {row.schedules.length ? <div className="menu-admin-table-wrap"><table className="menu-admin-table"><caption className="sr-only">Collection schedule rules</caption><thead><tr><th>Day / date</th><th>Window</th><th>State</th><th>Order</th><th>Actions</th></tr></thead><tbody>{row.schedules.map(s => <tr key={s.id}>
        <td data-label="Day / date">{s.data.ruleType === 'WEEKLY' ? weekdays[s.data.dayOfWeek - 1] : s.data.specificDate}<small>{s.data.ruleType === 'WEEKLY' ? 'Every week' : 'Specific date'}</small></td><td data-label="Window">{s.data.startTime ? `${s.data.startTime} – ${s.data.endTime}` : 'All day'}{s.data.startTime && s.data.startTime > s.data.endTime && <small>Ends the following day</small>}</td><td data-label="State">{s.data.active ? 'Active' : 'Inactive'}</td><td data-label="Order">{s.data.displayOrder}</td><td data-label="Actions"><div className="menu-admin-actions"><button disabled={state.disabled} onClick={() => edit(s)}>Edit rule</button><button className="menu-admin-danger" disabled={state.disabled || form.dirty} onClick={() => setPending(s)}>Remove rule</button></div></td>
      </tr>)}</tbody></table></div> : <EmptyState title="No collection schedule restrictions">Add a weekly or specific-date rule when this collection needs its own time windows.</EmptyState>}
    </section>
  </>;
}
