import {singleMutation,mutationKey} from '../../../services/mutations.js';
let state = { accessToken: '', user: null, expiresAt: null };
let refreshing = null;
const listeners = new Set();
export const identityState = () => state;
export const subscribeIdentity = (listener) => { listeners.add(listener); return () => listeners.delete(listener); };
function publish(next) { state = next; listeners.forEach((listener) => listener(state)); }
async function decode(response) {
  if (!response.ok) {
    let detail; try { detail = await response.json(); } catch { /* No credentials in errors. */ }
    const error = new Error(detail?.message || (response.status === 401 ? 'Please sign in again.' : response.status === 403 ? 'You do not have permission for this action.' : 'The identity service is unavailable.'));
    error.status = response.status; throw error;
  }
  if (response.status === 204) return null;
  return response.json();
}
async function csrf() { return decode(await fetch('/api/identity/csrf', { credentials: 'same-origin' })); }
async function post(path, body) {
  const token = await csrf();
  return decode(await fetch(`/api/identity/${path}`, { method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', [token.headerName]: token.token }, body: JSON.stringify(body || {}) }));
}
export async function login(username, password) {
  if (refreshing) await refreshing.catch(() => {});
  const data = await post('login', { username, password }); publish(data); return data;
}
export function refreshAccess() {
  if (refreshing) return refreshing;
  const refresh = () => post('refresh');
  const action = typeof navigator !== 'undefined' && navigator.locks ? navigator.locks.request('nakorn-staff-refresh', refresh) : refresh();
  refreshing = action.then((data) => { publish(data); return data; }).catch((error) => {
    publish({ accessToken: '', user: null, expiresAt: null }); throw error;
  }).finally(() => { refreshing = null; });
  return refreshing;
}
export async function logout() {
  if (refreshing) await refreshing.catch(() => {});
  await post('logout'); publish({ accessToken: '', user: null, expiresAt: null });
}
const staffUuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Read ownership only. Signature, live-session and permission checks remain server-side.
function credentialOwner(token) {
  try {
    const parts = token.split('.');
    if (parts.length !== 3 || !parts[2]) return null;
    const decode = part => JSON.parse(atob(part.replaceAll('-', '+').replaceAll('_', '/')));
    const header = decode(parts[0]), claims = decode(parts[1]);
    if (header.alg !== 'HS256' || claims.iss !== 'nakorn-thai' || !staffUuid.test(claims.sub) || !staffUuid.test(claims.sid) || !Number.isFinite(claims.exp)) return null;
    return { id: claims.sub, sessionId: claims.sid };
  } catch { return null; }
}
export function staffIdentityContext() {
  const owner = credentialOwner(state.accessToken);
  if (!owner || state.user?.id !== owner.id || state.user.enabled !== true || !['ADMIN', 'FOH', 'BOH'].includes(state.user.role)) return null;
  return { ...owner, role: state.user.role };
}
export function createStaffMutationGuard(authorization, additionalGuard) {
  const initiating = staffIdentityContext();
  const supplied = authorization?.startsWith('Bearer ') ? credentialOwner(authorization.slice(7)) : null;
  const cancelled = () => new Error('This order action was cancelled because the staff identity, session or page changed.');
  if (!initiating || !supplied || supplied.id !== initiating.id || supplied.sessionId !== initiating.sessionId) throw cancelled();
  // Capture before joining the serialized queue. An optional caller guard cannot replace this binding.
  return () => {
    if (additionalGuard && !additionalGuard()) throw cancelled();
    const current = staffIdentityContext();
    if (!current || current.id !== initiating.id || current.sessionId !== initiating.sessionId || current.role !== initiating.role) throw cancelled();
  };
}
export async function fetchWithIdentity(url, options = {}) {
  // Internal dispatch hook is not part of RequestInit and must not reach fetch.
  const { beforeDispatch, ...requestOptions } = options;
  if (!requestOptions.headers?.Authorization?.startsWith('Bearer ')) {
    beforeDispatch?.();
    return fetch(url, requestOptions);
  }
  if (!state.accessToken || Date.parse(state.expiresAt) <= Date.now() + 30000) await refreshAccess();
  const execute = () => {
    beforeDispatch?.();
    // No await between validation, credential selection and network dispatch.
    return fetch(url, { ...requestOptions, headers: { ...requestOptions.headers, Authorization: `Bearer ${state.accessToken}` } });
  };
  let response = await execute();
  if (response.status === 401) { await refreshAccess(); response = await execute(); }
  return response;
}
async function rawusersRequest(method = 'GET', id = '', body) {
  const token = method === 'GET' ? null : await csrf();
  return decode(await fetchWithIdentity(`/api/identity/users${id ? `/${id}` : ''}`, {
    method, credentials: 'same-origin', headers: { Authorization: `Bearer ${state.accessToken}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { [token.headerName]: token.token } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  }));
}

export function usersRequest(method = 'GET', id = '', body){return method !== 'GET'?singleMutation(mutationKey('/identity/users/'+id,{method,body}),()=>rawusersRequest(method,id,body)):rawusersRequest(method,id,body);}
