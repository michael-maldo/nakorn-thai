// Refresh existing domain options without caching availability or provider credentials.
export function observeOptions(load, receive, fail, target = window, interval = 15000, timeoutMs = 5000) {
  let active = true, running = false, cancelRequest;
  const scheduleTimeout = (target.setTimeout || globalThis.setTimeout).bind(target);
  const clearTimeout = (target.clearTimeout || globalThis.clearTimeout).bind(target);
  async function refresh() {
    if (!active || running || target.document?.hidden) return;
    running = true;
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    let timeout;
    const deadline = new Promise((_, reject) => {
      cancelRequest = () => {
        clearTimeout(timeout);
        reject(new Error('Availability request cancelled.'));
        controller?.abort();
      };
      timeout = scheduleTimeout(() => {
        // Settle the race first: custom requests may ignore abort or never settle.
        reject(new Error('Availability request timed out. Please try again.'));
        controller?.abort();
      }, timeoutMs);
    });
    try {
      // Only the winning result is applied; late resolutions/rejections are ignored.
      const options = await Promise.race([Promise.resolve().then(() => load(controller?.signal)), deadline]);
      if (!options || typeof options !== 'object' || Array.isArray(options)) throw new Error('Availability response is invalid. Please try again.');
      if (active) receive(options);
    }
    catch (error) { if (active) fail(error); }
    finally { clearTimeout(timeout); cancelRequest = undefined; running = false; }
  }
  const timer = target.setInterval(refresh, interval);
  target.addEventListener('focus', refresh);
  // StrictMode immediately cleans up its first subscription before this microtask runs.
  Promise.resolve().then(refresh);
  return () => {
    active = false;
    target.clearInterval(timer);
    target.removeEventListener('focus', refresh);
    cancelRequest?.();
  };
}
