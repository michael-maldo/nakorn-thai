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
class CreateReservationIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
 p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));}
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
 String time=LocalDate.now(ZoneId.of("Australia/Melbourne")).plusDays(2)+"T18:00:00";
 String body(UUID id){return "{\"requestId\":\""+id+"\",\"customerName\":\"Booking Test\",\"phone\":\"0400000000\",\"partySize\":4,\"requestedAt\":\""+time+"\",\"notes\":\"Window please\"}";}
 @Test void persistsRetriesAndRestrictsAccess() throws Exception {
 var id=UUID.randomUUID();
 mvc.perform(post("/api/reservations").contentType("application/json").content(body(id))).andExpect(status().isForbidden());
 for(int i=0;i<2;i++)mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated()).andExpect(jsonPath("$.reference").value(id.toString())).andExpect(jsonPath("$.phone").doesNotExist());
 assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM reservation WHERE id=?",Integer.class,id));
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id).replace("Window please","Different"))).andExpect(status().isConflict());
 String url="/api/staff/reservations?date="+time.substring(0,10);
 mvc.perform(get(url)).andExpect(status().isUnauthorized());
 mvc.perform(get(url).with(user("kitchen").roles("BOH"))).andExpect(status().isForbidden());
 mvc.perform(get(url).with(user("front").roles("FOH"))).andExpect(status().isOk()).andExpect(jsonPath("$[0].customerName").value("Booking Test"));
 }
 @Test void validatesTimeAndPartySize() throws Exception {
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(UUID.randomUUID()).replace(time,"2020-01-01T18:00:00"))).andExpect(status().isBadRequest());
 mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(UUID.randomUUID()).replace("\"partySize\":4","\"partySize\":21"))).andExpect(status().isBadRequest());
 }
 @Test void staffWorkflowRejectsStaleAndInvalidTransitions() throws Exception {
 var id=UUID.randomUUID();mvc.perform(post("/api/reservations").with(csrf()).contentType("application/json").content(body(id))).andExpect(status().isCreated());
 var url="/api/staff/reservations/"+id;
 String confirmed="{\"version\":0,\"status\":\"CONFIRMED\",\"staffNote\":\"Called guest\"}";
 mvc.perform(patch(url).with(user("kitchen").roles("BOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isForbidden());
 mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isNoContent());
 // Flush the transaction before the next request to model separate HTTP transactions.
 em.flush();em.clear();
 mvc.perform(patch(url).with(user("front").roles("FOH")).with(csrf()).contentType("application/json").content(confirmed)).andExpect(status().isConflict());
 mvc.perform(patch(url).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(confirmed.replace("\"version\":0","\"version\":1").replace("CONFIRMED","SEATED"))).andExpect(status().isNoContent());
 em.flush();em.clear();
 assertEquals("SEATED",jdbc.queryForObject("SELECT status FROM reservation WHERE id=?",String.class,id));
 assertEquals("admin",jdbc.queryForObject("SELECT updated_by FROM reservation WHERE id=?",String.class,id));
 }
 @Autowired jakarta.persistence.EntityManager em;
}
