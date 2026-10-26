import {fetchWithIdentity,createStaffMutationGuard} from '../../identity/api/identityApi.js';
import {createOrderApi} from '../../../../shared/staff-orders/services/createOrderApi.js';
export const {getOrderingOptions,getOrder,submitOrder,getStaffOrder,getStaffOrders,changeOrderStatus,verifyStaffPayment,getReadOnlyOrder}=createOrderApi({fetchWithIdentity,createStaffMutationGuard});
