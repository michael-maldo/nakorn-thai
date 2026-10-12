const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
// Order IDs locate a request; they never grant customer or staff access.
export function orderLink(hash){
 const params=new URLSearchParams(hash.split('?')[1]||'');
 if(!params.has('order'))return {orderId:null,invalid:false};
 const ids=params.getAll('order');
 return ids.length===1&&uuid.test(ids[0])?{orderId:ids[0].toLowerCase(),invalid:false}:{orderId:null,invalid:true};
}
