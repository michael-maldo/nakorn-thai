import { fetchWithIdentity } from '../../identity/api/identityApi.js';
async function decode(response) {
  if (!response.ok) { let body; try { body = await response.json(); } catch {} const error=new Error(body?.message || 'Payment or verification request failed. Please try again.');error.status=response.status;throw error; }
  return response.status === 204 ? null : response.json();
}
export async function paymentRequest(path, { body, receipt, authorization, signal } = {}) {
  const csrf = body === undefined ? null : await decode(await fetch('/api/orders/csrf', { credentials: 'same-origin', signal }));
  signal?.throwIfAborted();
  return decode(await fetchWithIdentity(path, { method: body === undefined ? 'GET' : 'POST', cache: 'no-store', signal, credentials: 'same-origin',
    headers: { ...(receipt ? { 'X-Order-Token': receipt.trackingToken } : {}), ...(authorization ? { Authorization: authorization } : {}), ...(csrf ? { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }));
}

// A single setup request, always for the saved restaurant order. Never auto-retry it.
export async function beginPayPalCheckout(receipt, signal) {
  const timeout=AbortSignal.timeout(20000),requestSignal=signal?AbortSignal.any([signal,timeout]):timeout;
  const payment=await paymentRequest(`/api/payments/${receipt.requestId}`,{receipt,body:{method:'PAYPAL'},signal:requestSignal});
  return payment?.paid ? payment : paymentRequest(`/api/payments/${receipt.requestId}/check`,{receipt,body:{},signal:requestSignal});
}
