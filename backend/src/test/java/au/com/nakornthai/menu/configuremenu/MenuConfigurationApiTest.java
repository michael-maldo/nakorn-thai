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
    @Test void dailyCutoffUsesNormalVersionedCollectionContract() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(put("/api/staff/menu/collections/"+id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("""
                {"name":"Lunch Special","slug":"lunch-special","status":"PUBLISHED","active":true,
                 "timezone":"UTC","displayOrder":2,"version":3,"dailyCutoffTime":"14:30:00"}
                """)).andExpect(status().isOk());
        var request=org.mockito.ArgumentCaptor.forClass(MenuConfigurationRequest.Collection.class);
        verify(handler).saveCollection(eq(id),request.capture());
        org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalTime.of(14,30),request.getValue().dailyCutoffTime());
        org.junit.jupiter.api.Assertions.assertEquals("UTC",request.getValue().timezone());
        org.junit.jupiter.api.Assertions.assertEquals(3L,request.getValue().version());
    }
    @Test void allCollectionWritesEnforceRolesAndCsrf() throws Exception {
        String root="/api/staff/menu/collections/"+UUID.randomUUID();
        for (String path:List.of(root,root+"/items/"+UUID.randomUUID(),root+"/categories/"+UUID.randomUUID(),root+"/schedules/"+UUID.randomUUID())) {
            for(String role:List.of("FOH","BOH")) {
                mvc.perform(put(path).with(user("staff").roles(role)).with(csrf()).contentType("application/json").content("{}"))
                        .andExpect(status().isForbidden());
                mvc.perform(delete(path+"?version=0").with(user("staff").roles(role)).with(csrf())).andExpect(status().isForbidden());
            }
            mvc.perform(put(path).with(csrf()).contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(path+"?version=0").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(handler);
    }
    @Test void invalidCollectionAndNegativeOverrideAreRejected() throws Exception {
        mvc.perform(post("/api/staff/menu/collections").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content("""
                {"name":" ","slug":"INVALID SLUG","status":"OTHER","timezone":"UTC","displayOrder":-1}
                """)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/staff/menu/collections/"+UUID.randomUUID()+"/items/"+UUID.randomUUID())
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"priceOverrideMinor\":-1,\"displayOrder\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(handler);
    }
    @Test void membershipAndScheduleWritesPassTheirOwnVersions() throws Exception {
        UUID collection=UUID.randomUUID(),item=UUID.randomUUID(),placement=UUID.randomUUID(),schedule=UUID.randomUUID();
        mvc.perform(put("/api/staff/menu/collections/"+collection+"/items/"+item)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("{\"collectionCategoryId\":\""+placement+"\",\"priceOverrideMinor\":0,\"displayOrder\":2,\"version\":3}"))
                .andExpect(status().isOk());
        verify(handler).saveMembership(collection,item,new MenuConfigurationRequest.Membership(placement,0L,2,3L));
        mvc.perform(put("/api/staff/menu/collections/"+collection+"/schedules/"+schedule)
                .with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
                .content("""
                {"ruleType":"WEEKLY","dayOfWeek":1,"startTime":"17:00:00","endTime":"01:00:00","active":false,"displayOrder":4,"version":2}
                """)).andExpect(status().isOk());
        verify(handler).saveSchedule(eq(collection),eq(schedule),argThat(r -> r.version()==2 && !r.active() && r.dayOfWeek()==1));
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
