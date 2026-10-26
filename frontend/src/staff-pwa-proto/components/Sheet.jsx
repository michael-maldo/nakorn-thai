import {useEffect,useRef} from 'react';
export default function Sheet({title,onClose,children}) {
 const ref=useRef(null);
 useEffect(()=>{const previous=document.activeElement,dialog=ref.current;dialog.showModal();return()=>{dialog.close();queueMicrotask(()=>{if(previous?.isConnected)previous.focus();});};},[]);
 return <dialog ref={ref} className="proto-sheet" aria-label={title} onCancel={event=>{event.preventDefault();onClose();}}><header><h2>{title}</h2><button type="button" aria-label="Close dialog" onClick={onClose}>✕</button></header>{children}</dialog>;
}
