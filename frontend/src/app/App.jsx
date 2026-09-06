import CartDock from '../domains/ordering/components/CartDock';
import { AuthProvider } from '../domains/identity/model/AuthContext';
import { CartProvider } from '../domains/ordering/model/CartContext';
import AppRouter from './AppRouter';

export default function App() {
  return <AuthProvider><CartProvider><AppRouter /><CartDock /></CartProvider></AuthProvider>;
}
