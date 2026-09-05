import { fetchWithIdentity } from '../../identity/api/identityApi.js';
async function request(path, { authorization, token, csrf, ...options } = {}) {
  const response = await fetchWithIdentity(`/api${path}`, {
    ...options, credentials: 'same-origin',
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(authorization ? { Authorization: authorization } : {}),
      ...(token ? { 'X-Order-Token': token } : {}),
      ...(csrf ? { [csrf.headerName]: csrf.token } : {}) },
  });
  if (!response.ok) {
    let detail;
    try { detail = await response.json(); } catch { /* A proxy may return HTML. */ }
    const error = new Error(response.status === 401 ? 'Sign-in failed. Check your staff credentials.' :
      response.status === 403 ? 'This account cannot perform that action, or its security token expired.' :
      detail?.message || 'The ordering service is unavailable. Please try again.');
    error.status = response.status; throw error;
  }
  if (response.status === 204) return null;
  try { return await response.json(); } catch { throw new Error('The ordering service returned an invalid response.'); }
}
export const getOrderingOptions = () => request('/orders/options');
export const getOrder = (receipt) => request(`/orders/${receipt.requestId}`, { token: receipt.trackingToken });
export async function submitOrder(payload) {
  const csrf = await request('/orders/csrf');
  return request('/orders', { method: 'POST', body: JSON.stringify(payload), csrf });
}
// Serialize staff polling and mutations so an authenticated read cannot rotate
// the CSRF token between its acquisition and the following status write.
let staffQueue = Promise.resolve();
function serial(action) {
  const result = staffQueue.then(action, action);
  staffQueue = result.catch(() => {}); return result;
}
export const getStaffOrders = (authorization, kitchen, history = false) => serial(() =>
  request(kitchen ? '/staff/kitchen/orders' : `/staff/foh/orders?history=${history}`, { authorization }));
export const changeOrderStatus = (id, command, authorization) => serial(async () => {
  const csrf = await request('/staff/orders/csrf', { authorization });
  return request(`/staff/orders/${id}/status`, { method: 'PATCH', body: JSON.stringify(command), authorization, csrf });
});
