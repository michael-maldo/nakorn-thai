import {useRef,useState} from 'react';
import {readAudioDiagnostics} from '../audio/alerts';
import {useOrdersRuntime} from '../runtime';
export default function AudioSettings({controller,version}){
 const {settings,setSettings,audioReady,enableSound,setNotice}=controller;
 const {writePreference}=useOrdersRuntime();
  const [audioDiagnostics, setAudioDiagnostics] = useState(null);
  const [diagnosticCopyStatus, setDiagnosticCopyStatus] = useState('');
  const diagnosticText = useRef(null);
  function refreshAudioDiagnostics() {
    const entries = readAudioDiagnostics();
    setAudioDiagnostics({ text: JSON.stringify({ version: version, capturedAt: new Date().toISOString(), entries }, null, 2), hasEntries: entries.length > 0 });
    setDiagnosticCopyStatus('');
  }
  function selectDiagnostics() {
    diagnosticText.current?.focus();
    diagnosticText.current?.select();
    diagnosticText.current?.setSelectionRange(0, diagnosticText.current.value.length);
  }
  async function copyDiagnostics() {
    if (!audioDiagnostics?.hasEntries) return;
    try {
      // Invoke immediately in the tap: no refresh, permissions request or await before writing.
      await navigator.clipboard.writeText(audioDiagnostics.text);
      setDiagnosticCopyStatus('Diagnostics copied');
    } catch {
      setDiagnosticCopyStatus('Could not copy automatically. Tap Select diagnostic text, then touch and hold the selected text and choose Copy.');
      selectDiagnostics();
    }
  }
 return <><div className="sp-settings-card"><h2>Incoming-order alerts</h2><p>{audioReady ? 'Sound enabled for this session' : 'Tap to Enable Sound at shift start or if automatic recovery needs activation.'}</p><div className="sp-actions"><button onClick={enableSound}>Enable Sound</button><button onClick={enableSound}>Test Sound</button></div><label>Alert sound<select value={settings.sound} onChange={event => { const next = { ...settings, sound: event.target.value }; setSettings(next); if (!writePreference('alerts', next)) setNotice('Sound preference could not be saved.'); }}><option value="double">Double beep</option><option value="chime">Chime</option></select></label><label>Alert volume · {Math.round(settings.volume * 100)}%<input type="range" min="0" max="1" step="0.05" value={settings.volume} onChange={event => { const next = { ...settings, volume: Number(event.target.value) }; setSettings(next); if (!writePreference('alerts', next)) setNotice('Volume preference could not be saved.'); }} /></label><p>Keep this app visible and the iPad awake during service. The app cannot override device volume, Silent Mode, Focus or background suspension. Background push notifications are not configured.</p></div><div className="sp-settings-card"><h2>Audio diagnostics</h2><p>Local technical log only. Capture after unlocking and before tapping Enable Sound. No background alarms are supported.</p><div className="sp-actions"><button onClick={refreshAudioDiagnostics}>Refresh audio diagnostics</button><button disabled={!audioDiagnostics?.hasEntries} onClick={copyDiagnostics}>Copy Diagnostics</button></div>{diagnosticCopyStatus && <p role="status">{diagnosticCopyStatus}</p>}{diagnosticCopyStatus.startsWith('Could not copy') && <button onClick={selectDiagnostics}>Select diagnostic text</button>}{audioDiagnostics && <textarea ref={diagnosticText} aria-label="Audio diagnostics" readOnly rows={14} style={{ width: '100%', boxSizing: 'border-box' }} value={audioDiagnostics.text} />}</div></>;
}
