import { CartProvider } from '../domains/ordering/model/CartContext';
import AppRouter from './AppRouter';

export default function App() {
  return <CartProvider><AppRouter /></CartProvider>;
}
