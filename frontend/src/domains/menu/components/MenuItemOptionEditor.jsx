import { useState } from 'react';
import { createItemOptionGroup, removeItemOptionGroup, saveItemOptionGroup, saveSharedOption } from '../api/menuApi';
import { itemOptionAssignment, optionPriceDraft, optionPriceMinor } from '../model/itemOptionPrices';
import { allowMenuNavigation, money } from '../model/menuAdminNavigation';
import useItemOptionAdmin from '../hooks/useItemOptionAdmin';
import useMenuAdminForm from '../hooks/useMenuAdminForm';
import { ConfirmRemoval, EmptyState, FormActions, OrderField } from './MenuAdminLayout';

export default function MenuItemOptionEditor({ item, authorization }) {
  const state = useItemOptionAdmin(item.id, authorization);
  const [editor, setEditor] = useState(null), [pending, setPending] = useState(null);
  const close = () => setEditor(null);
  const unused = state.groups?.filter(group => group.group.data.active && !state.assignments.some(a => a.id === group.group.id)) || [];
  return <section>
    <div className="menu-admin-section-heading"><div><h2>Options &amp; extras</h2><p>Reuse choices across your menu. Every extra price below belongs only to <strong>{item.name}</strong>.</p></div><button disabled={state.busy} onClick={() => { if (allowMenuNavigation()) { close(); setPending(null); state.reload(); } }}>Reload options</button></div>
    {state.error && <p role="alert" className="menu-admin-error">{state.error}</p>}
    {state.notice && <p role="status" className="menu-admin-success">{state.notice}</p>}
    {state.needsReload && <p role="status">Reload options before making further changes.</p>}
    {!state.groups ? !state.error && <p role="status">Loading option groups…</p> : <div key={state.revision}>
      {editor ? editor.kind === 'new' ? <NewGroupForm item={item} state={state} onClose={close} />
        : editor.kind === 'library' ? <SharedChoiceForm group={state.groups.find(g => g.group.id === editor.groupId)} state={state} onClose={close} />
          : <AssignmentForm item={item} state={state} resource={editor.resource} groups={editor.resource ? state.groups.filter(g => g.group.id === editor.resource.id) : unused} onClose={close} />
        : <>
          <div className="menu-admin-actions"><button className="menu-admin-primary" disabled={state.disabled} onClick={() => { setPending(null); setEditor({ kind: 'new' }); }}>Create option group</button><button disabled={state.disabled || !unused.length} onClick={() => { setPending(null); setEditor({ kind: 'reuse' }); }}>Reuse existing group</button></div>
          <p className="menu-admin-hint">Required choices ask the customer to choose one, such as Pork or Beef. Optional extras can be skipped or added in the quantities you allow.</p>
          {pending && <ConfirmRemoval title="Remove this group from the item?" busy={state.disabled} onCancel={() => setPending(null)} onConfirm={() => state.mutate(() => removeItemOptionGroup(item.id, pending, authorization), () => setPending(null), 'Group removed from this item.')}>
            Its reusable choices and assignments on other items will remain. This item’s option prices will be removed.
          </ConfirmRemoval>}
          {state.assignments.length ? state.assignments.map(assignment => {
            const group = state.groups.find(g => g.group.id === assignment.id);
            return <article className="menu-admin-option-card" key={assignment.id}><h3>{group.group.data.name}</h3><p>{assignment.data.minSelections > 0 ? 'Required choice' : 'Optional extras'} · {group.group.data.selectionType === 'SINGLE' ? 'Choose one' : `Up to ${assignment.data.maxSelections} extras`}{!group.group.data.active && ' · Group inactive'}</p>
              <ul>{group.options.map(option => { const price = assignment.data.prices.find(p => p.optionId === option.id); return <li key={option.id}><span>{option.data.name}{!option.data.active && ' (inactive)'}</span><strong>{price ? `+${money(price.priceDeltaMinor)}` : 'Not enabled on this item'}</strong></li>; })}</ul>
              <div className="menu-admin-actions"><button disabled={state.disabled} onClick={() => { setPending(null); setEditor({ kind: 'edit', resource: assignment }); }}>Edit item prices &amp; rules</button><button disabled={state.disabled} onClick={() => { setPending(null); setEditor({ kind: 'library', groupId: assignment.id }); }}>Edit shared choices</button><button disabled={state.disabled} className="menu-admin-danger" onClick={() => setPending(assignment)}>Remove from item</button></div>
            </article>;
          }) : <EmptyState title="No options assigned">Create a group here or reuse one from another item. Set prices separately for this dish.</EmptyState>}
        </>}
    </div>}
  </section>;
}

