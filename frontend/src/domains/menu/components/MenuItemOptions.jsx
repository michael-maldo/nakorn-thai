import { changeOptionSelection } from '../model/menuOptions';
import { money } from '../../ordering/model/cartReducer';

export default function MenuItemOptions({ groups, selections, onChange, disabled = false }) {
  return <div className="menu-option-groups">
    {groups.map((group) => {
      const total = selections.filter((selection) => group.options.some((option) => option.id === selection.optionId))
        .reduce((sum, selection) => sum + selection.quantity, 0);
      return <fieldset key={group.id} disabled={disabled || !group.active} className="menu-option-group">
        <legend>{group.name} {group.minSelections > 0 ? '(required)' : '(optional)'}</legend>
        {!group.active && <p>Currently unavailable</p>}
        {group.selectionType === 'SINGLE' ? <label>Choose {group.name}
          <select value={selections.find((selection) => group.options.some((option) => option.id === selection.optionId))?.optionId ?? ''}
            onChange={(event) => {
              const id = event.target.value;
              if (!id) onChange(selections.filter((selection) => !group.options.some((option) => option.id === selection.optionId)));
              else onChange(changeOptionSelection(groups, selections, group.id, id, 1));
            }}>
            <option value="">{group.minSelections > 0 ? 'Choose an option' : 'None'}</option>
            {group.options.map((option) => <option key={option.id} value={option.id} disabled={!option.available}>
              {option.name} (+{money(option.priceDeltaMinor)}){!option.available ? ' — unavailable' : ''}
            </option>)}
          </select>
        </label> : <>
          <p>Choose {group.minSelections}–{group.maxSelections} options per dish. Selected: {total}.</p>
          {group.options.map((option) => {
            const quantity = selections.find((selection) => selection.optionId === option.id)?.quantity ?? 0;
            return <label className="menu-option-checkbox" key={option.id}>
              <span>{option.name} (+{money(option.priceDeltaMinor)}){!option.available && ' — unavailable'}</span>
              <input aria-label={`${group.name}: ${option.name}`} type="checkbox" checked={quantity > 0}
                disabled={!option.available || (quantity === 0 && total >= group.maxSelections)}
                onChange={(event) => onChange(changeOptionSelection(groups, selections, group.id, option.id, event.target.checked ? 1 : 0))} />
            </label>;
          })}
        </>}
      </fieldset>;
    })}
  </div>;
}
