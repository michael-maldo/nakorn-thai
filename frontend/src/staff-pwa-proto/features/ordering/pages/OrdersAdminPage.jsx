import OrdersPage from '../../../../shared/staff-orders/components/OrdersPage';
import PageAction from '../../../components/PageAction';
export {StaffOrderCard} from '../../../../shared/staff-orders/components/StaffOrderCard';
export default function OrdersAdminPage(props){return <OrdersPage {...props} showStatus={false} Refresh={PageAction} orderHref={id=>`#/staff/orders?order=${encodeURIComponent(id)}`} onBack={()=>{location.hash='#/staff/orders';}}/>;}
