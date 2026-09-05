import { useEffect, useState } from 'react';
import { getSignatureDishes } from '../api/menuApi';

export default function useMenu() {
  const [state, setState] = useState({ items: [], loading: true, error: '' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ items: [], loading: true, error: '' });
    getSignatureDishes(controller.signal)
      .then((menu) => {
        if (!Array.isArray(menu.items)) throw new Error('The menu service returned an invalid response.');
        if (!controller.signal.aborted) setState({ items: menu.items, loading: false, error: '' });
      })
      .catch((error) => {
        if (!controller.signal.aborted) setState({ items: [], loading: false, error: error.message });
      });
    return () => controller.abort();
  }, [attempt]);
  return { ...state, retry: () => setAttempt((value) => value + 1) };
}
