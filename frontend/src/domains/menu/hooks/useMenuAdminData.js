import { useEffect, useState } from 'react';
import { getCollectionConfiguration, getStaffMenu } from '../api/menuApi';

export default function useMenuAdminData(authorization) {
  const [destination, navigate] = useState(null);
  const [data, setData] = useState(null), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [needsReload, setNeedsReload] = useState(false), [revision, setRevision] = useState(0);
  // Navigate after React has rendered the completed write and updated form guards.
  useEffect(() => {
    if (destination && !busy) { window.location.hash = destination; navigate(null); }
  }, [destination, busy]);
  const read = async () => {
    const [menu, collections] = await Promise.all([getStaffMenu(authorization), getCollectionConfiguration(authorization)]);
    return { menu, collections };
  };
  useEffect(() => {
    let active = true;
    read().then(value => { if (active) setData(value); }).catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [authorization]);
  async function reload() {
    setBusy(true); setError('');
    try { setData(await read()); setNeedsReload(false); setRevision(value => value + 1); }
    catch (e) { setError(e.message); }
    finally { setBusy(false); }
  }
  async function mutate(action, message, onCommitted = () => {}) {
    setBusy(true); setError(''); setNotice('');
    let committed = false, result;
    try {
      result = await action(); committed = true;
      onCommitted(result); setNeedsReload(true); setNotice(message);
      setData(await read()); setNeedsReload(false); setRevision(value => value + 1);
    } catch (e) {
      setError(committed ? `Saved successfully, but refreshing failed. Reload before editing again. ${e.message}` : e.message);
      if (e.status === 409) setNeedsReload(true);
    } finally { setBusy(false); }
    return { committed, result };
  }
  const clearFeedback = () => { setError(''); setNotice(''); };
  return { clearFeedback, ...data, loading: !data && !error, busy, error, notice, needsReload, revision, reload, mutate, navigate, authorization, disabled: busy || needsReload };
}
