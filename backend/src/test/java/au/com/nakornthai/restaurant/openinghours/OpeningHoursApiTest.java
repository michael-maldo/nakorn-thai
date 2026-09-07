package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.restaurant.availability.*;
import au.com.nakornthai.restaurant.domain.*;
import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({OpeningHoursController.class, RestaurantAvailabilityController.class}) @Import(SecurityConfig.class)
class OpeningHoursApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean OpeningHoursHandler handler;
    @MockitoBean RestaurantAvailabilityService availability;
    @MockitoBean Clock clock;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    final String window = """
            {"dayOfWeek":1,"opensAt":"17:00:00","closesAt":"01:00:00","active":true,"displayOrder":0}
            """;
    @Test void staffReadsAreAdminOnlyAndWritesRequireCsrf() throws Exception {
        for (String path : List.of("/schedule", "/csrf")) {
            mvc.perform(get("/api/staff/restaurant" + path)).andExpect(status().isUnauthorized());
            for (String role : List.of("FOH", "BOH"))
                mvc.perform(get("/api/staff/restaurant" + path).with(user("staff").roles(role))).andExpect(status().isForbidden());
        }
        for (String path : List.of("/hours", "/closed-dates")) {
            mvc.perform(post("/api/staff/restaurant" + path).with(user("admin").roles("ADMIN"))
                    .contentType("application/json").content(window)).andExpect(status().isForbidden());
            for (String role : List.of("FOH", "BOH"))
                mvc.perform(post("/api/staff/restaurant" + path).with(user("staff").roles(role)).with(csrf())
                        .contentType("application/json").content(window)).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/staff/restaurant/settings").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/staff/restaurant/hours/"+UUID.randomUUID()+"?version=0").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(handler);
    }
    @Test void malformedWindowIsRejectedAndValidOvernightWindowReachesHandler() throws Exception {
        mvc.perform(post("/api/staff/restaurant/hours").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(window.replace("\"dayOfWeek\":1", "\"dayOfWeek\":8")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
        mvc.perform(post("/api/staff/restaurant/hours").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(window)).andExpect(status().isCreated());
        verify(handler).saveWindow(isNull(), any());
    }
    @Test void publicAvailabilityIsUncachedAndDoesNotExposeClosureReasons() throws Exception {
        var instant = Instant.parse("2026-09-07T08:00:00Z"); when(clock.instant()).thenReturn(instant);
        when(availability.schedule()).thenReturn(new RestaurantSchedule(ZoneId.of("Australia/Melbourne"), List.of(), Set.of()));
        mvc.perform(get("/api/restaurant/availability")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.timezone").value("Australia/Melbourne"))
                .andExpect(jsonPath("$.open").value(false)).andExpect(jsonPath("$.closedDates").doesNotExist());
        verify(clock).instant();
    }
}
