import {useEffect,useState} from 'react';
import Header from '../../../website/components/Header';
import {getReadOnlyOrder} from '../api/orderApi';
import {orderStates} from '../model/orderModel';
const money=n=>'AUD '+(n/100).toFixed(2);
const steps=['NEW','ACCEPTED','PREPARING','READY','COMPLETED'];
export default function ReadOnlyOrderTrackingPage({token}){
 const [order,setOrder]=useState(null),[error,setError]=useState('');
 useEffect(()=>{
  if(!token){setError('This order tracking link is missing or invalid. Use the complete link from your email, or contact Nakorn Thai.');return;}
  const controller=new AbortController();let timer;
  async function poll(){try{const next=await getReadOnlyOrder(token,controller.signal);if(controller.signal.aborted)return;setOrder(next);setError('');timer=setTimeout(poll,['COMPLETED','CANCELLED'].includes(next.status)?60000:5000);}
   catch(e){if(controller.signal.aborted)return;setError(e.message);if(e.status===404)setOrder(null);else timer=setTimeout(poll,10000);}}
  poll();return()=>{controller.abort();clearTimeout(timer);};
 },[token]);
 const date=value=>new Intl.DateTimeFormat('en-AU',{dateStyle:'medium',timeStyle:'short',timeZone:order.timezone}).format(new Date(value));
 return <><Header/><main className="restaurant-menu page-width read-only-tracking"><h1>Track your order</h1>
 {error&&<p role="alert" className="staff-error">{error}</p>}
 {!order&&!error&&<p role="status">Loading your order…</p>}
 {order&&<><section className="order-panel" aria-labelledby="order-progress"><h2 id="order-progress">Order progress</h2><p>Order #{order.reference}</p><p aria-live="polite">{orderStates[order.status]||order.status}</p>
 {order.status==='CANCELLED'?<p>Your order has been cancelled. Please contact Nakorn Thai if you need assistance.</p>:<ol className="tracking-progress">{steps.map((s,i)=><li key={s} aria-current={s===order.status?'step':undefined}>{i<=steps.indexOf(order.status)?'✓ ':''}{orderStates[s]}</li>)}</ol>}
 {order.status==='COMPLETED'&&<p>Your order is complete. Thank you for ordering with Nakorn Thai.</p>}
 {order.status==='READY'&&<p>Your order is ready for pickup at Nakorn Thai, 233 Glenferrie Rd, Malvern.</p>}
 {order.estimatedReadyAt&&!['COMPLETED','CANCELLED'].includes(order.status)&&<p>Estimated pickup: {date(order.estimatedReadyAt)}</p>}{order.updatedAt&&<p>Last updated: {date(order.updatedAt)}</p>}</section>
 <section className="order-panel" aria-labelledby="your-order"><h2 id="your-order">Your order</h2><p>{order.fulfilment==='PICKUP'?'Pickup':order.fulfilment}</p>
 {order.items.map((item,i)=><article className="tracking-item" key={i}><h3>{item.quantity} × {item.name}</h3><p>{item.variation}</p><p>Unit price (including options): {money(item.unitPriceMinor)}</p>
 {item.options.length>0&&<ul>{item.options.map((option,j)=><li key={j}>{option.groupName}: {option.name} × {option.quantity}<br/>Adjustment per item: {money(option.priceDeltaMinor*option.quantity)} ({money(option.priceDeltaMinor)} each)</li>)}</ul>}
 <p>Line total: {money(item.lineTotalMinor)}</p></article>)}
 <p><strong>Order total: {money(order.totalMinor)}</strong></p>{order.customerInstructions&&<><h3>Your instructions</h3><p className="tracking-instructions">{order.customerInstructions}</p></>}</section></>}
 <p>This private link provides read-only order tracking for seven days. Keep it private.</p><a href="#/recover-order">Recover access using a verification code</a></main></>;
}
