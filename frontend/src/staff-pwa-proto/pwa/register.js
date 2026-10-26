export async function registerPrototypeWorker(onUpdate){
 if(!import.meta.env.PROD||!('serviceWorker'in navigator))return()=>{};
 const registration=await navigator.serviceWorker.register('/staff-pwa-proto/sw.js',{scope:'/staff-pwa-proto/',updateViaCache:'none'});
 let disposed=false;
 const notify=()=>{if(!disposed&&registration.waiting)onUpdate(()=>registration.waiting?.postMessage({type:'ACTIVATE_PROTOTYPE'}));};
 const found=()=>{const worker=registration.installing;worker?.addEventListener('statechange',()=>{if(worker.state==='installed'&&navigator.serviceWorker.controller)notify();});};
 const check=()=>registration.update().catch(()=>{});
 registration.addEventListener('updatefound',found);notify();const timer=setInterval(check,300000);
 return()=>{disposed=true;clearInterval(timer);registration.removeEventListener('updatefound',found);};
}
