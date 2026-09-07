import { fetchWithIdentity, identityState } from '../../identity/api/identityApi.js';

async function decode(response) {
  if (!response.ok) {
    let body; try { body = await response.json(); } catch { /* Use a safe fallback. */ }
    const error = new Error(body?.message || (response.status === 401 ? 'Please sign in again.' : response.status === 403 ? 'You do not have permission for this action.' : 'Restaurant scheduling is unavailable. Please try again.'));
    error.status = response.status;
    error.code = body?.code;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
export async function getRestaurantAvailability() {
  return decode(await fetch('/api/restaurant/availability', { credentials: 'same-origin', cache: 'no-store' }));
}
export async function restaurantRequest(path = '/schedule', { method = 'GET', body } = {}) {
  const headers = { Authorization: `Bearer ${identityState().accessToken}` };
  if (method !== 'GET') {
    const csrf = await decode(await fetchWithIdentity('/api/staff/restaurant/csrf', { credentials: 'same-origin', headers }));
    headers[csrf.headerName] = csrf.token;
  }
  return decode(await fetchWithIdentity(`/api/staff/restaurant${path}`, {
    method, credentials: 'same-origin', headers: { ...headers, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}),
  }));
}
