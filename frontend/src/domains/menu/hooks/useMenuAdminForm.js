import { useEffect, useRef, useState } from 'react';
import { registerMenuGuard } from '../model/menuAdminNavigation';

export function useMenuNavigationGuard(dirty, busy) {
  const state = useRef({ dirty, busy });
  state.current = { dirty, busy };
  useEffect(() => registerMenuGuard(() => state.current), []);
  return state;
}
export default function useMenuAdminForm(initial, busy) {
  const [draft, setDraft] = useState(initial);
  useEffect(() => {
    const legend = document.querySelector('.menu-admin-form legend');
    if (legend) { legend.tabIndex = -1; legend.focus(); }
  }, []);
  const [baseline, setBaseline] = useState(JSON.stringify(initial));
  const dirty = JSON.stringify(draft) !== baseline;
  const guard = useMenuNavigationGuard(dirty, busy);
  const field = (name, value) => setDraft(before => ({ ...before, [name]: value }));
  function committed() { guard.current.dirty = false; setBaseline(JSON.stringify(draft)); }
  function reset() { guard.current.dirty = false; setDraft(initial); setBaseline(JSON.stringify(initial)); }
  return { draft, setDraft, field, dirty, committed, reset };
}
