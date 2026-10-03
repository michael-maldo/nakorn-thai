import { isVerified } from '../../notification/model/contactVerification.js';
export { emptyVerification,changeDestination,isVerified } from '../../notification/model/contactVerification.js';
export function verificationIds(phone,now=Date.now()) {
 return {phoneVerificationId:isVerified(phone,now)?phone.id:null};
}
export function canSubmit(phone,now=Date.now()) { return Boolean(isVerified(phone,now)); }
