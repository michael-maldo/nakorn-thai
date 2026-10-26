import { saveCollectionChild } from '../api/menuApi';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { FormActions, OrderField } from './MenuAdminLayout';
export const weekdays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

export default function MenuScheduleEditor({ resource, row, state, onCancel }) {
  const form = useMenuAdminForm(resource.data, state.busy);
  const { draft, field } = form;
  return <form className="menu-admin-form" onSubmit={event => { event.preventDefault(); state.mutate(() => saveCollectionChild(row.collection.id, 'schedules', { ...resource, data: draft }, state.authorization), 'Collection schedule saved.', () => { form.committed(); onCancel(); }); }}><fieldset disabled={state.disabled}>
    <legend>{resource.id ? 'Edit collection schedule' : 'Add collection schedule'}</legend><p>Times use {row.collection.data.timezone}, the collection schedule timezone.</p><div className="menu-admin-fields">
      <label>Rule type<select value={draft.ruleType} onChange={e => form.setDraft(before => ({ ...before, ruleType: e.target.value, dayOfWeek: e.target.value === 'WEEKLY' ? 1 : null, specificDate: e.target.value === 'SPECIFIC_DATE' ? '' : null }))}><option value="WEEKLY">Weekly</option><option value="SPECIFIC_DATE">Specific date</option></select></label>
      {draft.ruleType === 'WEEKLY' ? <label>Starting day<select value={draft.dayOfWeek} onChange={e => field('dayOfWeek', Number(e.target.value))}>{weekdays.map((day, index) => <option key={day} value={index + 1}>{day}</option>)}</select></label> : <label>Starting date<input type="date" required value={draft.specificDate || ''} onChange={e => field('specificDate', e.target.value)} /></label>}
      <label>Start time<input type="time" step={1} value={draft.startTime || ''} onChange={e => field('startTime', e.target.value || null)} /></label><label>End time<input type="time" step={1} value={draft.endTime || ''} onChange={e => field('endTime', e.target.value || null)} /></label>
      <OrderField value={draft.displayOrder} onChange={value => field('displayOrder', value)} /><label className="menu-admin-check"><input type="checkbox" checked={draft.active} onChange={e => field('active', e.target.checked)} />Active rule</label>
    </div><p className="menu-admin-hint">Leave both times blank for all day. Otherwise supply distinct start and end times. Start is inclusive; end is exclusive. Overnight ranges belong to the starting day.</p>
    <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty || !resource.id} onCancel={() => { form.reset(); onCancel(); }} label="Save schedule" />
  </fieldset></form>;
}
