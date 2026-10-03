export function paypalReturnMode(location) {
 const query=new URLSearchParams(location.search);
 const fragment=new URLSearchParams(location.hash.split('?')[1]||'');
 return query.get('paypal')||fragment.get('paypal')||'';
}
export function safeApprovalUrl(value) {
 try {const url=new URL(value);return url.protocol==='https:'&&!url.username&&!url.password&&['paypal.com','www.paypal.com','sandbox.paypal.com','www.sandbox.paypal.com'].includes(url.hostname)?url.href:null;}catch{return null;}
}
