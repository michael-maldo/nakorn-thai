async function decode(response) {
 if(!response.ok){let body;try{body=await response.json();}catch{}throw new Error(body?.message||'Verification service unavailable. Please try again.');}
 return response.json();
}
async function write(path,body) {
 const csrf=await decode(await fetch('/api/orders/csrf',{credentials:'same-origin'}));
 return decode(await fetch('/api/orders/contact-verifications'+path,{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(body)}));
}
export const startContactVerification=(channel,destination)=>write('',{channel,destination});
export const verifyContactCode=(id,code)=>write('/'+id+'/verify',{code});
