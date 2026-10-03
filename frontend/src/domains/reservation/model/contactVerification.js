import { isVerified } from '../../notification/model/contactVerification.js';
export { emptyVerification,changeDestination,isVerified } from '../../notification/model/contactVerification.js';
export function verificationIds(phone,email,now=Date.now()) {
 return {phoneVerificationId:isVerified(phone,now)?phone.id:null,emailVerificationId:isVerified(email,now)?email.id:null};
}
export function canSubmit(phone,email,now=Date.now()) { return Boolean(isVerified(phone,now)||isVerified(email,now)); }
