export function emptyVerification(destination='') { return {destination,status:'idle',id:null,code:'',error:'',expiresAt:null,resendAt:null}; }
export function changeDestination(state,destination) { return state.destination===destination?state:emptyVerification(destination); }
export function isVerified(state,now=Date.now()) { return state.status==='verified' && state.id && Date.parse(state.expiresAt)>now; }
export function verificationIds(phone,email,now=Date.now()) {
 return {phoneVerificationId:isVerified(phone,now)?phone.id:null,emailVerificationId:isVerified(email,now)?email.id:null};
}
export function canSubmit(phone,email,now=Date.now()) { return Boolean(isVerified(phone,now)||isVerified(email,now)); }
