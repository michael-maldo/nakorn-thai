import { saveCollectionConfiguration } from '../api/menuApi';
import { menuAdminHref } from '../model/menuAdminNavigation';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { FormActions, OrderField } from './MenuAdminLayout';

export default function MenuCollectionOverview({ row, state }) {
  const initial = row?.collection.data || { name: '', slug: '', description: null, status: 'DRAFT', active: true, displayOrder: 0, timezone: state.collections[0]?.restaurantTimezone || '', startsAt: null, endsAt: null, dailyCutoffTime: null };
  const form = useMenuAdminForm(initial, state.busy);
  const { draft, field } = form;
  async function save(event) {
    event.preventDefault();
    const result = await state.mutate(() => saveCollectionConfiguration({ id: row?.collection.id, version: row?.collection.version ?? null, data: draft }, state.authorization), 'Collection saved.', form.committed);
    if (!row && result.committed) state.navigate(menuAdminHref('collections', result.result.id));
  }
  return <form className="menu-admin-form" onSubmit={save}><fieldset disabled={state.disabled}><legend>Collection overview</legend><p className="menu-admin-muted">Manage the collection’s identity and publication settings.</p><div className="menu-admin-fields">
    <label>Name<input required maxLength={150} value={draft.name} onChange={e => field('name', e.target.value)} /></label>
    <label>Slug<input required maxLength={180} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={draft.slug} onChange={e => field('slug', e.target.value)} /><small>Unique lowercase words separated by hyphens.</small></label>
    <label className="menu-admin-wide">Description<textarea maxLength={10000} rows={4} value={draft.description || ''} onChange={e => field('description', e.target.value || null)} /></label>
    <label>Status<select value={draft.status} onChange={e => field('status', e.target.value)}>{['DRAFT', 'PUBLISHED', 'ARCHIVED'].map(status => <option key={status}>{status}</option>)}</select></label>
    <OrderField value={draft.displayOrder} onChange={value => field('displayOrder', value)} />
    <label className="menu-admin-check"><input type="checkbox" checked={draft.active} onChange={e => field('active', e.target.checked)} />Active collection</label>
    {!row && !initial.timezone && <label>Collection schedule timezone<input required value={draft.timezone} onChange={e => field('timezone', e.target.value)} placeholder="IANA timezone" /><small>Only used for collection schedule rules. Restaurant settings govern restaurant time.</small></label>}
  </div><p className="menu-admin-hint">Published and active does not guarantee availability now. Restaurant opening rules, collection schedules and cutoff settings also apply. Archive or deactivate to stop availability without deleting data.</p>
  <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty} onCancel={() => { form.reset(); state.clearFeedback(); }} label={row ? 'Save overview' : 'Create collection'} /></fieldset></form>;
}
