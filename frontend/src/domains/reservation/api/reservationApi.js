import { fetchWithIdentity } from '../../identity/api/identityApi.js';
async function decode(response) {
 if (!response.ok) { let body; try { body=await response.json(); } catch {} throw new Error(body?.message || (response.status===401?'Please sign in again.':'Booking service unavailable. Please try again.')); }
 return response.status===204?null:response.json();
}
export async function reservationRequest(path='', {method='GET',body,authorization}={}) {
 const csrf=method==='GET'?null:await decode(await fetch('/api/reservations/csrf',{credentials:'same-origin'}));
 return decode(await fetchWithIdentity(path.startsWith('/staff')?`/api${path}`:`/api/reservations${path}`,{method,credentials:'same-origin',headers:{...(authorization?{Authorization:authorization}:{}),...(body?{'Content-Type':'application/json'}:{}),...(csrf?{[csrf.headerName]:csrf.token}:{})},...(body?{body:JSON.stringify(body)}:{})}));
}

export function startContactVerification(channel,destination) {
 return reservationRequest('/contact-verifications',{method:'POST',body:{channel,destination}});
}
export function verifyContactCode(id,code) {
 return reservationRequest(`/contact-verifications/${id}/verify`,{method:'POST',body:{code}});
}
