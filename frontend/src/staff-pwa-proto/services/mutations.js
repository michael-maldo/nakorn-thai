// Coalesce identical in-flight operations before CSRF acquisition or queueing.
// Completed writes are never cached or automatically replayed.
const pending=new Map();
export function singleMutation(key,operation){
 if(pending.has(key))return pending.get(key);
 const promise=Promise.resolve().then(operation).finally(()=>{if(pending.get(key)===promise)pending.delete(key);});
 pending.set(key,promise);return promise;
}
export function mutationKey(path,options={}){
 const body=options.body;
 // A multipart upload's File identity is intentionally excluded; a second tap on
 // the same resource while uploading must not start another upload.
 return `${options.method || 'GET'}:${path}:${typeof body==='string'?body:typeof FormData!=='undefined'&&body instanceof FormData?'multipart':JSON.stringify(body)}`;
}
