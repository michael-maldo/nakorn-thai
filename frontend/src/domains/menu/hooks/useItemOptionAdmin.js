import { useEffect, useState } from 'react';
import { getItemOptionGroups, getOptionGroups } from '../api/menuApi';
import { useMenuNavigationGuard } from './useMenuAdminForm';

export default function useItemOptionAdmin(itemId, authorization) {
  const [data, setData] = useState(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [needsReload, setNeedsReload] = useState(false), [revision, setRevision] = useState(0);
  useMenuNavigationGuard(false, busy);
  const read = async () => {
    const [groups, assignments] = await Promise.all([getOptionGroups(authorization), getItemOptionGroups(itemId, authorization)]);
    return { groups, assignments };
  };
  useEffect(() => {
    let active = true;
    read().then(value => { if (active) { setData(value); setError(''); } }).catch(failure => { if (active) setError(failure.message); });
    return () => { active = false; };
  }, [itemId, authorization]);
  async function reload() {
    setBusy(true); setError('');
    try { setData(await read()); setNeedsReload(false); setRevision(value => value + 1); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }
  async function mutate(action, onCommitted, message) {
    setBusy(true); setError(''); setNotice('');
    let committed = false;
    try {
      await action(); committed = true; onCommitted(); setNotice(message); setNeedsReload(true);
      setData(await read()); setNeedsReload(false); setRevision(value => value + 1);
    } catch (failure) {
      setError(committed ? `Saved, but refreshing failed. Reload before editing again. ${failure.message}` : failure.message);
      if (failure.status === 409) setNeedsReload(true);
    } finally { setBusy(false); }
  }
  return { ...data, busy, error, notice, needsReload, revision, reload, mutate, authorization, disabled: busy || needsReload };
}
