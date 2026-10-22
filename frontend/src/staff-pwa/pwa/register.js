export async function registerStaffWorker(onUpdate) {
  if (!('serviceWorker' in navigator) || !import.meta.env.PROD) return () => {};
  const registration = await navigator.serviceWorker.register('/staff/sw.js', { scope: '/staff/', updateViaCache: 'none' });
  let disposed = false;
  const check = () => registration.update().catch(() => {});
  const notify = () => { if (!disposed && registration.waiting) onUpdate(() => registration.waiting?.postMessage({ type: 'ACTIVATE' })); };
  const found = () => {
    const worker = registration.installing;
    worker?.addEventListener('statechange', () => { if (worker.state === 'installed' && navigator.serviceWorker.controller) notify(); });
  };
  registration.addEventListener('updatefound', found); notify();
  const timer = setInterval(check, 5 * 60 * 1000);
  const visible = () => { if (!document.hidden) check(); };
  document.addEventListener('visibilitychange', visible);
  return () => { disposed = true; clearInterval(timer); registration.removeEventListener('updatefound', found); document.removeEventListener('visibilitychange', visible); };
}
