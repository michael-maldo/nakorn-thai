package au.com.nakornthai.restaurant.orderingsettings;

import au.com.nakornthai.ordering.createorder.*;
import au.com.nakornthai.restaurant.openinghours.*;
import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({OrderingSettingsController.class, OpeningHoursController.class, CreateOrderController.class}) @Import(SecurityConfig.class)
class OrderingSettingsApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderingSettingsHandler handler;
    @MockitoBean OpeningHoursHandler hours;
    @MockitoBean CreateOrderHandler orders;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    final String path = "/api/staff/restaurant/ordering";
    final String update = "{\"acceptingOrders\":false,\"pauseMessage\":\"Kitchen busy\",\"version\":0}";
    @Test void adminAndFohCanReadAndWriteWithCsrfButBohAndGuestsCannot() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(csrf()).contentType("application/json").content(update)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(user("boh").roles("BOH"))).andExpect(status().isForbidden());
        mvc.perform(put(path).with(user("boh").roles("BOH")).with(csrf()).contentType("application/json").content(update)).andExpect(status().isForbidden());
        for (String role : new String[]{"ADMIN", "FOH"}) {
            mvc.perform(get(path).with(user("staff").roles(role))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get("/api/staff/restaurant/csrf").with(user("staff").roles(role))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
            mvc.perform(put(path).with(user("staff").roles(role)).contentType("application/json").content(update)).andExpect(status().isForbidden());
            mvc.perform(put(path).with(user("staff").roles(role)).with(csrf()).contentType("application/json").content(update)).andExpect(status().isOk());
        }
        verify(handler, times(2)).save(any());
        mvc.perform(put("/api/staff/restaurant/settings").with(user("foh").roles("FOH")).with(csrf())
                .contentType("application/json").content("{\"timezone\":\"UTC\",\"version\":0}")).andExpect(status().isForbidden());
        verifyNoInteractions(hours);
    }
    @Test void missingVersionMissingSwitchAndLongMessagesAreRejected() throws Exception {
        for (String body : new String[]{"{}", "{\"version\":0}", "{\"acceptingOrders\":true}",
                update.replace("Kitchen busy", "x".repeat(301)), update.replace("\"version\":0", "\"version\":-1")})
            mvc.perform(put(path).with(user("foh").roles("FOH")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
    }
    @Test void publicOptionsExplainPauseWithoutExposingStaffSettings() throws Exception {
        when(orders.orderingStatus()).thenReturn(new OrderingSettingsHandler.Status(false, "PAUSED_BY_STAFF", "Kitchen busy"));
        mvc.perform(get("/api/orders/options")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.reason").value("PAUSED_BY_STAFF"))
                .andExpect(jsonPath("$.message").value("Kitchen busy")).andExpect(jsonPath("$.version").doesNotExist());
    }
}
