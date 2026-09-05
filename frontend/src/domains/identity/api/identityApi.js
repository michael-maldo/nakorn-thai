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
export async function fetchWithIdentity(url, options = {}) {
  if (!options.headers?.Authorization?.startsWith('Bearer ')) return fetch(url, options);
  if (!state.accessToken || Date.parse(state.expiresAt) <= Date.now() + 30000) await refreshAccess();
  const execute = () => fetch(url, { ...options, headers: { ...options.headers, Authorization: `Bearer ${state.accessToken}` } });
  let response = await execute();
  if (response.status === 401) { await refreshAccess(); response = await execute(); }
  return response;
}
export async function usersRequest(method = 'GET', id = '', body) {
  const token = method === 'GET' ? null : await csrf();
  return decode(await fetchWithIdentity(`/api/identity/users${id ? `/${id}` : ''}`, {
    method, credentials: 'same-origin', headers: { Authorization: `Bearer ${state.accessToken}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { [token.headerName]: token.token } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  }));
}
