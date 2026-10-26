import {orderTime} from '../model/presentation';
export default function OrderMonitorStatus({controller}){
 const {monitor,audioReady,enableSound,notice,setNotice}=controller;
 return <>
      <div className="sp-monitor" role="status"><span className={monitor.error || !monitor.foreground ? 'sp-dot sp-dot-warning' : 'sp-dot'} />{!monitor.foreground ? 'Monitoring paused while hidden' : monitor.error ? 'Monitoring unavailable · stale data' : monitor.lastSync ? 'Foreground monitoring' : 'Waiting for first synchronization'}{monitor.lastSync && ` · Last sync ${orderTime(monitor.lastSync)} (Melbourne)`}<span className="sp-sound-status">{audioReady ? 'Sound enabled' : 'Sound not enabled'}</span><button onClick={enableSound}>{audioReady ? 'Test Sound' : 'Tap to Enable Sound'}</button></div>
      {monitor.unread > 0 && <div className="sp-alert" role="status">{monitor.unread} new pending order(s). Acknowledging does not accept an order.<button onClick={monitor.acknowledge}>Acknowledge</button></div>}
      {notice && <div className="sp-notice" role="status">{notice}<button aria-label="Dismiss notice" onClick={() => setNotice('')}>✕</button></div>}
 </>;
}
