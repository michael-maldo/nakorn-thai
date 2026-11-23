import { useEffect, useState } from 'react';

const invalidAccess = new Set([401, 403, 404, 410]);

export default function useTrackedOrder(load) {
  const [result, setResult] = useState({ order: null, error: '', expired: false });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!load) return;
    const controller = new AbortController();
    const { signal } = controller;
    let timer;
    async function poll() {
      try {
        const order = await load(AbortSignal.any([signal, AbortSignal.timeout(10000)]));
        if (signal.aborted) return;
        setResult({ order, error: '', expired: false });
        timer = setTimeout(poll, ['COMPLETED', 'CANCELLED'].includes(order.status) ? 60000 : 5000);
      } catch (failure) {
        if (signal.aborted) return;
        if (invalidAccess.has(failure.status)) {
          setResult({ order: null, error: '', expired: true });
        } else {
          setResult((previous) => ({ ...previous, error: 'Order tracking is temporarily unavailable. Please try again.' }));
          timer = setTimeout(poll, 10000);
        }
      }
    }
    poll();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [load, attempt]);
  return {
    ...result,
    retry() {
      setResult((previous) => ({ ...previous, error: '' }));
      setAttempt((previous) => previous + 1);
    },
  };
}
