import { useEffect, useState } from 'react';
import { getHomepageMenu } from '../api/menuApi';

export default function useHomepageMenu() {
  const [state, setState] = useState({ collections: [], loading: true, error: '' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ collections: [], loading: true, error: '' });
    getHomepageMenu(controller.signal).then(collections => {
      if (!controller.signal.aborted) setState({ collections, loading: false, error: '' });
    }).catch(error => {
      if (!controller.signal.aborted) setState({ collections: [], loading: false, error: error.message });
    });
    return () => controller.abort();
  }, [attempt]);
  return { ...state, retry: () => setAttempt(value => value + 1) };
}
