import {useEffect,useRef,useState} from 'react';
import {useOrdersRuntime} from '../runtime';
import useOrderMonitor from './useOrderMonitor';
import {alertDefaults,normalizeAlertSettings,createSoundEngine} from '../audio/alerts';
import {createPrinter,printerDefaults} from '../printing/epos';

// One controller per authenticated shell session, never one per page/modal.
export default function useOrdersController(){
 const {user,authorization,readPreference,writePreference}=useOrdersRuntime();
 const [settings,setSettings]=useState(()=>normalizeAlertSettings(readPreference('alerts',alertDefaults)));
 const [config,setConfig]=useState(()=>({...printerDefaults,...readPreference('printer',{})}));
 const [audioReady,setAudioReady]=useState(false),[notice,setNotice]=useState('');
 const configRef=useRef(config);configRef.current=config;
 const settingsRef=useRef(settings);settingsRef.current=settings;
 const sound=useRef(null),printer=useRef(null);if(!printer.current)printer.current=createPrinter({getConfig:()=>configRef.current});
 useEffect(()=>{sound.current=createSoundEngine(window.AudioContext||window.webkitAudioContext,setAudioReady);return()=>{sound.current?.close();sound.current=null;};},[]);
 const monitor=useOrderMonitor(authorization,user.role,ids=>{
  const engine=sound.current;
  engine?.notifyPending(ids,()=>settingsRef.current).then(played=>{if(!played&&sound.current===engine&&!engine.ready())setAudioReady(false);});
 });
 async function enableSound(){const engine=sound.current;try{
  if(!await engine.enable()||sound.current!==engine)return;
  if(!engine.play(settingsRef.current))throw Error('Sound could not play. Tap to Enable Sound again.');
  setNotice('Test sound scheduled. Confirm that you heard it using the iPad volume controls.');
 }catch(error){if(sound.current===engine){setAudioReady(false);setNotice(error.message);}}}
 function savePrinter(next){if(!writePreference('printer',next))throw Error('Device storage unavailable; profile not saved.');configRef.current=next;setConfig(next);}
 return {monitor,settings,setSettings,config,setConfig,audioReady,notice,setNotice,enableSound,printer:printer.current,savePrinter};
}
