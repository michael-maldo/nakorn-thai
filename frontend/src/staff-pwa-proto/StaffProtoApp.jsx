import OrderMonitorStatus from '../shared/staff-orders/components/OrderMonitorStatus';
import {OrdersRuntimeProvider} from '../shared/staff-orders/runtime';
import useOrdersController from '../shared/staff-orders/hooks/useOrdersController';
import AudioSettings from '../shared/staff-orders/components/AudioSettings';
import PrinterSettings from './features/printing/PrinterSettings';
import {paymentRequest} from './features/payment/api/paymentApi';
import * as orderApi from './features/ordering/api/orderApi';
import {readPreference,writePreference} from './app/orderPreferences';
import {useEffect,useRef,useState} from 'react';
import {useAuth} from './features/identity/model/AuthContext';
import {staffIdentityContext} from './features/identity/api/identityApi';
import useProtoRoute from './app/useProtoRoute';
import {resolveRoute,operationRoutes,managementRoutes} from './app/routes';
import {allowMenuNavigation} from './features/menu/model/menuAdminNavigation';
import {orderLink} from './features/ordering/model/orderLinks';
import StaffDashboardPage from './features/staff/pages/StaffDashboardPage';
import OrdersAdminPage from './features/ordering/pages/OrdersAdminPage';
import ReservationAdminPage from './features/reservation/pages/ReservationAdminPage';
import FunctionEnquiriesPage from './features/staff/pages/FunctionEnquiriesPage';
import StaffMenuPage from './features/staff/pages/StaffMenuPage';
import HomepageSettingsPage from './features/menu/pages/HomepageSettingsPage';
import OnlineOrderingPage from './features/restaurant/pages/OnlineOrderingPage';
import RestaurantSchedulePage from './features/restaurant/pages/RestaurantSchedulePage';
import IntegrationSettingsPage from './features/restaurant/pages/IntegrationSettingsPage';
import UsersPage from './features/identity/pages/UsersPage';
import {PageActionTarget} from './components/PageAction';
import Icon from './components/Icon';
import Sheet from './components/Sheet';
import {registerPrototypeWorker} from './pwa/register';
import {consumeAssistantCallback,markAssistantOutcomeUnknown} from './features/printing/assistantTransport';
const storageKey='nakorn-staff-pwa-proto:sidebar';
function readSidebar(){try{return localStorage.getItem(storageKey)!=='collapsed';}catch{return true;}}
export default function StaffProtoApp(){
 const auth=useAuth(),context=staffIdentityContext();
 return <OrdersRuntimeProvider value={{...auth,identity:staffIdentityContext,api:{...orderApi,paymentRequest},readPreference,writePreference}}><OrdersSession key={`${auth.user.id}:${auth.user.role}:${context?.sessionId||'unknown'}`}/></OrdersRuntimeProvider>;
}
function OrdersSession(){const orders=useOrdersController();return <Workspace orders={orders}/>;}
function Workspace({orders}){
 const {user,logout}=useAuth(),hash=useProtoRoute(),route=resolveRoute(hash);
 const [expanded,setExpanded]=useState(readSidebar),[sheet,setSheet]=useState(null),[busy,setBusy]=useState(false),[update,setUpdate]=useState(null),[notice,setNotice]=useState('');
 const [actionTarget,setActionTarget]=useState(null);
 const content=useRef(null),context=staffIdentityContext();
 const pageOwner=`${user.id}:${user.role}:${context?.sessionId || 'unknown'}`;
 useEffect(()=>{setSheet(null);content.current?.focus();},[hash]);
 useEffect(()=>{let disposed=false,cleanup;registerPrototypeWorker(activate=>{if(!disposed)setUpdate(()=>activate);}).then(stop=>{if(disposed)stop();else cleanup=stop;}).catch(()=>setNotice('Update check unavailable. Try again when online.'));return()=>{disposed=true;cleanup?.();};},[]);
 useEffect(()=>{
  try{const result=consumeAssistantCallback(window.location.search);if(result)setNotice(result.message);}catch{}
  const onReturn=()=>{if(document.visibilityState&&document.visibilityState!=='visible')return;try{const result=markAssistantOutcomeUnknown();if(result)setNotice(result.message);}catch{setNotice('Print outcome unknown. Inspect the printer before any deliberate manual reprint.');}};
  document.addEventListener('visibilitychange',onReturn);window.addEventListener('pageshow',onReturn);window.addEventListener('focus',onReturn);
  return()=>{document.removeEventListener('visibilitychange',onReturn);window.removeEventListener('pageshow',onReturn);window.removeEventListener('focus',onReturn);};
 },[]);
 function toggle(){setExpanded(value=>{try{localStorage.setItem(storageKey,value?'collapsed':'expanded');}catch{}return !value;});}
 async function signOut(approved=false){if(!approved&&!allowMenuNavigation(undefined,undefined,()=>signOut(true)))return;setBusy(true);try{await logout();}finally{setBusy(false);}}
 const operations=operationRoutes(user.role),management=managementRoutes(user.role);
 const nav=items=><>{items.map(item=><a key={item.path} href={item.path} title={item.label} aria-current={route?.path===item.path?'page':undefined}><Icon name={item.icon}/><span>{item.label}</span></a>)}</>;
 let page;
 if(!route)page=<section><h1>Page unavailable</h1><a href="#/staff">Return home</a></section>;
 else if(!route.roles.includes(user.role))page=<section><h1>Access restricted</h1><p>This page is unavailable for your role.</p><a href="#/staff">Return home</a></section>;
 else switch(route.path){
 case '#/staff':page=<StaffDashboardPage/>;break;
 case '#/staff/orders':page=<OrdersAdminPage key={hash} controller={orders} linked={orderLink(hash)}/>;break;
 case '#/staff/reservations':page=<ReservationAdminPage/>;break;
 case '#/staff/functions':page=<FunctionEnquiriesPage/>;break;
 case '#/staff/menu':page=<StaffMenuPage hash={hash}/>;break;
 case '#/staff/homepage':page=<HomepageSettingsPage/>;break;
 case '#/staff/ordering':page=<OnlineOrderingPage/>;break;
 case '#/staff/restaurant':page=<RestaurantSchedulePage/>;break;
 case '#/staff/settings':page=<IntegrationSettingsPage orders={orders}/>;break;
 case '#/staff/users':page=<UsersPage/>;break;
 }
 return <PageActionTarget.Provider value={actionTarget}><div className={`proto-app ${expanded?'':'proto-collapsed'}`}>
 <a className="proto-skip" href="#proto-content" onClick={e=>{e.preventDefault();content.current?.focus();}}>Skip to content</a>
 <header className="proto-header"><button className="proto-rail-toggle" aria-label={expanded?'Collapse sidebar':'Expand sidebar'} aria-expanded={expanded} onClick={toggle}><Icon name="manage"/></button><button className="proto-drawer-toggle" aria-label="Open navigation" onClick={()=>setSheet('navigation')}><Icon name="manage"/></button><a className="proto-brand" href="#/staff"><span className="proto-monogram">NT</span><strong>NAKORN THAI<small>Staff prototype</small></strong></a><button className="proto-device" aria-label="Device settings" title="Device settings" aria-haspopup="dialog" onClick={()=>setSheet('device')}><Icon name="settings"/><span>Device settings</span></button><span className="proto-identity">{user.username} · {user.role}</span>{user.role==='ADMIN'&&management.length>0&&<button className="proto-manage" aria-haspopup="dialog" aria-expanded={sheet==='manage'} onClick={()=>setSheet('manage')}>Manage</button>}<button disabled={busy} onClick={()=>signOut()}>Sign out</button></header><div className="shared-staff-orders proto-order-status"><OrderMonitorStatus controller={orders}/></div>
 <div className="proto-body"><aside className="proto-sidebar"><p>OPERATIONS</p><nav aria-label="Staff navigation">{nav(operations)}</nav>{user.role!=='ADMIN'&&management.length>0&&<nav aria-label="Ordering controls">{nav(management)}</nav>}<small>{user.username}<br/>{user.role}</small></aside><div className="proto-workspace"><div ref={content} id="proto-content" className="proto-content" tabIndex={-1}>{notice&&<p role="status">{notice}</p>}{update&&<p role="status">A prototype update is ready. <button onClick={()=>{if(allowMenuNavigation()){navigator.serviceWorker.addEventListener('controllerchange',()=>location.reload(),{once:true});update();}}}>Update prototype</button></p>}{<div key={pageOwner}>{page}</div>}</div><div ref={setActionTarget} className="proto-action-bar" role="region" aria-label="Page refresh actions"/></div></div>

 {sheet&&<Sheet title={sheet==='manage'?'Manage':sheet==='device'?'Device sound & printing':'Workspace navigation'} onClose={()=>setSheet(null)}>{sheet==='device'?<div className="shared-staff-orders"><AudioSettings controller={orders} version="prototype"/><PrinterSettings config={orders.config} printer={orders.printer} onSave={orders.savePrinter}/></div>:<nav className="proto-sheet-nav" aria-label={sheet==='manage'?'Management navigation':'Operations navigation'} onClick={event=>{if(event.target.closest('a')&&!event.defaultPrevented)setSheet(null);}}>{nav(sheet==='manage'?management:operations)}</nav>}{sheet==='navigation'&&user.role!=='ADMIN'&&management.length>0&&<nav className="proto-sheet-nav" aria-label="Ordering controls" onClick={event=>{if(event.target.closest('a')&&!event.defaultPrevented)setSheet(null);}}>{nav(management)}</nav>}<p>Printing requires build capability, device enablement and a saved profile. Online access is required.</p><button disabled={busy} onClick={()=>signOut()}>Sign out</button></Sheet>}
 </div></PageActionTarget.Provider>;
}
