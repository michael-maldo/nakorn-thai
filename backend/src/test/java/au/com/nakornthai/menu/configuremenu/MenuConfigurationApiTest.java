package au.com.nakornthai.menu.configuremenu;

import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MenuConfigurationController.class) @Import(SecurityConfig.class)
class MenuConfigurationApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean MenuConfigurationHandler handler;
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository sessions;
    @Test void configurationIsAdminOnlyAndWritesRequireCsrf() throws Exception {
        for(String path:List.of("/api/staff/menu/collections","/api/staff/menu/option-groups")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user("front").roles("FOH"))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(handler);
    }
    @Test void collectionReadAndValidatedWriteUseOwningHandler() throws Exception {
        when(handler.collections()).thenReturn(List.of());
        mvc.perform(get("/api/staff/menu/collections").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        when(handler.saveCollection(isNull(),any())).thenReturn(new MenuConfigurationHandler.Resource(UUID.randomUUID(),0L,null));
        mvc.perform(post("/api/staff/menu/collections").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("""
                {"name":"Lunch","slug":"lunch","status":"PUBLISHED","active":true,"timezone":"Australia/Melbourne","displayOrder":1}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(0));
        verify(handler).saveCollection(isNull(),any());
    }
    @Test void malformedConfigurationNeverReachesHandler() throws Exception {
        mvc.perform(post("/api/staff/menu/option-groups").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("""
                {"name":"Protein","code":"protein","selectionType":"ANY","active":true}
                """)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/staff/menu/items/"+UUID.randomUUID()+"/option-groups/"+UUID.randomUUID())
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"minSelections\":-1,\"maxSelections\":0}" )).andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
    }
}
