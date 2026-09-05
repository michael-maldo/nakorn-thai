package au.com.nakornthai.reservation.createreservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class FunctionEnquiryIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    String body(UUID id) {
        return """
            {"requestId":"%s","customerName":"Venue Test","email":"venue@example.com",
             "phone":"0400000000","eventType":"Birthday","guestCount":40,
             "preferredDate":null,"preferredTime":"Evening","message":"Private dining enquiry"}
            """.formatted(id);
    }
    void create(UUID id) throws Exception {
        mvc.perform(post("/api/functions").with(csrf()).contentType("application/json").content(body(id)))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.reference").value(id.toString())).andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test void publicRequestPersistsAndRetriesWithoutExposingDetails() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post("/api/functions").contentType("application/json").content(body(id))).andExpect(status().isForbidden());
        create(id);create(id);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM function_enquiry WHERE id=?",Integer.class,id));
        mvc.perform(post("/api/functions").with(csrf()).contentType("application/json").content(body(id).replace("Birthday","Wedding"))).andExpect(status().isConflict());
        mvc.perform(get("/api/functions/"+id)).andExpect(status().isUnauthorized());
    }
    @Test void validatesEmailGuestsAndDate() throws Exception {
        String request=body(UUID.randomUUID());
        for(String invalid:new String[]{request.replace("venue@example.com","bad-address"),request.replace("\"guestCount\":40","\"guestCount\":0"),request.replace("\"preferredDate\":null","\"preferredDate\":\"2020-01-01\"")})
            mvc.perform(post("/api/functions").with(csrf()).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
    }
    @Test void staffAccessAndQueueValidation() throws Exception {
        create(UUID.randomUUID());
        mvc.perform(get("/api/staff/functions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/functions").with(user("kitchen").roles("BOH"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/functions").with(user("front").roles("FOH"))).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items[0].email").value("venue@example.com"));
        mvc.perform(get("/api/staff/functions?status=BAD").with(user("admin").roles("ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/staff/functions?page=-1").with(user("front").roles("FOH"))).andExpect(status().isBadRequest());
    }
    @Test void confirmsWithDateAndRejectsStaleAndInvalidChanges() throws Exception {
        var id=UUID.randomUUID();create(id);
        String url="/api/staff/functions/"+id;
        String update="{\"version\":0,\"status\":\"CONFIRMED\",\"arrangedDate\":null,\"staffNote\":\"Spoke to guest\"}";
        mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(update)).andExpect(status().isBadRequest());
        String dated=update.replace("\"arrangedDate\":null","\"arrangedDate\":\""+LocalDate.now(ZoneId.of("Australia/Melbourne")).plusDays(20)+"\"");
        mvc.perform(patch(url).with(user("kitchen").roles("BOH")).with(csrf()).contentType("application/json").content(dated)).andExpect(status().isForbidden());
        mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(dated)).andExpect(status().isNoContent());
        em.flush();em.clear();
        mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(dated)).andExpect(status().isConflict());
        mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(dated.replace("\"version\":0","\"version\":1").replace("CONFIRMED","NEW"))).andExpect(status().isConflict());
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM function_enquiry WHERE id=?",String.class,id));
        assertEquals("front",jdbc.queryForObject("SELECT updated_by FROM function_enquiry WHERE id=?",String.class,id));
    }
}
