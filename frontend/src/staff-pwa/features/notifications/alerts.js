// Match Android NewOrderTracker: a silent baseline, then one alert per new ID.
export function createPendingTracker() {
  const seen = new Set(), unread = new Set();
  let initialized = false;
  return {
    accept(orders) {
      const pending = new Set(); let arrived = false;
      for (const order of orders) {
        if (order.status === 'NEW') {
          pending.add(order.id);
          if (initialized && !seen.has(order.id)) { unread.add(order.id); arrived = true; }
        }
        seen.add(order.id);
      }
      for (const id of unread) if (!pending.has(id)) unread.delete(id);
      initialized = true;
      return { arrived, count: unread.size };
    },
    acknowledge() { unread.clear(); },
  };
}
export const alertDefaults = { sound: 'double', volume: 0.65 };
export function normalizeAlertSettings(value = {}) {
  return { sound: ['double', 'chime'].includes(value.sound) ? value.sound : 'double',
    volume: Number.isFinite(Number(value.volume)) ? Math.max(0, Math.min(1, Number(value.volume))) : 0.65 };
}
export function createSoundEngine(Context, changed = () => {}) {
  let context;
  const ready = () => context?.state === 'running';
  return {
    ready,
    async enable() {
      if (!Context) throw new Error('Web Audio is unavailable in this browser.');
      if (!context || context.state === 'closed') {
        context = new Context(); context.onstatechange = () => changed(ready());
      }
      // Called directly from a tap. Never attempt to unlock audio during polling.
      let timer;
      try {
        await Promise.race([context.resume(), new Promise((_resolve, reject) => { timer = setTimeout(() => reject(new Error('Sound activation timed out. Tap Enable Sound to try a fresh audio session.')), 3000); })]);
      } catch (error) {
        context.onstatechange = null; context.close().catch(() => {}); context = null; changed(false); throw error;
      } finally { clearTimeout(timer); }
      changed(ready());
      if (!ready()) throw new Error('Sound is interrupted. Tap Enable Sound again.');
    },
    play(settings) {
      if (!ready()) { changed(false); return false; }
      const { sound, volume } = normalizeAlertSettings(settings);
      [0, 0.24].forEach((delay, index) => {
        const oscillator = context.createOscillator(), gain = context.createGain();
        const start = context.currentTime + delay;
        oscillator.frequency.value = sound === 'chime' ? [660, 880][index] : 880;
        gain.gain.setValueAtTime(0, start);
        gain.gain.linearRampToValueAtTime(volume * 0.3, start + 0.015);
        gain.gain.exponentialRampToValueAtTime(0.001, start + 0.19);
        oscillator.connect(gain); gain.connect(context.destination);
        oscillator.start(start); oscillator.stop(start + 0.2);
        oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); };
      });
      return true;
    },
    close() { if (context) { context.onstatechange = null; context.close().catch(() => {}); context = null; } },
  };
}
