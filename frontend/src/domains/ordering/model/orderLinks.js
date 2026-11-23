const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function normalizeOrderId(value) {
 const candidate = typeof value === 'string' ? value.trim() : '';
 return uuid.test(candidate) ? candidate.toLowerCase() : null;
}
// Order IDs locate a request; they never grant customer or staff access.
export function orderLink(hash){
 const params=new URLSearchParams(hash.split('?')[1]||'');
 if(!params.has('order'))return {orderId:null,invalid:false};
 const ids=params.getAll('order');
 const orderId=ids.length===1&&uuid.test(ids[0])?normalizeOrderId(ids[0]):null;
 return {orderId,invalid:!orderId};
}

// Tokens stay in the hash and are sent only in a dedicated read-only header.
export function trackingToken(hash){
 const params=new URLSearchParams(hash.split('?')[1]||'');
 const values=params.getAll('t');
 return values.length===1&&/^[0-9a-f]{64}$/.test(values[0])?values[0]:null;
}
