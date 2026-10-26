import {useEffect,useRef,useState} from 'react';
import {useAuth} from './features/identity/model/AuthContext';
import {staffIdentityContext} from './features/identity/api/identityApi';
import useProtoRoute from './app/useProtoRoute';
import {resolveRoute,visibleRoutes} from './app/routes';
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
import Icon from './components/Icon';
import Sheet from './components/Sheet';
import {registerPrototypeWorker} from './pwa/register';
const storageKey='nakorn-staff-pwa-proto:sidebar';
function readSidebar(){try{return localStorage.getItem(storageKey)!=='collapsed';}catch{return true;}}
export default function StaffProtoApp(){
 return <Workspace/>;
}
function Workspace(){
 const {user,logout}=useAuth(),hash=useProtoRoute(),route=resolveRoute(hash);
 const [expanded,setExpanded]=useState(readSidebar),[sheet,setSheet]=useState(null),[busy,setBusy]=useState(false),[update,setUpdate]=useState(null),[notice,setNotice]=useState('');
 const content=useRef(null),context=staffIdentityContext();
 const pageOwner=`${user.id}:${user.role}:${context?.sessionId || 'unknown'}`;
 useEffect(()=>{setSheet(null);content.current?.focus();},[hash]);
 useEffect(()=>{let disposed=false,cleanup;registerPrototypeWorker(activate=>{if(!disposed)setUpdate(()=>activate);}).then(stop=>{if(disposed)stop();else cleanup=stop;}).catch(()=>setNotice('Update check unavailable. Try again when online.'));return()=>{disposed=true;cleanup?.();};},[]);
 function toggle(){setExpanded(value=>{try{localStorage.setItem(storageKey,value?'collapsed':'expanded');}catch{}return !value;});}
 async function signOut(approved=false){if(!approved&&!allowMenuNavigation(undefined,undefined,()=>signOut(true)))return;setBusy(true);try{await logout();}finally{setBusy(false);}}
 const nav=<>{visibleRoutes(user.role).map(item=><a key={item.path} href={item.path} title={item.label} aria-current={route?.path===item.path?'page':undefined}><Icon name={item.icon}/><span>{item.label}</span></a>)}</>;
 let page;
 if(!route)page=<section><h1>Page unavailable</h1><a href="#/staff">Return home</a></section>;
 else if(!route.roles.includes(user.role))page=<section><h1>Access restricted</h1><p>This page is unavailable for your role.</p><a href="#/staff">Return home</a></section>;
 else switch(route.path){
 case '#/staff':page=<StaffDashboardPage/>;break;
 case '#/staff/orders':page=<OrdersAdminPage key={hash} linked={orderLink(hash)}/>;break;
 case '#/staff/reservations':page=<ReservationAdminPage/>;break;
 case '#/staff/functions':page=<FunctionEnquiriesPage/>;break;
 case '#/staff/menu':page=<StaffMenuPage hash={hash}/>;break;
 case '#/staff/homepage':page=<HomepageSettingsPage/>;break;
 case '#/staff/ordering':page=<OnlineOrderingPage/>;break;
 case '#/staff/restaurant':page=<RestaurantSchedulePage/>;break;
 case '#/staff/settings':page=<IntegrationSettingsPage/>;break;
 case '#/staff/users':page=<UsersPage/>;break;
 }
 return <div className={`proto-app ${expanded?'':'proto-collapsed'}`}>
 <a className="proto-skip" href="#proto-content" onClick={e=>{e.preventDefault();content.current?.focus();}}>Skip to content</a>
 <header className="proto-header"><button className="proto-rail-toggle" aria-label={expanded?'Collapse sidebar':'Expand sidebar'} aria-expanded={expanded} onClick={toggle}><Icon name="manage"/></button><button className="proto-drawer-toggle" aria-label="Open navigation" onClick={()=>setSheet('navigation')}><Icon name="manage"/></button><a className="proto-brand" href="#/staff"><span className="proto-monogram">NT</span><strong>NAKORN THAI<small>Staff prototype</small></strong></a><span className="proto-identity">{user.username} · {user.role}</span><button disabled={busy} onClick={()=>signOut()}>Sign out</button></header>
 <div className="proto-body"><aside className="proto-sidebar"><p>WORKSPACE</p><nav aria-label="Staff navigation">{nav}</nav><small>{user.username}<br/>{user.role}</small></aside><div ref={content} id="proto-content" className="proto-content" tabIndex={-1}>{notice&&<p role="status">{notice}</p>}{update&&<p role="status">A prototype update is ready. <button onClick={()=>{if(allowMenuNavigation()){navigator.serviceWorker.addEventListener('controllerchange',()=>location.reload(),{once:true});update();}}}>Update prototype</button></p>}{<div key={pageOwner}>{page}</div>}</div></div>
 <footer className="proto-footer"><a href="#/staff" aria-current={route?.path==='#/staff'?'page':undefined}><Icon name="home"/>Home</a><a href="#/staff/orders" aria-current={route?.path==='#/staff/orders'?'page':undefined}><Icon name="orders"/>Orders</a><button onClick={()=>setSheet('manage')}><Icon name="manage"/>Manage</button></footer>
 {sheet&&<Sheet title={sheet==='manage'?'Manage':'Workspace navigation'} onClose={()=>setSheet(null)}><nav className="proto-sheet-nav" aria-label="Management navigation">{nav}</nav><p>Printing is disabled. Online access is required.</p><button disabled={busy} onClick={()=>signOut()}>Sign out</button></Sheet>}
 </div>;
}
