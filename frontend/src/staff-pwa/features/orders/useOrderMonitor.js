import { useEffect, useRef, useState } from 'react';
import { getStaffOrders } from '../../../domains/ordering/api/orderApi';
import { staffIdentityContext } from '../../../domains/identity/api/identityApi';
import { createPendingTracker } from '../notifications/alerts';
export default function useOrderMonitor(authorization, role, onArrival) {
  const [state, setState] = useState({ orders: [], loading: true, error: '', lastSync: null, unread: 0, foreground: !document.hidden });
  const callback = useRef(onArrival); callback.current = onArrival;
  const auth = useRef(authorization); auth.current = authorization;
  const refresh = useRef(() => {}), tracker = useRef(createPendingTracker());
  useEffect(() => {
    const owner = staffIdentityContext();
    const owns = () => { const current = staffIdentityContext(); return active && !!current && current.id === owner?.id && current.sessionId === owner?.sessionId && current.role === owner?.role; };
    let active = true, reading = false, timer, controller;
    const load = async () => {
      if (!owns() || reading || document.hidden) return;
      clearTimeout(timer); reading = true;
      setState(previous => ({ ...previous, loading: true, foreground: true }));
      try {
        controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 15000);
        let orders;
        try { orders = await getStaffOrders(auth.current, role === 'BOH', false, controller.signal); }
        finally { clearTimeout(timeout); }
        if (!owns()) return;
        if (!Array.isArray(orders)) throw new Error('Invalid order queue response.');
        const { arrived, count } = tracker.current.accept(orders);
        setState({ orders, error: '', loading: false, foreground: true, lastSync: Date.now(), unread: count });
        if (arrived) callback.current(tracker.current.arrivals());
      } catch (error) {
        if (owns()) setState(previous => ({ ...previous, loading: false, error: error.name === 'AbortError' ? 'Order monitoring timed out. Check the connection and refresh.' : error.message }));
      } finally { reading = false; if (active && !document.hidden) timer = setTimeout(load, 10000); }
    };
    const visible = () => {
      clearTimeout(timer);
      setState(previous => ({ ...previous, foreground: !document.hidden }));
      if (!document.hidden) load();
    };
    refresh.current = load;
    document.addEventListener('visibilitychange', visible); window.addEventListener('online', load);
    load();
    return () => { active = false; controller?.abort(); clearTimeout(timer); refresh.current = () => {}; document.removeEventListener('visibilitychange', visible); window.removeEventListener('online', load); };
  }, [role]);
  return { ...state, refresh: () => refresh.current(), acknowledge: () => { tracker.current.acknowledge(); setState(previous => ({ ...previous, unread: 0 })); } };
}
