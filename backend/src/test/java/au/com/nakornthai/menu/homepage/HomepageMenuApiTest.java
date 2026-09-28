package au.com.nakornthai.menu.homepage;

import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomepageMenuController.class)
@Import(SecurityConfig.class)
class HomepageMenuApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean HomepageMenuHandler handler;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository jwtSessions;

    @Test void publicSelectionIsAnonymousAndNotCached() throws Exception {
        when(handler.publicCollections()).thenReturn(List.of());
        mvc.perform(get("/api/menu/homepage")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(content().json("[]"));
    }
    @Test void staffSettingsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/staff/menu/homepage")).andExpect(status().isUnauthorized());
        verifyNoInteractions(handler);
    }
    @ParameterizedTest @ValueSource(strings = {"FOH", "BOH"})
    void nonAdminsCannotReadOrWrite(String role) throws Exception {
        mvc.perform(get("/api/staff/menu/homepage").with(user("staff").roles(role))).andExpect(status().isForbidden());
        mvc.perform(put("/api/staff/menu/homepage").with(user("staff").roles(role)).with(csrf())
                .contentType("application/json").content("{\"version\":0,\"collectionIds\":[]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(handler);
    }
    @Test void adminWriteRequiresCsrfAndAllowsEmptySelection() throws Exception {
        String body = "{\"version\":0,\"collectionIds\":[]}";
        mvc.perform(put("/api/staff/menu/homepage").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        when(handler.save(any())).thenReturn(new HomepageMenuHandler.Settings(1L, List.of(), List.of()));
        mvc.perform(put("/api/staff/menu/homepage").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.collectionIds").isEmpty());
        verify(handler).save(new HomepageMenuRequest(0L, List.of()));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"version\":-1,\"collectionIds\":[]}", "{\"version\":0,\"collectionIds\":[null]}"})
    void invalidInputIsRejected(String body) throws Exception {
        mvc.perform(put("/api/staff/menu/homepage").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
    }
}
