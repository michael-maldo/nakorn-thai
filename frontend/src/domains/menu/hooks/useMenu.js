import { useEffect, useState } from 'react';
import { getMenuCollection, getMenuCollections } from '../api/menuApi';
import { selectCollection } from '../model/menuCollections';

export default function useMenu(selectedId = null) {
  const [state, setState] = useState({ collections: [], menu: null, loading: true, error: '', selectedId });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState((previous) => ({ ...previous, menu: null, loading: true, error: '', selectedId }));
    async function load() {
      const collections = await getMenuCollections(controller.signal);
      const selected = selectCollection(collections, selectedId);
      const menu = selected ? await getMenuCollection(selected.slug, controller.signal) : null;
      if (menu && (menu.id !== selected.id || !Array.isArray(menu.items) || !Array.isArray(menu.categories)
        || typeof menu.availability?.available !== 'boolean')) throw new Error('The menu service returned an invalid response.');
      if (!controller.signal.aborted) setState({ collections: collections.map((entry) => entry.id === menu?.id
        ? { ...entry, availability: menu.availability } : entry), menu, loading: false, error: '', selectedId });
    }
    load().catch((error) => {
      if (!controller.signal.aborted) setState({ collections: [], menu: null, loading: false, error: error.message, selectedId });
    });
    return () => controller.abort();
  }, [attempt, selectedId]);
  const current = state.selectedId === selectedId;
  return { ...state, menu: current ? state.menu : null, items: current ? state.menu?.items ?? [] : [],
    loading: !current || state.loading, retry: () => setAttempt((value) => value + 1) };
}
