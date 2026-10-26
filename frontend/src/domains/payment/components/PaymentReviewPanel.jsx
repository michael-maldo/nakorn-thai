import Review from '../../../shared/staff-orders/components/PaymentReviewPanel';
import {paymentRequest} from '../api/paymentApi';
export default function PaymentReviewPanel(props){return <Review {...props} request={paymentRequest} orderHref={id=>`#/staff/orders?order=${encodeURIComponent(id)}`}/>;}
