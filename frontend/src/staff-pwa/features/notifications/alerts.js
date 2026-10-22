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
const diagnostics = [];
export const readAudioDiagnostics = () => diagnostics.map(entry => ({ ...entry }));
function logAudio(event, details) {
  const entry = { timestamp: new Date().toISOString(), event, ...details };
  diagnostics.push(entry); if (diagnostics.length > 80) diagnostics.shift();
  console.info('[Nakorn Staff audio]', entry);
}

export function createSoundEngine(Context, changed = () => {}, {
  document: page = globalThis.document, window: host = globalThis.window,
  log = logAudio, timeoutMs = 3000, probeMs = 120,
} = {}) {
  let context, verified = false, disposed = false, generation = 0, activation = null;
  const nodes = new Set(), timers = new Map();
  const visible = () => !page?.hidden;
  const ready = () => !disposed && visible() && verified && context?.state === 'running';
  const report = (event, extra = {}) => log(event, {
    state: context?.state || 'uninitialized', visibility: page?.visibilityState || (visible() ? 'visible' : 'hidden'),
    currentTime: context?.currentTime ?? null, ...extra,
  });
  const publish = value => { verified = value; if (!disposed) changed(ready()); };
  const stopNodes = () => {
    for (const node of nodes) {
      node.oscillator.onended = null;
      try { node.oscillator.stop(); } catch { /* Already stopped. */ }
      try { node.oscillator.disconnect(); node.gain.disconnect(); }
      catch (error) { report('cleanup-error', { errorName: error.name, errorMessage: error.message }); }
    }
    nodes.clear();
  };
  const retire = reason => {
    generation++; activation = null; publish(false); stopNodes();
    for (const [timer, cancel] of timers) { clearTimeout(timer); cancel(); }
    timers.clear();
    const previous = context;
    report('context-retired', { reason }); context = null;
    if (previous) {
      previous.onstatechange = null;
      try { Promise.resolve(previous.close()).catch(error => report('close-error', { errorName: error.name, errorMessage: error.message })); }
      catch (error) { report('close-error', { errorName: error.name, errorMessage: error.message }); }
    }
  };
  const delay = milliseconds => new Promise(resolve => {
    const timer = setTimeout(() => { timers.delete(timer); resolve(); }, milliseconds);
    timers.set(timer, resolve);
  });
  async function resumeAndCheck(target, ticket, source) {
    let timer;
    const resumed = target.resume(); // Synchronous call within the tap's activation, before any await.
    try {
      await Promise.race([resumed, new Promise((_resolve, reject) => {
        timer = setTimeout(() => { timers.delete(timer); reject(new Error('Sound activation timed out. Tap to Enable Sound again.')); }, timeoutMs);
        timers.set(timer, () => reject(new Error('Audio activation cancelled.')));
      })]);
      if (disposed || ticket !== generation || target !== context || !visible()) return false;
      if (target.state !== 'running') throw new Error('Sound is interrupted. Tap to Enable Sound again.');
      const start = target.currentTime;
      await delay(probeMs);
      if (disposed || ticket !== generation || target !== context || !visible()) return false;
      if (target.state !== 'running' || !(target.currentTime > start)) throw new Error('Audio clock is stalled. Tap to Enable Sound to start a fresh audio session.');
      publish(true); report('resume-verified', { source }); return true;
    } finally { clearTimeout(timer); timers.delete(timer); }
  }
  function activate(source) {
    if (disposed || !visible()) return Promise.resolve(false);
    if (activation) return activation;
    if (!Context) return Promise.reject(new Error('Web Audio is unavailable in this browser.'));
    // A user tap may rebuild an interrupted, closed or otherwise invalid engine.
    if (context && !ready() && source === 'gesture') retire('gesture-rebuild');
    if (!context) {
      if (source !== 'gesture') { publish(false); report('gesture-required', { source }); return Promise.resolve(false); }
      try {
        context = new Context();
        const target = context;
        context.onstatechange = () => {
          if (disposed || target !== context) return;
          report('statechange');
          if (target.state !== 'running') { publish(false); stopNodes(); }
        };
        report('context-created');
      } catch (error) { publish(false); report('creation-error', { errorName: error.name, errorMessage: error.message }); return Promise.reject(error); }
    }
    const target = context, ticket = generation;
    if (!ready()) publish(false);
    report('resume-request', { source });
    const operation = resumeAndCheck(target, ticket, source).catch(error => {
      if (!disposed && ticket === generation && target === context) {
        report('resume-error', { source, errorName: error.name, errorMessage: error.message }); retire('resume-failed');
      }
      throw error;
    }).finally(() => { if (activation === operation) activation = null; });
    activation = operation; return operation;
  }
  const restore = event => {
    report(event.type, { persisted: !!event.persisted });
    if (!visible()) return;
    // Do not create/unlock a new context without a tap, or replay sounds missed while hidden.
    if (!context) { publish(false); report('gesture-required', { source: event.type }); return; }
    activate(event.type).catch(() => {}); // Diagnostics + visible enable control handle blocked resume.
  };
  const visibility = () => {
    report('visibilitychange');
    if (!visible()) retire('page-hidden'); else restore({ type: 'foreground' });
  };
  const hide = event => { report('pagehide', { persisted: !!event.persisted }); retire('pagehide'); };
  page?.addEventListener('visibilitychange', visibility);
  host?.addEventListener('pagehide', hide);
  host?.addEventListener('pageshow', restore);
  host?.addEventListener('focus', restore);
  publish(false); report('engine-created');
  return {
    ready,
    enable: () => activate('gesture'),
    play(settings) {
      if (!ready()) { publish(false); report('play-blocked'); return false; }
      const { sound, volume } = normalizeAlertSettings(settings);
      try {
        [0, 0.24].forEach((offset, index) => {
          const oscillator = context.createOscillator();
          // Register immediately so partial graph failures also dispose the oscillator.
          const node = { oscillator, gain: { disconnect() {} } }; nodes.add(node);
          const gain = context.createGain(); node.gain = gain;
          const start = context.currentTime + offset;
          oscillator.frequency.value = sound === 'chime' ? [660, 880][index] : 880;
          gain.gain.setValueAtTime(0, start);
          gain.gain.linearRampToValueAtTime(volume * 0.3, start + 0.015);
          gain.gain.exponentialRampToValueAtTime(0.001, start + 0.19);
          oscillator.connect(gain); gain.connect(context.destination);
          oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); nodes.delete(node); };
          oscillator.start(start); oscillator.stop(start + 0.2);
        });
        report('play-scheduled', { sound, volume });
        // Detect WebKit's running-but-frozen clock even if resume() previously succeeded.
        const target = context, ticket = generation, start = context.currentTime;
        delay(probeMs).then(() => {
          if (!disposed && ticket === generation && target === context && visible() && (!(target.currentTime > start) || target.state !== 'running')) {
            report('play-clock-stalled'); retire('play-clock-stalled');
          }
        });
        return true;
      } catch (error) {
        report('play-error', { errorName: error.name, errorMessage: error.message }); retire('play-failed'); return false;
      }
    },
    close() {
      if (disposed) return;
      page?.removeEventListener('visibilitychange', visibility);
      host?.removeEventListener('pagehide', hide);
      host?.removeEventListener('pageshow', restore);
      host?.removeEventListener('focus', restore);
      disposed = true; retire('cleanup');
    },
  };
}
