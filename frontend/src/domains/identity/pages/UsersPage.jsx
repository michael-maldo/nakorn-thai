import { useEffect, useState } from 'react';
import { usersRequest } from '../api/identityApi';
import { useAuth } from '../model/AuthContext';
export default function UsersPage() {
  const { logout } = useAuth(); const [users, setUsers] = useState([]); const [draft, setDraft] = useState(null);
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [notice, setNotice] = useState('');
  async function reload() { setBusy(true);setError('');try { setUsers(await usersRequest());setDraft(null); } catch(e){setError(e.message);}finally{setBusy(false);} }
  useEffect(() => { let active = true; usersRequest().then((data) => { if(active)setUsers(data); }).catch((e) => { if(active)setError(e.message); });return () => {active=false;}; }, []);
  async function save(event) {
    event.preventDefault();setBusy(true);setError('');setNotice('');
    try {
      await usersRequest(draft.id ? 'PUT' : 'POST',draft.id || '',draft);
      setDraft(null);setNotice('Staff account saved. Updated accounts must sign in again.');setUsers(await usersRequest());
    } catch(e) {setError(e.message);}finally{setBusy(false);}
  }
  return <main className="staff-menu page-width"><header className="staff-heading"><div><a href="#/staff">Staff home</a><h1>Staff accounts</h1></div><button disabled={busy} onClick={logout}>Sign out</button></header>
    {error && <p role="alert" className="staff-error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <div className="staff-toolbar"><button disabled={busy} onClick={() => {setDraft({username:'',role:'FOH',password:''});setError('');}}>Add staff account</button><button disabled={busy} onClick={reload}>Reload accounts</button></div>
    {draft && <form className="staff-panel" onSubmit={save}><h2>{draft.id ? 'Edit account' : 'New account'}</h2><fieldset disabled={busy}>
      <label>Username<input required minLength={3} maxLength={50} pattern="[a-z0-9][a-z0-9._\-]{2,49}" disabled={!!draft.id} autoComplete="off" value={draft.username} onChange={(e)=>setDraft({...draft,username:e.target.value})} /></label>
      <label>Role<select value={draft.role} onChange={(e)=>setDraft({...draft,role:e.target.value})}><option value="FOH">Front of house</option><option value="BOH">Kitchen</option><option value="ADMIN">Administrator</option></select></label>
      {draft.id && <label className="staff-check"><input type="checkbox" checked={draft.enabled} onChange={(e)=>setDraft({...draft,enabled:e.target.checked})} />Account enabled</label>}
      <label>{draft.id ? 'New password (leave blank to keep current)' : 'Password'}<input type="password" required={!draft.id} minLength={12} maxLength={72} autoComplete="new-password" value={draft.password} onChange={(e)=>setDraft({...draft,password:e.target.value})} /></label>
      <p>Use at least 12 characters. Editing an account revokes its sessions. The last enabled administrator cannot be disabled or demoted.</p>
      <div className="staff-toolbar"><button>Save account</button><button type="button" onClick={()=>setDraft(null)}>Cancel</button></div>
    </fieldset></form>}
    <div className="staff-table-wrap"><table className="staff-table"><caption>Restaurant staff</caption><thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Action</th></tr></thead><tbody>{users.map((user)=><tr key={user.id}><td>{user.username}</td><td>{user.role}</td><td>{user.enabled?'Enabled':'Disabled'}</td><td><button disabled={busy} onClick={()=>setDraft({...user,password:''})}>Edit</button></td></tr>)}</tbody></table></div>
  </main>;
}
