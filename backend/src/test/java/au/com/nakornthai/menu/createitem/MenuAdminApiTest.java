package au.com.nakornthai.menu.createitem;

import au.com.nakornthai.menu.getitem.*;
import au.com.nakornthai.menu.updateitem.*;
import au.com.nakornthai.menu.deleteitem.*;
import au.com.nakornthai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
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

@WebMvcTest({CreateMenuItemController.class, GetMenuItemController.class, UpdateMenuItemController.class, DeleteMenuItemController.class})
@Import(SecurityConfig.class)
class MenuAdminApiTest {
    @org.springframework.test.context.DynamicPropertySource
    static void admin(org.springframework.test.context.DynamicPropertyRegistry properties) {
        properties.add("MENU_ADMIN_USERNAME", () -> "menu-admin");
        properties.add("MENU_ADMIN_PASSWORD_HASH", () -> new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("test-password-only"));
    }
    @MockitoBean au.com.nakornthai.identity.infrastructure.SpringDataStaffSessionRepository jwtSessions;
    @Autowired au.com.nakornthai.shared.security.JwtService jwt;
    @Autowired MockMvc mvc;
    @MockitoBean CreateMenuItemHandler create;
    @MockitoBean GetMenuItemHandler list;
    @MockitoBean UpdateMenuItemHandler update;
    @MockitoBean DeleteMenuItemHandler archive;
    private static final String PATH = "/api/staff/menu/items";

    @Test void bearerSessionCanReadAndWriteAndRevocationDeniesAccess() throws Exception {
        var user=new au.com.nakornthai.identity.infrastructure.UserJpaEntity();
        user.setId(java.util.UUID.randomUUID());user.setUsername("menu-admin");user.setRole("ADMIN");
        var session=new au.com.nakornthai.identity.infrastructure.StaffSessionJpaEntity();session.setUser(user);session.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(jwtSessions.findById(session.getId())).thenReturn(java.util.Optional.of(session));
        String bearer="Bearer "+jwt.issue(user,session.getId());
        when(list.handle()).thenReturn(new MenuItemResponse.Dashboard(List.of(),List.of(),List.of()));
        mvc.perform(get(PATH).header("Authorization",bearer)).andExpect(status().isOk());
        mvc.perform(get(PATH).with(httpBasic("menu-admin","test-password-only"))).andExpect(status().isUnauthorized());
        when(create.handle(any())).thenReturn(java.util.UUID.randomUUID());
        mvc.perform(post(PATH).header("Authorization",bearer).with(csrf()).contentType("application/json").content("""
            {"name":"Curry","slug":"curry","description":"Test curry","categoryId":"10000000-0000-0000-0000-000000000001",
            "status":"DRAFT","available":true,"displayOrder":0,"collectionIds":[]}
            """)).andExpect(status().isCreated());
        session.setRevokedAt(java.time.Instant.now());
        mvc.perform(get(PATH).header("Authorization",bearer)).andExpect(status().isUnauthorized());
    }
    @Test void anonymousCannotReadStaffMenu() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(list);
    }
    @Test void nonAdminCannotReadOrWrite() throws Exception {
        mvc.perform(get(PATH).with(user("staff"))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("staff")).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(list, create);
    }
    @Test void adminStillNeedsCsrfForWrites() throws Exception {
        mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/20000000-0000-0000-0000-000000000001?version=0").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(create, archive);
    }
    @Test void adminCanObtainCsrfAndListWithoutCaching() throws Exception {
        when(list.handle()).thenReturn(new MenuItemResponse.Dashboard(List.of(), List.of(), List.of()));
        mvc.perform(get("/api/staff/menu/csrf").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty());
        verify(list).handle();
    }
    @Test void invalidBodyNeverReachesHandler() throws Exception {
        mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(create);
    }
}
