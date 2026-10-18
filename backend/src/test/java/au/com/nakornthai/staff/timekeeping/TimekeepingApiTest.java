package au.com.nakornthai.staff.timekeeping;

import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TimekeepingController.class) @Import(SecurityConfig.class)
class TimekeepingApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean TimekeepingHandler handler;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    final String base = "/api/staff/timekeeping";
    final UUID id = UUID.randomUUID();
    final String staff = "{\"staffUserId\":\"" + id + "\"}";
    final String manual = "{\"staffUserId\":\"" + id + "\",\"clockIn\":\"2026-01-01T20:00:00Z\",\"clockOut\":\"2026-01-02T04:00:00Z\",\"reason\":\"Missed shift\"}";
    final String correction = "{\"version\":0,\"clockIn\":\"2026-01-01T20:00:00Z\",\"clockOut\":null,\"reason\":\"Wrong time\"}";

    @Test void allStaffCanReadOnlyTheirOwnTimesheetAndCannotOverrideIdentity() throws Exception {
        mvc.perform(get(base + "/me")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"ADMIN", "FOH", "BOH"}) {
            mvc.perform(get(base + "/me").with(user("alice").roles(role))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get(base + "/me").param("staffUserId", id.toString()).with(user("alice").roles(role)))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(base + "/csrf").with(user("alice").roles(role))).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        }
        verify(handler, times(3)).mine("alice", null, null, 0, 25);
        verifyNoMoreInteractions(handler);
    }
    @Test void onlyAdminsCanListOthersAndAuditOrMutateAndCsrfIsRequired() throws Exception {
        for (String role : new String[]{"FOH", "BOH", "ADMIN"}) {
            boolean admin = role.equals("ADMIN");
            mvc.perform(get(base + "/entries").param("staffUserId", id.toString()).with(user("staff").roles(role)))
                    .andExpect(admin ? status().isOk() : status().isForbidden());
            mvc.perform(get(base + "/entries/" + id + "/audit").with(user("staff").roles(role)))
                    .andExpect(admin ? status().isOk() : status().isForbidden());
            for (String operation : new String[]{"clock-in", "clock-out", "entries"}) {
                String body = operation.equals("entries") ? manual : staff;
                mvc.perform(post(base + "/" + operation).with(user("staff").roles(role)).with(csrf()).contentType("application/json").content(body))
                        .andExpect(admin ? (operation.equals("clock-out") ? status().isOk() : status().isCreated()) : status().isForbidden());
                mvc.perform(post(base + "/" + operation).with(user("staff").roles(role)).contentType("application/json").content(body))
                        .andExpect(status().isForbidden());
            }
            mvc.perform(patch(base + "/entries/" + id).with(user("staff").roles(role)).with(csrf()).contentType("application/json").content(correction))
                    .andExpect(admin ? status().isOk() : status().isForbidden());
        }
        mvc.perform(post(base + "/clock-in").with(csrf()).contentType("application/json").content(staff)).andExpect(status().isUnauthorized());
        verify(handler).all("staff", id, null, null, 0, 25);
        verify(handler).history(id, "staff", 0, 25);
        verify(handler).clockIn(id, "staff"); verify(handler).clockOut(id, "staff");
        verify(handler).manual(any(), eq("staff")); verify(handler).correct(eq(id), any(), eq("staff"));
    }
    @Test void validationRejectsMissingReasonsVersionsTimesAndMalformedParameters() throws Exception {
        for (String body : new String[]{"{}", manual.replace("Missed shift", " "), manual.replace("Missed shift", "x".repeat(1001)),
                manual.replace("2026-01-01T20:00:00Z", "2026-01-01T20:00:00"), manual.replace("\"clockOut\":\"2026-01-02T04:00:00Z\"", "\"clockOut\":null")})
            mvc.perform(post(base + "/entries").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        for (String body : new String[]{"{}", correction.replace("Wrong time", " "), correction.replace("\"version\":0", "\"version\":-1")})
            mvc.perform(patch(base + "/entries/" + id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "/me").param("from", "yesterday").with(user("staff").roles("FOH"))).andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
    }
    @Test void databaseFailuresReturnSanitizedErrors() throws Exception {
        when(handler.clockIn(id, "admin")).thenThrow(new org.springframework.dao.DataIntegrityViolationException("internal table and SQL details"));
        mvc.perform(post(base + "/clock-in").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(staff))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Attendance conflicts with existing data or changed concurrently; reload before saving"));
        when(handler.clockOut(id, "admin")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("internal database address"));
        mvc.perform(post(base + "/clock-out").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(staff))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value("Timekeeping is temporarily unavailable; reload to check the result before retrying"));
    }
}
