export function emptyVerification(destination='') { return {destination,status:'idle',id:null,code:'',error:'',expiresAt:null,resendAt:null}; }
export function changeDestination(state,destination) { return state.destination===destination?state:emptyVerification(destination); }
export function isVerified(state,now=Date.now()) { return state.status==='verified' && state.id && Date.parse(state.expiresAt)>now; }
