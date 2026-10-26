import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import {AuthProvider,useAuth} from './features/identity/model/AuthContext';
import LoginForm from './features/identity/components/LoginForm';
import StaffProtoApp from './StaffProtoApp';
import './styles/prototype.css';
function Access(){const {user,loading}=useAuth();if(loading)return <main className="proto-login"><p role="status">Checking your staff session…</p></main>;if(!user)return <main className="proto-login"><h1>NAKORN THAI</h1><p>Staff prototype</p><LoginForm/></main>;if(!user.enabled||!['ADMIN','FOH','BOH'].includes(user.role))return <main><h1>Access restricted</h1></main>;return <StaffProtoApp/>;}
createRoot(document.getElementById('root')).render(<StrictMode><AuthProvider><Access/></AuthProvider></StrictMode>);
