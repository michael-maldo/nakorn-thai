package au.com.nakornthai.restaurant.availability;

import au.com.nakornthai.ordering.createorder.*;
import au.com.nakornthai.reservation.createreservation.*;
import au.com.nakornthai.restaurant.domain.RestaurantClosedException;
import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CreateOrderController.class, CreateReservationController.class}) @Import(SecurityConfig.class)
class RestaurantClosedApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean CreateOrderHandler orders;
    @MockitoBean CreateReservationHandler reservations;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    @Test void directOrderApiReturnsMachineReadableClosureError() throws Exception {
        when(orders.handle(any())).thenThrow(new RestaurantClosedException());
        mvc.perform(post("/api/orders").with(csrf()).contentType("application/json").content("""
                {"requestId":"00000000-0000-0000-0000-000000000001","trackingToken":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "customerName":"Guest","phone":"0400000000","notes":"",
                 "items":[{"variationId":"00000000-0000-0000-0000-000000000002","quantity":1,"expectedUnitPriceMinor":2490,
                           "collectionId":"00000000-0000-0000-0000-000000000003","selectedOptions":[]}]}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESTAURANT_CLOSED"))
                .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void directReservationApiReturnsMachineReadableClosureError() throws Exception {
        when(reservations.handle(any())).thenThrow(new RestaurantClosedException());
        mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content("""
                {"requestId":"00000000-0000-0000-0000-000000000001","customerName":"Guest","phone":"0400000000",
                 "partySize":4,"requestedAt":"2026-09-07T18:00:00","notes":""}
                """)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESTAURANT_CLOSED"))
                .andExpect(header().string("Cache-Control","no-store"));
    }
}
