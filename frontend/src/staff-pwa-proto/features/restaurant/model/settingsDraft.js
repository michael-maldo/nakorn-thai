// Settings drafts remain in component memory, including write-only replacements.
export function savedDraft(row) {
  return { fields: { ...row.fields }, secrets: {}, replace: {}, clear: [] };
}
export function settingsDraftDirty(draft, row) {
  return Object.keys({ ...draft.fields, ...row.fields }).some(key => draft.fields[key] !== row.fields[key])
    || Object.values(draft.secrets).some(value => value !== '') || draft.clear.length > 0;
}
