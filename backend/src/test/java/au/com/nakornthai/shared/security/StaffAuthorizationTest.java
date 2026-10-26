package au.com.nakornthai.shared.security;

import au.com.nakornthai.ordering.changestatus.*;
import au.com.nakornthai.ordering.infrastructure.*;
import au.com.nakornthai.reservation.listreservations.ListReservationsController;
import au.com.nakornthai.reservation.infrastructure.*;
import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security chain + real write handlers, mocked persistence; no live database/providers. */
@SpringJUnitWebConfig(StaffAuthorizationTest.Config.class)
class StaffAuthorizationTest {
 @Configuration @EnableWebMvc @EnableWebSecurity
 @Import({SecurityConfig.class,ChangeOrderStatusController.class,ChangeOrderStatusHandler.class,ListReservationsController.class,au.com.nakornthai.ordering.listorders.ListOrdersController.class,au.com.nakornthai.ordering.listorders.ListOrdersHandler.class,OrderMapper.class})
 static class Config {

 }
 @Autowired WebApplicationContext context;
 @org.springframework.test.context.bean.override.mockito.MockitoBean EntityManager em;
 @org.springframework.test.context.bean.override.mockito.MockitoBean SpringDataOrderRepository orderRows;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.payment.infrastructure.SpringDataOrderPaymentRepository paymentRows;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.notification.orderconfirmation.SendOrderConfirmationHandler orders;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.notification.reservationconfirmation.SendReservationConfirmationHandler confirmations;
 @org.springframework.test.context.bean.override.mockito.MockitoBean RestaurantAvailabilityService availability;
 @org.springframework.test.context.bean.override.mockito.MockitoBean SpringDataReservationRepository repository;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
 @org.springframework.test.context.bean.override.mockito.MockitoBean au.com.nakornthai.restaurant.configuration.RuntimeConfiguration configuration;
 MockMvc mvc; OrderJpaEntity order; ReservationJpaEntity booking;
 final UUID id=UUID.randomUUID();
 @BeforeEach void setup(){
  reset(em,availability);
  mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  order=new OrderJpaEntity();order.setId(id);order.setVersion(1L);order.setStatus("NEW");order.setPhoneVerified(true);
  booking=new ReservationJpaEntity();booking.setId(id);booking.setVersion(1L);booking.setStatus("REQUESTED");booking.setRequestedAt(LocalDateTime.of(2030,1,1,18,0));
  when(em.find(OrderJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
  when(em.find(ReservationJpaEntity.class,id,LockModeType.PESSIMISTIC_WRITE)).thenReturn(booking);
  when(availability.schedule()).thenReturn(new au.com.nakornthai.restaurant.domain.RestaurantSchedule(ZoneId.of("Australia/Melbourne"),List.of(),Set.of()));
 }
 ResultActions order(String role,String status) throws Exception {
  return mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("staff").roles(role)).with(csrf()).contentType("application/json").content("{\"version\":1,\"status\":\""+status+"\",\"reason\":\"Test cancellation\",\"pickupMinutes\":20,\"paymentCollected\":true}"));
 }
 ResultActions booking(String role,String status) throws Exception {
  return mvc.perform(patch("/api/staff/reservations/"+id).with(user("staff").roles(role)).with(csrf()).contentType("application/json").content("{\"version\":1,\"status\":\""+status+"\",\"staffNote\":\"Test\"}"));
 }
 @Test void onlyAdminCanCancelOrdersThroughGenericEndpoint() throws Exception {
  for(String role:List.of("FOH","BOH")){order(role,"CANCELLED").andExpect(status().isForbidden());verifyNoInteractions(em);}
  order("ADMIN","CANCELLED").andExpect(status().isNoContent());assertEquals("CANCELLED",order.getStatus());verify(em).persist(any(OrderEventJpaEntity.class));
 }
 @Test void reservationCancellationAndBohWritesAreDeniedBeforePersistence() throws Exception {
  booking("FOH","CANCELLED").andExpect(status().isForbidden());booking("BOH","CONFIRMED").andExpect(status().isForbidden());verifyNoInteractions(em);
  booking("ADMIN","CANCELLED").andExpect(status().isNoContent());assertEquals("CANCELLED",booking.getStatus());assertEquals("staff",booking.getUpdatedBy());
 }
 @Test void bothOperationalRolesCanExecuteEveryNonCancellationEdge() throws Exception {
  for(String role:List.of("FOH","BOH")) {
   order.setStatus("NEW");order(role,"ACCEPTED").andExpect(status().isNoContent());assertEquals("ACCEPTED",order.getStatus());
   order(role,"PREPARING").andExpect(status().isNoContent());assertEquals("PREPARING",order.getStatus());
   order(role,"READY").andExpect(status().isNoContent());assertEquals("READY",order.getStatus());
   order(role,"COMPLETED").andExpect(status().isNoContent());assertEquals("COMPLETED",order.getStatus());
  }
  booking("FOH","CONFIRMED").andExpect(status().isNoContent());booking("FOH","SEATED").andExpect(status().isNoContent());
 }
 @Test void invalidStatesAndStaleVersionsAreStillRejected() throws Exception {
  order("ADMIN","READY").andExpect(status().isConflict());booking("ADMIN","SEATED").andExpect(status().isConflict());
  order.setStatus("COMPLETED");order("ADMIN","CANCELLED").andExpect(status().isConflict());
  booking.setVersion(2L);booking("ADMIN","CANCELLED").andExpect(status().isConflict());
 }
 @Test void nonAdminsCannotSkipStatesOrBypassPaymentPrerequisites() throws Exception {
  for(String role:List.of("FOH","BOH")) {
   order.setStatus("NEW");order(role,"READY").andExpect(status().isConflict());
   order.setPaymentMethod("PAYPAL");order(role,"ACCEPTED").andExpect(status().isConflict());
   order.setStatus("READY");order(role,"COMPLETED").andExpect(status().isConflict());
  }
 }
 @Test void paymentHoldsRefundObligationsAndAuditArePreserved() throws Exception {
  order.setPaymentMethod("PAYPAL");order("ADMIN","ACCEPTED").andExpect(status().isConflict());
  order.setPaidAt(Instant.now());order.setPaymentReviewRequired(true);order("ADMIN","ACCEPTED").andExpect(status().isConflict());
  order("ADMIN","CANCELLED").andExpect(status().isNoContent());assertEquals("REQUIRED",order.getRefundStatus());assertEquals("Test cancellation",order.getCancellationReason());
  var event=org.mockito.ArgumentCaptor.forClass(OrderEventJpaEntity.class);verify(em).persist(event.capture());assertEquals("staff",event.getValue().getActor());assertEquals("CANCELLED",event.getValue().getStatus());
 }
 @Test void bohCanReachNewOrdersAndDetailsWithoutPrivateContact() throws Exception {
  order.setCustomerName("Private guest");order.setPhone("+61412345678");
  when(orderRows.findByStatusInOrderByCreatedAtAsc(anyList(),any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(order));
  when(paymentRows.findByOrderIdIn(anyList())).thenReturn(List.of());
  mvc.perform(get("/api/staff/kitchen/orders").with(user("cook").roles("BOH"))).andExpect(status().isOk())
   .andExpect(jsonPath("$[0].status").value("NEW")).andExpect(jsonPath("$[0].paymentMethod").value("PAY_AT_RESTAURANT"))
   .andExpect(jsonPath("$[0].customerName").doesNotExist());
  verify(orderRows).findByStatusInOrderByCreatedAtAsc(eq(List.of("NEW","ACCEPTED","PREPARING","READY")),any(org.springframework.data.domain.Pageable.class));
  when(orderRows.findById(id)).thenReturn(Optional.of(order));when(paymentRows.findById(id)).thenReturn(Optional.empty());
  mvc.perform(get("/api/staff/orders/"+id).with(user("cook").roles("BOH"))).andExpect(status().isOk())
   .andExpect(jsonPath("$.status").value("NEW")).andExpect(jsonPath("$.phone").doesNotExist());
 }
 @Test void managementEndpointsDenyNonAdmins() throws Exception {
  for(String role:List.of("FOH","BOH"))for(String path:List.of("/api/staff/functions","/api/staff/restaurant/ordering","/api/staff/restaurant/configuration","/api/staff/menu/items","/api/identity/users","/api/staff/timekeeping/report"))
   mvc.perform(get(path).with(user("staff").roles(role))).andExpect(status().isForbidden());
 }
 @Test void authenticationAndCsrfRemainRequired() throws Exception {
  mvc.perform(patch("/api/staff/orders/"+id+"/status").with(csrf()).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(patch("/api/staff/orders/"+id+"/status").with(user("staff").roles("ADMIN")).contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
}