function AssignmentForm({ item, groups, resource, state, onClose }) {
  const [groupId, setGroupId] = useState(resource?.id || groups[0].group.id);
  const group = groups.find(g => g.group.id === groupId);
  return <>
    {!resource && <label className="menu-admin-option-picker">Reusable group<select value={groupId} onChange={e => { if (allowMenuNavigation()) setGroupId(e.target.value); }} disabled={state.disabled}>{groups.map(g => <option key={g.group.id} value={g.group.id}>{g.group.data.name} ({g.group.data.code})</option>)}</select></label>}
    <AssignmentPrices key={groupId} item={item} group={group} resource={resource} state={state} onClose={onClose} />
  </>;
}
function AssignmentPrices({ item, group, resource, state, onClose }) {
  const single = group.group.data.selectionType === 'SINGLE';
  const form = useMenuAdminForm({ minSelections: resource?.data.minSelections ?? (single ? 1 : 0), maxSelections: resource?.data.maxSelections ?? (single ? 1 : 5), displayOrder: resource?.data.displayOrder ?? 0, prices: optionPriceDraft(group, resource) }, state.busy);
  return <form className="menu-admin-form" onSubmit={event => {
    event.preventDefault(); state.mutate(() => saveItemOptionGroup(item.id, group.group.id, itemOptionAssignment(group, resource, form.draft), state.authorization), () => { form.committed(); onClose(); }, 'Options and prices saved for this item.');
  }}><fieldset disabled={state.disabled}><legend>{group.group.data.name}: prices for {item.name}</legend><p>Enter $0 for an included choice. Leave a price blank to exclude that choice from this item. These prices never change another item.</p>
    <div className="menu-admin-fields">
      <label>Selection requirement<select value={form.draft.minSelections > 0 ? 'required' : 'optional'} onChange={e => form.field('minSelections', e.target.value === 'required' ? 1 : 0)}><option value="required">Required — at least one</option><option value="optional">Optional — can skip</option></select></label>
      <label>Maximum selections<input type="number" required min="1" max="100" step="1" disabled={single} value={form.draft.maxSelections} onChange={e => form.field('maxSelections', e.target.value)} /></label>
      <OrderField value={form.draft.displayOrder} onChange={value => form.field('displayOrder', value)} />
      {group.options.map(option => <label key={option.id}>{option.data.name} extra price (AUD){!option.data.active && ' — inactive'}<input type="number" min="0" max="9999999.99" step="0.01" placeholder="Not enabled" value={form.draft.prices[option.id]} onChange={e => form.field('prices', { ...form.draft.prices, [option.id]: e.target.value })} /></label>)}
    </div>
    {!group.options.length && <p>This group has no choices. Add shared choices before making it required.</p>}
    <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty || !resource} onCancel={() => { form.reset(); onClose(); }} label={resource ? 'Save item options' : 'Assign to item'} />
  </fieldset></form>;
}

