package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.restaurant.availability.RestaurantAvailabilityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL",matches=".+")
class RestaurantSchedulingIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username",()->System.getenv().getOrDefault("DB_TEST_USERNAME","nakorn_test"));
        p.add("spring.datasource.password",()->System.getenv().getOrDefault("DB_TEST_PASSWORD",""));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired OpeningHoursHandler handler;
    @Autowired RestaurantAvailabilityService availability;
    @Autowired MockMvc mvc;
    @BeforeEach void emptySchedule() { jdbc.update("DELETE FROM restaurant_closed_date"); jdbc.update("DELETE FROM restaurant_opening_hours"); }
    OpeningHoursRequest.Window window(String opens, String closes) {
        return new OpeningHoursRequest.Window((short)1,LocalTime.parse(opens),LocalTime.parse(closes),true,0,null);
    }
    @Test void migrationHistorySettingsAuditAndIndexesArePresent() {
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('20','21') AND success",Integer.class));
        assertEquals("Australia/Melbourne",handler.read().settings().getTimezone());
        assertNotNull(handler.read().settings().getCreatedAt());
        assertFalse(availability.isOpen(Instant.parse("2026-09-07T08:00:00Z")));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND indexname='restaurant_opening_hours_active_day'",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM information_schema.triggers WHERE event_object_table IN ('restaurant_settings','restaurant_opening_hours','restaurant_closed_date') AND trigger_schema='public'",Integer.class));
    }
    @Test void timezoneAndMultipleWindowsPersistWithVersionsAndStaleWritesFail() {
        var settings=handler.read().settings();
        handler.saveSettings(new OpeningHoursRequest.Settings("Pacific/Auckland",settings.getVersion()));
        var first=handler.saveWindow(null,window("11:30","14:30"));
        var second=handler.saveWindow(null,window("17:00","01:00"));
        assertNotEquals(first.getId(),second.getId());
        assertNotNull(first.getCreatedAt()); assertNotNull(first.getUpdatedAt()); assertEquals(0L,first.getVersion());
        var id=first.getId();
        handler.saveWindow(id,new OpeningHoursRequest.Window((short)1,LocalTime.of(11,0),LocalTime.of(14,0),true,0,0L));
        em.clear();
        var snapshot=handler.read(); assertEquals("Pacific/Auckland",snapshot.settings().getTimezone());
        assertEquals(2,snapshot.hours().size());
        assertEquals(1L,snapshot.hours().stream().filter(h->h.getId().equals(id)).findFirst().orElseThrow().getVersion());
        assertTrue(availability.isOpen(Instant.parse("2026-09-06T23:30:00Z")));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->handler.deleteWindow(id,0L));
    }
    @Test void closedDatePersistsAndBlocksTuesdayOvernightPortion() {
        handler.saveWindow(null,window("17:00","01:00"));
        var date=handler.saveClosure(null,new OpeningHoursRequest.Closure(LocalDate.of(2026,9,8),"Test closure",null));
        em.clear();
        assertEquals("Test closure",handler.read().closedDates().getFirst().getReason());
        assertNotNull(date.getCreatedAt());assertEquals(0L,date.getVersion());
        assertTrue(availability.isOpen(Instant.parse("2026-09-07T13:00:00Z")));
        assertFalse(availability.isOpen(Instant.parse("2026-09-07T14:30:00Z")));
        handler.deleteClosure(date.getId(),0L);
        assertTrue(availability.isOpen(Instant.parse("2026-09-07T14:30:00Z")));
    }
    @Test void databaseRejectsInvalidWeekday() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,8,'17:00','22:00')",UUID.randomUUID()));
    }
    @Test void databaseRejectsEqualEndpoints() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO restaurant_opening_hours(id,day_of_week,opens_at,closes_at) VALUES (?,1,'17:00','17:00')",UUID.randomUUID()));
    }
    @Test void databaseRejectsDuplicateClosedDate() {
        jdbc.update("INSERT INTO restaurant_closed_date(id,closed_date) VALUES (?,'2026-09-08')",UUID.randomUUID());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO restaurant_closed_date(id,closed_date) VALUES (?,'2026-09-08')",UUID.randomUUID()));
    }
    @Test void databaseAllowsOnlyOneSettingsRow() {
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("INSERT INTO restaurant_settings(id,timezone) VALUES (2,'Australia/Melbourne')"));
    }
    @Test void adminApiPersistsAndRejectsStaleWrites() throws Exception {
        var settings=handler.read().settings();
        String body="{\"timezone\":\"Pacific/Auckland\",\"version\":"+settings.getVersion()+"}";
        mvc.perform(put("/api/staff/restaurant/settings").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("Pacific/Auckland"));
        em.clear();
        mvc.perform(put("/api/staff/restaurant/settings").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/staff/restaurant/schedule").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.settings.timezone").value("Pacific/Auckland"));
    }
}
