import Verification from '../../notification/components/ContactVerification.jsx';
import { startContactVerification,verifyContactCode } from '../api/reservationApi.js';
export default function ContactVerification(props) {
 return <Verification {...props} startVerification={startContactVerification} verifyCode={verifyContactCode} />;
}
