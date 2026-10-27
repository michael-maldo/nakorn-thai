import OrderMonitorStatus from './OrderMonitorStatus';
import {useEffect,useRef,useState} from 'react';
import OrderBoard from './OrderBoard';
import OrderDetails from './OrderDetails';
import PaymentReviewPanel from './PaymentReviewPanel';
import {useOrdersRuntime} from '../runtime';
import {printingEnabled} from '../printing/epos';
import {orderDraftDirty} from '../model/staffOrderDrafts';
import '../orders.css';

// The shell supplies direct links and the refresh placement. There is one active monitor.
export default function OrdersPage({controller,linked={},Refresh=({children})=>children,onBack,orderHref,showStatus=true}){
 const {user,authorization,api}=useOrdersRuntime();
 const {monitor,printer,config,audioReady,enableSound,notice,setNotice}=controller;
 const [revision,setRevision]=useState(0);
 const [selected,setSelected]=useState(linked.orderId||null),[history,setHistory]=useState(false),[rows,setRows]=useState([]),[filter,setFilter]=useState('ALL'),[loading,setLoading]=useState(false),[error,setError]=useState('');
 const drafts=useRef({}),read=useRef(0),pending=useRef(false);
 useEffect(()=>()=>{read.current++;},[]);
 async function loadHistory(){if(pending.current)return;pending.current=true;const ticket=++read.current;setLoading(true);setError('');try{const data=await api.getStaffOrders(authorization,false,true);if(ticket===read.current)setRows(data);}catch(e){if(ticket===read.current)setError(e.message);}finally{pending.current=false;if(ticket===read.current)setLoading(false);}}
 const refresh=()=>history?loadHistory():monitor.refresh();
 return <section className="shared-staff-orders">
 {showStatus&&<OrderMonitorStatus controller={controller}/>}
 {linked.invalid&&<p role="alert">This order link is invalid. Choose an order from the queue.</p>}
 {linked.orderId&&<button onClick={()=>{setSelected(null);onBack?.();}}>Back to all orders</button>}
 {user.role!=='BOH'&&<><label>Queue<select value={history?'history':'active'} onChange={e=>{const next=e.target.value==='history';setHistory(next);setFilter('ALL');if(next)loadHistory();}}><option value="active">Active orders</option><option value="history">History (placed in the last 24 hours)</option></select></label><PaymentReviewPanel authorization={authorization} request={api.paymentRequest} orderHref={orderHref} onOrder={orderHref?undefined:setSelected}/></>}
 <Refresh><button type="button" disabled={history?loading:monitor.loading} onClick={refresh}>Refresh</button></Refresh>
 {history?<><h1>Order history</h1><label>Status<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All states</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option></select></label>{loading&&<p role="status">Loading history…</p>}{error&&<p role="alert">{error} Use Refresh to retry.</p>}{!loading&&!rows.length&&<p>No orders in this view.</p>}{rows.filter(row=>filter==='ALL'||row.status===filter).map(row=><button key={row.id} onClick={()=>setSelected(row.id)}>#{row.reference} · {row.status}</button>)}</>:<><label>Status<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="ALL">All states</option><option value="NEW">New</option><option value="ACCEPTED">Accepted</option><option value="PREPARING">Preparing</option><option value="READY">Ready for pickup</option></select></label><OrderBoard monitor={monitor} role={user.role} onOrder={setSelected} showRefresh={false} initialStatus="NEW" statusFilter={filter} onFilter={setFilter}/></>}
 {Object.entries(drafts.current).filter(([,value])=>Object.values(value).some(orderDraftDirty)).map(([id])=><div key={id}>Retained order draft · {id}<button onClick={()=>setSelected(id)}>Resume order draft</button><button onClick={()=>{delete drafts.current[id];setSelected(null);setRevision(x=>x+1);}}>Discard retained draft</button></div>)}
 {selected&&<OrderDetails key={selected} id={selected} initialDrafts={drafts.current[selected]} onDrafts={value=>{drafts.current[selected]=value;}} onClose={()=>setSelected(null)} onChanged={monitor.refresh} monitoringError={monitor.error} printer={printer} config={user.role==='BOH'?{...config,layout:'kitchen',autoPrint:false}:config} canPrint={printingEnabled&&config.deviceEnabled===true&&!!config.endpoint}/>}
 </section>;
}
