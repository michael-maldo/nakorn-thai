import { fetchWithIdentity } from '../../identity/api/identityApi.js';

async function decode(response) {
  if (!response.ok) {
    let body;
    try { body = await response.json(); } catch { /* Proxy errors may not be JSON. */ }
    throw new Error(body?.message || (response.status === 401 ? 'Please sign in again.' : response.status === 403 ? 'Access denied. Please sign in with a permitted staff account.' : 'The enquiry service is unavailable. Please try again.'));
  }
  return response.status === 204 ? null : response.json();
}

export async function functionRequest(path = '/api/functions', { method = 'GET', body, authorization, signal } = {}) {
  const csrf = method === 'GET' ? null : await decode(await fetch('/api/functions/csrf', { credentials: 'same-origin', signal }));
  return decode(await fetchWithIdentity(path, {
    method, credentials: 'same-origin', signal,
    headers: {
      ...(authorization ? { Authorization: authorization } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  }));
}