const blankChoice = () => ({ name: '', code: '', amount: '0.00' });
function NewGroupForm({ item, state, onClose }) {
  const form = useMenuAdminForm({ name: '', code: '', selectionType: 'SINGLE', maxSelections: 1, displayOrder: 0, options: [blankChoice(), blankChoice()] }, state.busy);
  const choiceField = (index, key, value) => form.field('options', form.draft.options.map((option, n) => n === index ? { ...option, [key]: value } : option));
  return <form className="menu-admin-form" onSubmit={event => {
    event.preventDefault(); state.mutate(() => createItemOptionGroup(item.id, { ...form.draft, maxSelections: Number(form.draft.maxSelections), options: form.draft.options.map(option => ({ name: option.name, code: option.code, priceDeltaMinor: optionPriceMinor(option.amount) })) }, state.authorization), () => { form.committed(); onClose(); }, 'Reusable group created and assigned. Prices saved only for this item.');
  }}><fieldset disabled={state.disabled}><legend>Create a reusable option group</legend><p>The group and choices will be available to reuse on other items. Prices entered here apply only to <strong>{item.name}</strong>.</p>
    <div className="menu-admin-fields"><label>Group name<input required maxLength={100} placeholder="Protein or spice level" value={form.draft.name} onChange={e => form.field('name', e.target.value)} /></label><label>Unique group code<input required maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" placeholder="protein" value={form.draft.code} onChange={e => form.field('code', e.target.value)} /><small>Lowercase words separated by hyphens.</small></label>
      <label>Option type<select value={form.draft.selectionType} onChange={e => form.setDraft({ ...form.draft, selectionType: e.target.value, maxSelections: e.target.value === 'SINGLE' ? 1 : 5 })}><option value="SINGLE">Required choice — choose one</option><option value="MULTIPLE">Optional extras — choose quantities</option></select></label>
      {form.draft.selectionType === 'MULTIPLE' && <label>Maximum extras per dish<input type="number" required min="1" max="100" step="1" value={form.draft.maxSelections} onChange={e => form.field('maxSelections', e.target.value)} /></label>}
      <OrderField value={form.draft.displayOrder} onChange={value => form.field('displayOrder', value)} />
    </div>
    {form.draft.options.map((option, index) => <fieldset className="menu-admin-option-choice" key={index}><legend>Choice {index + 1}</legend><div className="menu-admin-fields"><label>Choice name<input required maxLength={100} value={option.name} onChange={e => choiceField(index, 'name', e.target.value)} /></label><label>Choice code<input required maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={option.code} onChange={e => choiceField(index, 'code', e.target.value)} /></label><label>Extra price for this item (AUD)<input type="number" required min="0" max="9999999.99" step="0.01" value={option.amount} onChange={e => choiceField(index, 'amount', e.target.value)} /></label></div><button type="button" disabled={form.draft.options.length === 1} onClick={() => form.field('options', form.draft.options.filter((_, n) => n !== index))}>Remove choice {index + 1}</button></fieldset>)}
    <button type="button" disabled={form.draft.options.length >= 100} onClick={() => form.field('options', [...form.draft.options, blankChoice()])}>Add another choice</button>
    <FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty} onCancel={() => { form.reset(); onClose(); }} label="Create group & assign to item" />
  </fieldset></form>;
}

function SharedChoiceForm({ group, state, onClose }) {
  const [optionId, setOptionId] = useState('new');
  const resource = group.options.find(option => option.id === optionId);
  return <><p className="menu-admin-hint">Editing a shared choice changes its name and availability everywhere it is used. Item prices are not changed. A new choice must be priced separately on each item before customers can select it.</p>
    <label className="menu-admin-option-picker">Shared choice<select disabled={state.disabled} value={optionId} onChange={e => { if (allowMenuNavigation()) setOptionId(e.target.value); }}><option value="new">Add new choice to {group.group.data.name}</option>{group.options.map(option => <option key={option.id} value={option.id}>{option.data.name}</option>)}</select></label>
    <SharedChoiceFields key={optionId} group={group} resource={resource} state={state} onClose={onClose} />
  </>;
}
function SharedChoiceFields({ group, resource, state, onClose }) {
  const form = useMenuAdminForm(resource?.data || { name: '', code: '', active: true, displayOrder: group.options.length }, state.busy);
  return <form className="menu-admin-form" onSubmit={event => { event.preventDefault(); state.mutate(() => saveSharedOption(group.group.id, { ...resource, data: form.draft, version: resource?.version ?? null }, state.authorization), () => { form.committed(); onClose(); }, 'Shared choice saved. Set its price on this item to enable it.'); }}><fieldset disabled={state.disabled}><legend>{resource ? 'Edit shared choice' : 'Add shared choice'}</legend><div className="menu-admin-fields">
    <label>Name<input required maxLength={100} value={form.draft.name} onChange={e => form.field('name', e.target.value)} /></label><label>Code<input required maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={form.draft.code} onChange={e => form.field('code', e.target.value)} /></label><OrderField value={form.draft.displayOrder} onChange={value => form.field('displayOrder', value)} /><label className="menu-admin-check"><input type="checkbox" checked={form.draft.active} onChange={e => form.field('active', e.target.checked)} />Active across assigned items</label>
  </div><FormActions busy={state.busy} disabled={state.disabled} dirty={form.dirty} onCancel={() => { form.reset(); onClose(); }} label="Save shared choice" /></fieldset></form>;
}
