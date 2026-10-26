import {createContext,useContext} from 'react';
import {createPortal} from 'react-dom';

export const PageActionTarget=createContext(null);
// Move presentation only; the owning page retains its callback, state and guards.
export default function PageAction({children}){
 const target=useContext(PageActionTarget);
 return target?createPortal(children,target):null;
}
