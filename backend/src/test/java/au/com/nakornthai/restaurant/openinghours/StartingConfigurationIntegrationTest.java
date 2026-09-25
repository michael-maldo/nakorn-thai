package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @Transactional
@EnabledIfEnvironmentVariable(named="DB_TEST_URL", matches=".+")
class StartingConfigurationIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    final UUID lunch = UUID.fromString("8ed50da5-f6d9-54b3-9611-2bc33b7e54d2");
    final UUID mondayHours = UUID.fromString("4ecf86b3-4ba2-4420-9665-5f0cbe6a2d8b");
    final UUID thursdayLunch = UUID.fromString("3e3dc9b3-257e-4d9e-9b55-57c23752cb9c");
    void runMigration(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(in); jdbc.execute(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        em.clear();
    }
    @Test void freshDatabaseHasApprovedScheduleAndOnlyRequestedStartingAccount() {
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM restaurant_opening_hours WHERE opens_at='09:00' AND closes_at='22:00' AND is_active", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM menu_collection_schedule WHERE collection_id=? AND start_time='11:00' AND is_active", Integer.class, lunch));
        assertEquals("14:30:00", jdbc.queryForObject("SELECT daily_cutoff_time::text FROM menu_collection WHERE id=?", String.class, lunch));
        assertEquals("14:30:00", jdbc.queryForObject("SELECT end_time::text FROM menu_collection_schedule WHERE collection_id=? AND day_of_week=2", String.class, lunch));
        assertEquals("14:30:00", jdbc.queryForObject("SELECT end_time::text FROM menu_collection_schedule WHERE collection_id=? AND day_of_week=4", String.class, lunch));
        assertEquals("ADMIN", jdbc.queryForObject("SELECT role FROM staff_user WHERE username='admin'", String.class));
        assertTrue(jdbc.queryForObject("SELECT enabled FROM staff_user WHERE username='admin'", Boolean.class));
        assertTrue(new BCryptPasswordEncoder().matches("admin", jdbc.queryForObject("SELECT password_hash FROM staff_user WHERE username='admin'", String.class)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM staff_user WHERE username IN ('foh','kitchen')", Integer.class));
    }
    @Test void localTimesReadAndWriteWithoutJvmTimezoneShifts() {
        var original = TimeZone.getDefault();
        try {
            for (String zone : List.of("UTC", "Australia/Melbourne")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone)); em.clear();
                var hours = em.find(OpeningHoursJpaEntity.class, mondayHours);
                var menu = em.find(MenuCollectionJpaEntity.class, lunch);
                var rule = em.find(MenuCollectionScheduleJpaEntity.class, thursdayLunch);
                assertEquals(LocalTime.of(9,0), hours.getOpensAt());
                assertEquals(LocalTime.of(22,0), hours.getClosesAt());
                assertEquals(LocalTime.of(14,30), menu.getDailyCutoffTime());
                assertEquals(LocalTime.of(11,0), rule.getStartTime());
                assertEquals(LocalTime.of(14,30), rule.getEndTime());
                hours.setOpensAt(LocalTime.of(10,0)); menu.setDailyCutoffTime(LocalTime.of(15,0)); rule.setEndTime(LocalTime.of(3,0));
                em.flush();
                assertEquals("10:00:00", jdbc.queryForObject("SELECT opens_at::text FROM restaurant_opening_hours WHERE id=?", String.class, mondayHours));
                assertEquals("15:00:00", jdbc.queryForObject("SELECT daily_cutoff_time::text FROM menu_collection WHERE id=?", String.class, lunch));
                assertEquals("03:00:00", jdbc.queryForObject("SELECT end_time::text FROM menu_collection_schedule WHERE id=?", String.class, thursdayLunch));
                hours.setOpensAt(LocalTime.of(9,0)); menu.setDailyCutoffTime(LocalTime.of(14,30)); rule.setEndTime(LocalTime.of(14,30)); em.flush();
            }
        } finally { TimeZone.setDefault(original); }
    }
    @Test void capturedLegacyTimesAreNormalizedButLaterEditsAndCredentialsSurvive() throws Exception {
        jdbc.update("UPDATE restaurant_opening_hours SET opens_at='23:00', closes_at='12:00' WHERE id=?", mondayHours);
        jdbc.update("UPDATE restaurant_opening_hours SET opens_at='10:15' WHERE day_of_week=2");
        jdbc.update("UPDATE menu_collection SET daily_cutoff_time='04:30', version=4 WHERE id=?", lunch);
        jdbc.update("UPDATE menu_collection_schedule SET start_time='01:00', end_time='16:30' WHERE id=?", thursdayLunch);
        jdbc.update("UPDATE menu_collection_schedule SET end_time='13:45' WHERE collection_id=? AND day_of_week=2", lunch);
        jdbc.update("UPDATE staff_user SET password_hash='existing-hash', enabled=false WHERE username='admin'");
        runMigration("V25__seed_starting_restaurant_configuration.sql");
        runMigration("V26__seed_starting_admin_account.sql");
        assertEquals(LocalTime.of(9,0), em.find(OpeningHoursJpaEntity.class, mondayHours).getOpensAt());
        assertEquals(LocalTime.of(14,30), em.find(MenuCollectionJpaEntity.class, lunch).getDailyCutoffTime());
        assertEquals(LocalTime.of(14,30), em.find(MenuCollectionScheduleJpaEntity.class, thursdayLunch).getEndTime());
        assertEquals("10:15:00", jdbc.queryForObject("SELECT opens_at::text FROM restaurant_opening_hours WHERE day_of_week=2", String.class));
        assertEquals("13:45:00", jdbc.queryForObject("SELECT end_time::text FROM menu_collection_schedule WHERE collection_id=? AND day_of_week=2", String.class, lunch));
        assertEquals("existing-hash", jdbc.queryForObject("SELECT password_hash FROM staff_user WHERE username='admin'", String.class));
        assertFalse(jdbc.queryForObject("SELECT enabled FROM staff_user WHERE username='admin'", Boolean.class));
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM restaurant_opening_hours", Integer.class));
        assertEquals(7, jdbc.queryForObject("SELECT count(*) FROM menu_collection_schedule WHERE collection_id=?", Integer.class, lunch));
    }
}
