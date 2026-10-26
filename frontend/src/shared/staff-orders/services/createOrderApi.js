export function createOrderApi({fetchWithIdentity,createStaffMutationGuard}) {
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
const getOrderingOptions = (signal) => request('/orders/options', { signal });
const getOrder = (receipt) => request(`/orders/${receipt.requestId}`, { token: receipt.trackingToken });
async function submitOrder(payload) {
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
const getStaffOrder = (id, authorization, signal) => serial(() => request(`/staff/orders/${id}`, {authorization, signal}));
const getStaffOrders = (authorization, kitchen, history = false, signal) => serial(() =>
  request(kitchen ? '/staff/kitchen/orders' : `/staff/foh/orders?history=${history}`, { authorization, signal }));
async function changeOrderStatus(id, command, authorization, isCurrent) {
  const beforeDispatch = createStaffMutationGuard(authorization, isCurrent);
  return serial(async () => {
    beforeDispatch();
    const csrf = await request('/staff/orders/csrf', { authorization, beforeDispatch });
    return request(`/staff/orders/${id}/status`, { method: 'PATCH', body: JSON.stringify(command), authorization, csrf, beforeDispatch });
  });
}
async function verifyStaffPayment(id, command, authorization, isCurrent) {
  const beforeDispatch = createStaffMutationGuard(authorization, isCurrent);
  return serial(async () => {
    beforeDispatch();
    const csrf = await request('/staff/orders/csrf', { authorization, beforeDispatch });
    return request(`/staff/payments/${id}/${command ? 'payid-confirm' : 'check'}`, {
      method: 'POST', body: JSON.stringify(command || {}), authorization, csrf, beforeDispatch,
    });
  });
}

async function getReadOnlyOrder(token,signal){
 const response=await fetch('/api/order-tracking',{headers:{'X-Order-View-Token':token},credentials:'omit',cache:'no-store',referrerPolicy:'no-referrer',signal});
 if(!response.ok){const error=new Error(response.status===404?'This order tracking link has expired or is invalid. Please contact Nakorn Thai and provide your order reference if you need assistance.':'Order tracking is temporarily unavailable. Please try again.');error.status=response.status;throw error;}
 return response.json();
}

return {getOrderingOptions,getOrder,submitOrder,getStaffOrder,getStaffOrders,changeOrderStatus,verifyStaffPayment,getReadOnlyOrder};
}
