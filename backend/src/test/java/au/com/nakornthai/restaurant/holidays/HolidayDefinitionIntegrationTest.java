package au.com.nakornthai.restaurant.holidays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class HolidayDefinitionIntegrationTest {
    private static final String PATH = "/api/staff/restaurant/holidays";
    private static final String HOLIDAY = """
            {"name":" Christmas ","startDate":"2026-12-25","recurrence":"ANNUAL"}
            """;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("DB_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("DB_TEST_PASSWORD"));
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired HolidayDefinitionService holidayService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test void adminCrudPersistsAndPreservesSchedule() throws Exception {
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '41' AND success", Integer.class));
        var before = mvc.perform(get("/api/staff/restaurant/schedule").with(user("admin").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(HOLIDAY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Christmas"))
                .andExpect(jsonPath("$.endDate").value("2026-12-25")).andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists()).andExpect(jsonPath("$.updatedAt").exists());
        var id = jdbc.queryForObject("SELECT id::text FROM restaurant_holiday_definition WHERE name = 'Christmas'", String.class);
        try {
            mvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.holidays[?(@.id == '%s')].id", id).exists());
            mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType("application/json")
                            .content(HOLIDAY.replace("Christmas", "christmas").replace("2026", "2030")))
                    .andExpect(status().isConflict());
            mvc.perform(put(PATH + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType("application/json").content("""
                            {"name":"Christmas","startDate":"2026-12-25","endDate":"2027-01-03",
                             "recurrence":"ANNUAL","notes":" Break ","active":false,"version":0}
                            """))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.notes").value("Break"))
                    .andExpect(jsonPath("$.endDate").value("2027-01-03")).andExpect(jsonPath("$.version").value(1));
            mvc.perform(delete(PATH + "/" + id).param("version", "0").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isConflict());
            mvc.perform(delete(PATH + "/" + id).param("version", "1").with(user("admin").roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNoContent());
            mvc.perform(get(PATH).with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.holidays[?(@.id == '%s')].id", id).doesNotExist());
            assertEquals(before, mvc.perform(get("/api/staff/restaurant/schedule").with(user("admin").roles("ADMIN")))
                    .andReturn().getResponse().getContentAsString());
        } finally {
            jdbc.update("DELETE FROM restaurant_holiday_definition WHERE id::text = ?", id);
        }
    }

    @Test void permitsOverlappingOneTimePeriodsAndRejectsExactDuplicates() throws Exception {
        String first = """
                {"name":"Integration Easter break","startDate":"2027-03-26","endDate":"2027-03-29",
                 "recurrence":"ONE_TIME","active":false}
                """;
        String overlap = first.replace("Easter break", "March event").replace("2027-03-26", "2027-03-27");
        try {
            mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType("application/json").content(first))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(false));
            mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType("application/json").content(overlap))
                    .andExpect(status().isCreated());
            mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType("application/json").content(first.replace("false", "true")))
                    .andExpect(status().isConflict());
        } finally {
            jdbc.update("DELETE FROM restaurant_holiday_definition WHERE name IN (?, ?)",
                    "Integration Easter break", "Integration March event");
        }
    }

    @Test void auditRecordsActorAndOnlySuccessfulMutations() throws Exception {
        String actor = "holiday-audit-verification";
        String body = "{\"name\":\"Audit holiday\",\"startDate\":\"2028-02-29\",\"recurrence\":\"ANNUAL\"}";
        String id = null;
        try {
            mvc.perform(post(PATH).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isCreated());
            id = jdbc.queryForObject("SELECT id::text FROM restaurant_holiday_definition WHERE name = 'Audit holiday'", String.class);
            assertEquals(1, auditCount(actor, "HOLIDAY_DEFINITION_CREATED"));
            mvc.perform(post(PATH).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(body)).andExpect(status().isConflict());
            assertEquals(1, auditCount(actor, "HOLIDAY_DEFINITION_CREATED"));
            mvc.perform(put(PATH + "/" + id).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(body.replace("}", ",\"version\":0,\"active\":false}")))
                    .andExpect(status().isOk());
            assertEquals(1, auditCount(actor, "HOLIDAY_DEFINITION_UPDATED"));
            mvc.perform(delete(PATH + "/" + id).param("version", "0").with(user(actor).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isConflict());
            assertEquals(0, auditCount(actor, "HOLIDAY_DEFINITION_DELETED"));
            mvc.perform(delete(PATH + "/" + id).param("version", "1").with(user(actor).roles("ADMIN")).with(csrf()))
                    .andExpect(status().isNoContent());
            assertEquals(1, auditCount(actor, "HOLIDAY_DEFINITION_DELETED"));
        } finally {
            if (id != null) jdbc.update("DELETE FROM restaurant_holiday_definition WHERE id::text = ?", id);
            jdbc.update("DELETE FROM configuration_audit WHERE actor = ?", actor);
        }
    }

    @Test void rollbackRemovesHolidayAndItsAuditTogether() {
        var actor = "holiday-rollback-test";
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor, "test"));
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                holidayService.save(null, new HolidayDefinitionRequest("Rollback holiday",
                        java.time.LocalDate.of(2028, 1, 1), java.time.LocalDate.of(2028, 1, 1),
                        HolidayRecurrence.ONE_TIME, null, true, null, null));
                assertEquals(1, auditCount(actor, "HOLIDAY_DEFINITION_CREATED"));
                status.setRollbackOnly();
            });
            assertEquals(0, auditCount(actor, "HOLIDAY_DEFINITION_CREATED"));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM restaurant_holiday_definition WHERE name = 'Rollback holiday'", Integer.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private int auditCount(String actor, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM configuration_audit WHERE actor = ? AND category = 'SETTINGS' AND action = ?",
                Integer.class, actor, action);
    }

    @Test void surchargeIsExactAndLegacyUpdatesPreserveIt() throws Exception {
        String actor = "surcharge-test-admin";
        String base = "{\"name\":\"Surcharge integration\",\"startDate\":\"2028-06-01\",\"recurrence\":\"ONE_TIME\"";
        String id = null;
        try {
            mvc.perform(post(PATH).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(base + ",\"surchargePercentage\":12.50}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.surchargePercentage").value(12.5));
            id = jdbc.queryForObject("SELECT id::text FROM restaurant_holiday_definition WHERE name = 'Surcharge integration'", String.class);
            assertEquals(0, new java.math.BigDecimal("12.50").compareTo(jdbc.queryForObject(
                    "SELECT surcharge_percentage FROM restaurant_holiday_definition WHERE id::text = ?", java.math.BigDecimal.class, id)));
            mvc.perform(put(PATH + "/" + id).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(base + ",\"version\":0,\"notes\":\"legacy edit\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.surchargePercentage").value(12.5));
            mvc.perform(put(PATH + "/" + id).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(base + ",\"version\":1,\"surchargePercentage\":100.00}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.surchargePercentage").value(100));
            mvc.perform(put(PATH + "/" + id).with(user(actor).roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(base + ",\"version\":1,\"surchargePercentage\":0.00}"))
                    .andExpect(status().isConflict());
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM configuration_audit WHERE actor = ? AND changed_fields @> CAST(? AS jsonb)",
                    Integer.class, actor, "[\"surchargePercentage\"]") > 0);
            for (String invalid : new String[] {"-0.01", "100.01", "1.234"}) {
                mvc.perform(post(PATH).with(user(actor).roles("ADMIN")).with(csrf())
                        .contentType("application/json").content(base + ",\"surchargePercentage\":" + invalid + "}"))
                        .andExpect(status().isBadRequest());
            }
        } finally {
            if (id != null) jdbc.update("DELETE FROM restaurant_holiday_definition WHERE id::text = ?", id);
            jdbc.update("DELETE FROM configuration_audit WHERE actor = ?", actor);
        }
    }

    @Test void requiresAdminCsrfAndValidFields() throws Exception {
        mvc.perform(get(PATH).with(user("staff").roles("FOH"))).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("staff").roles("BOH")).with(csrf())
                .contentType("application/json").content(HOLIDAY)).andExpect(status().isForbidden());
        mvc.perform(post(PATH).with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(HOLIDAY)).andExpect(status().isForbidden());
        for (String invalid : new String[] {"{}", HOLIDAY.replace("Christmas", " "),
                HOLIDAY.replace("\"recurrence\"", "\"endDate\":\"2026-12-24\",\"recurrence\"")}) {
            mvc.perform(post(PATH).with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
    }
}
