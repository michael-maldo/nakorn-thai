// Match Android NewOrderTracker: a silent baseline, then one alert per new ID.
export function createPendingTracker() {
  const seen = new Set(), unread = new Set();
  let initialized = false, arrivals = [];
  return {
    accept(orders) {
      const pending = new Set(); let arrived = false; arrivals = [];
      for (const order of orders) {
        if (order.status === 'NEW') {
          pending.add(order.id);
          if (initialized && !seen.has(order.id)) { unread.add(order.id); arrivals.push(order.id); arrived = true; }
        }
        seen.add(order.id);
      }
      for (const id of unread) if (!pending.has(id)) unread.delete(id);
      initialized = true;
      return { arrived, count: unread.size };
    },
    arrivals() { return [...arrivals]; },
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
  let enabledThisShift = false, automaticBlocked = false, activationSource = null;
  let notification = null, notificationEpoch = 0;
  const notified = new Set();
  const nodes = new Set(), timers = new Map();
  const visible = () => !page?.hidden;
  const ready = () => !disposed && visible() && verified && context?.state === 'running';
  const report = (event, extra = {}) => log(event, {
    timestamp: new Date().toISOString(), state: context?.state || 'uninitialized', visibility: page?.visibilityState || (visible() ? 'visible' : 'hidden'),
    currentTime: context?.currentTime ?? null, enabledThisShift, automaticBlocked,
    activationSource, generation, hasFocus: page?.hasFocus?.() ?? null,
    userActivation: host?.navigator?.userActivation?.isActive ?? null, ...extra,
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
  const retire = (reason, preserveActivation = false) => {
    generation++; if (!preserveActivation) activation = null; publish(false); stopNodes();
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
  const failure = (outcome, message) => Object.assign(new Error(message), { outcome });
  async function resumeAndCheck(target, ticket, deadline) {
    let timer;
    try {
      // Called synchronously before the first await; manual activation stays inside the tap.
      report('resume-called', { targetState: target.state });
      await Promise.race([target.resume(), new Promise((_resolve, reject) => {
        timer = setTimeout(() => { timers.delete(timer); reject(failure('timed-out', 'Sound activation timed out. Tap to Enable Sound again.')); }, Math.max(0, deadline - Date.now()));
        timers.set(timer, () => reject(failure('cancelled', 'Audio activation cancelled.')));
      })]);
      if (disposed || ticket !== generation || target !== context || !visible()) { report('recovery-cancelled', { reason: 'resource-or-visibility-changed' }); return false; }
      report('resume-resolved', { targetState: target.state });
      if (target.state !== 'running') throw failure(target.state === 'interrupted' ? 'interrupted' : 'blocked', 'Sound is interrupted or requires activation. Tap to Enable Sound again.');
      const start = target.currentTime;
      await delay(Math.min(probeMs, Math.max(0, deadline - Date.now())));
      if (disposed || ticket !== generation || target !== context || !visible()) { report('recovery-cancelled', { reason: 'resource-or-visibility-changed' }); return false; }
      if (Date.now() >= deadline) throw failure('timed-out', 'Audio readiness check timed out. Tap to Enable Sound again.');
      if (target.state !== 'running') throw failure('interrupted', 'Sound was interrupted. Tap to Enable Sound again.');
      report('clock-probe', { startTime: start, endTime: target.currentTime, advanced: target.currentTime > start });
      if (!(target.currentTime > start)) throw failure('stalled', 'Audio clock is stalled. Tap to Enable Sound to start a fresh audio session.');
      return true;
    } finally { clearTimeout(timer); timers.delete(timer); }
  }
  const classify = error => error.outcome || (error.name === 'NotAllowedError' ? 'blocked' : 'failed');
  function createContext() {
    report('context-create-request');
    try { context = new Context(); }
    catch (error) { report('context-create-failed', { errorName: error.name, errorMessage: error.message }); throw error; }
    const target = context;
    context.onstatechange = () => {
      if (disposed || target !== context) return;
      report('statechange');
      if (target.state !== 'running') { publish(false); stopNodes(); }
    };
    report('context-created');
  }
  function activate(source) {
    const manual = source === 'gesture';
    report('recovery-request', { source });
    if (disposed || !visible()) { report('recovery-skipped', { source, reason: disposed ? 'disposed' : 'page-hidden' }); return Promise.resolve(false); }
    if (activation) {
      if (!manual || activationSource === 'gesture') { report('recovery-shared', { source }); return activation; }
      // A tap must not wait behind Safari's possibly gesture-blocked automatic resume.
      retire('manual-preempts-recovery');
    }
    if (!manual && (!enabledThisShift || automaticBlocked)) {
      publish(false);
      report('recovery-skipped', { source, reason: !enabledThisShift ? 'missing-prior-activation' : 'failure-latch' });
      report('manual-activation-required', { source, reason: !enabledThisShift ? 'missing-prior-activation' : 'failure-latch' });
      return Promise.resolve(false);
    }
    if (manual) { automaticBlocked = false; report('failure-latch-reset', { source }); notificationEpoch++; notification = null; }
    if (context && (context.state === 'closed' || manual && !ready())) retire('rebuild');
    const deadline = Date.now() + timeoutMs;
    let ticket = generation;
    activationSource = source;
    if (!ready()) publish(false);
    report('resume-request', { source });
    const recover = async () => {
      // One existing context plus at most one replacement. Never retry a denied/hung resume.
      const hadContext = !!context;
      for (let attempt = 0; attempt < (hadContext ? 2 : 1); attempt++) {
        try {
          if (!Context) throw failure('failed', 'Web Audio is unavailable in this browser.');
          if (!context) createContext();
          if (!await resumeAndCheck(context, ticket, deadline)) return false;
          if (manual) enabledThisShift = true;
          automaticBlocked = false; publish(true);
          report('recovery-outcome', { source, outcome: 'successful' }); return true;
        } catch (error) {
          if (disposed || ticket !== generation || !visible()) return false;
          const outcome = classify(error);
          report('resume-error', { source, outcome, errorName: error.name, errorMessage: error.message });
          retire('resume-failed', true); ticket = generation;
          if (attempt === 0 && hadContext && !['blocked', 'timed-out', 'cancelled'].includes(outcome) && Date.now() < deadline) continue;
          automaticBlocked = true;
          report('failure-latch-set', { source, outcome });
          report('manual-activation-required', { source, reason: outcome });
          report('recovery-outcome', { source, outcome });
          if (manual) throw error;
          return false;
        }
      }
      return false;
    };
    const operation = recover().finally(() => { if (activation === operation) { activation = null; activationSource = null; } });
    activation = operation; return operation;
  }
  const restore = event => {
    report(event.type, { persisted: !!event.persisted });
    if (!visible()) { report('recovery-skipped', { source: event.type, reason: 'page-hidden' }); return; }
    // Automatic recovery schedules no tones and obeys the browser's activation policy.
    activate(event.type).catch(() => {});
  };
  const visibility = () => {
    report('visibilitychange');
    if (!visible()) { notificationEpoch++; notification = null; retire('page-hidden'); } else restore({ type: 'foreground' });
  };
  const hide = event => { report('pagehide', { persisted: !!event.persisted }); notificationEpoch++; notification = null; retire('pagehide'); };
  page?.addEventListener('visibilitychange', visibility);
  host?.addEventListener('pagehide', hide);
  host?.addEventListener('pageshow', restore);
  host?.addEventListener('focus', restore);
  publish(false); report('engine-created');
  const engine = {
    ready,
    recover: () => activate('incoming-order'),
    enable: () => activate('gesture'),
    play(settings) {
      if (!ready()) { publish(false); report('play-blocked'); return false; }
      report('play-attempt');
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
    notifyPending(ids, getSettings) {
      // Reserve immediately, before recovery. IDs stay only in memory and never enter diagnostics.
      const fresh = ids.filter(id => !notified.has(id));
      for (const id of fresh) notified.add(id);
      if (!fresh.length || disposed || !visible()) {
        report('notification-skipped', { reason: !fresh.length ? 'no-new-notification' : disposed ? 'disposed' : 'page-hidden' });
        return Promise.resolve(false);
      }
      report('notification-request');
      // Preserve the existing one-tone-per-poll-batch behavior, coalescing concurrent batches.
      if (notification) return notification;
      const epoch = notificationEpoch;
      const operation = (async () => {
        // Recheck the clock even for 'running' resources; Safari can freeze them silently.
        if (!await activate('incoming-order')) return false;
        if (disposed || !visible() || epoch !== notificationEpoch) return false;
        return engine.play(getSettings());
      })().catch(error => { report('notification-failed', { outcome: classify(error) }); return false; })
        .finally(() => { if (notification === operation) notification = null; });
      notification = operation; return operation;
    },
    close() {
      if (disposed) return;
      page?.removeEventListener('visibilitychange', visibility);
      host?.removeEventListener('pagehide', hide);
      host?.removeEventListener('pageshow', restore);
      host?.removeEventListener('focus', restore);
      disposed = true; notificationEpoch++; notified.clear(); retire('cleanup');
    },
  };
  return engine;
}
