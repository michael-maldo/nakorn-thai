import {createContext,useContext} from 'react';
const Runtime=createContext(null);
export const OrdersRuntimeProvider=Runtime.Provider;
export function useOrdersRuntime(){const value=useContext(Runtime);if(!value)throw Error('Orders runtime is required');return value;}
