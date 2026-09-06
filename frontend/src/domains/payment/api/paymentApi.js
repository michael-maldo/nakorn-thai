import { fetchWithIdentity } from '../../identity/api/identityApi.js';
async function decode(response) {
  if (!response.ok) { let body; try { body = await response.json(); } catch {} throw new Error(body?.message || 'Payment or verification request failed. Please try again.'); }
  return response.status === 204 ? null : response.json();
}
export async function paymentRequest(path, { body, receipt, authorization } = {}) {
  const csrf = body === undefined ? null : await decode(await fetch('/api/orders/csrf', { credentials: 'same-origin' }));
  return decode(await fetchWithIdentity(path, { method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin',
    headers: { ...(receipt ? { 'X-Order-Token': receipt.trackingToken } : {}), ...(authorization ? { Authorization: authorization } : {}), ...(csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }));
}
