package au.com.nakornthai.staff.timekeeping;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static au.com.nakornthai.staff.timekeeping.TimekeepingDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class TimekeepingIntegrationTest {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }
    @Autowired TimekeepingHandler handler;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired au.com.nakornthai.shared.security.JwtService jwt;
    @Autowired au.com.nakornthai.identity.infrastructure.SpringDataUserRepository users;
    UUID adminId, staff, other;
    String admin, employee;
    final Instant start = Instant.parse("2025-06-01T10:00:00Z");
    @BeforeEach void accounts() {
        adminId = account("ADMIN"); staff = account("FOH"); other = account("BOH");
        admin = username(adminId); employee = username(staff);
    }
    UUID account(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_user(id,username,password_hash,role,created_at,updated_at) values (?,?,?,?,now(),now())", id, username(id), "unused-test-hash", role);
        return id;
    }
    String username(UUID id) { return "t-" + id; }
    Entry manual(UUID user, Instant in, Instant out) { return handler.manual(new ManualRequest(user, in, out, "Historical attendance"), admin); }
    Entry shift() { return manual(staff, start, start.plusSeconds(3600)); }
    void conflict(Runnable action) { assertEquals(409, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value()); }
    void bad(Runnable action) { assertEquals(400, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value()); }

    @Test void liveClockInAndOutUseServerTimeAndRejectDuplicates() {
        Instant before = Instant.now().minusSeconds(1);
        Entry opened = handler.clockIn(staff, admin);
        assertTrue(opened.open()); assertNull(opened.workedSeconds()); assertEquals(adminId, opened.createdBy());
        assertFalse(opened.clockIn().isBefore(before)); assertFalse(opened.clockIn().isAfter(Instant.now()));
        conflict(() -> handler.clockIn(staff, admin));
        Entry closed = handler.clockOut(staff, admin);
        assertFalse(closed.open()); assertTrue(closed.clockOut().isAfter(closed.clockIn()));
        assertEquals(0, seconds(closed.clockIn(), closed.clockOut()).compareTo(closed.workedSeconds()));
        conflict(() -> handler.clockOut(staff, admin));
        var audit = handler.history(opened.id(), admin, 0, 25).entries();
        assertEquals(List.of("CLOCK_IN", "CLOCK_OUT"), audit.stream().map(Audit::action).toList());
        assertNull(audit.getFirst().previousValues()); assertNull(audit.getLast().previousValues().clockOut());
        assertEquals(closed.clockOut(), audit.getLast().newValues().clockOut());
        assertTrue(audit.stream().allMatch(a -> adminId.equals(a.actorId())));
    }
    @Test void clockOutWithoutOpenShiftFails() { conflict(() -> handler.clockOut(staff, admin)); }
    @Test void historicalOvernightAndMultipleAdjacentShiftsHaveActualDuration() {
        Entry first = manual(staff, Instant.parse("2025-06-01T22:00:00+10:00"), Instant.parse("2025-06-02T02:00:00+10:00"));
        Entry second = manual(staff, first.clockOut(), first.clockOut().plusSeconds(7200));
        assertEquals(0, new BigDecimal("14400").compareTo(first.workedSeconds()));
        var sheet = handler.all(admin, staff, null, null, 0, 1);
        assertEquals(2, sheet.totalElements()); assertEquals(1, sheet.entries().size());
        assertEquals(second.id(), sheet.entries().getFirst().id());
        assertEquals(0, new BigDecimal("21600").compareTo(sheet.completedSeconds()));
        Entry dst = manual(other, Instant.parse("2025-10-05T01:00:00+10:00"), Instant.parse("2025-10-05T04:00:00+11:00"));
        assertEquals(0, new BigDecimal("7200").compareTo(dst.workedSeconds()));
        Entry fractional = manual(other, start, start.plusSeconds(1).plusNanos(123456000));
        assertEquals(0, new BigDecimal("1.123456").compareTo(fractional.workedSeconds()));
    }
    @Test void allOverlapShapesAreRejectedAndOtherEmployeesAreIndependent() {
        shift();
        for (long[] range : new long[][]{{0,3600},{-1,3601},{-1,1},{3599,3601},{1,3599}})
            conflict(() -> manual(staff, start.plusSeconds(range[0]), start.plusSeconds(range[1])));
        manual(other, start, start.plusSeconds(3600));
        manual(staff, start.minusSeconds(3600), start);
        manual(staff, start.plusSeconds(3600), start.plusSeconds(7200));
        assertEquals(3, handler.all(admin, staff, null, null, 0, 25).totalElements());
    }
    @Test void invalidTimesReasonsAndPaginationFailWithoutWriting() {
        bad(() -> manual(staff, start, start)); bad(() -> manual(staff, start.plusSeconds(1), start));
        bad(() -> manual(staff, start.plusNanos(1), start.plusSeconds(1)));
        bad(() -> handler.manual(new ManualRequest(staff, start, start.plusSeconds(1), "\t "), admin));
        var entry = shift();
        bad(() -> handler.correct(entry.id(), new CorrectionRequest(0L, start, start.plusSeconds(2), ""), admin));
        bad(() -> handler.correct(entry.id(), new CorrectionRequest(0L, start, start, "Correction"), admin));
        bad(() -> handler.all(admin, staff, start, start, 0, 25));
        bad(() -> handler.mine(employee, null, null, -1, 25));
        bad(() -> handler.mine(employee, null, null, 0, 101));
        bad(() -> handler.mine(employee, null, null, Integer.MAX_VALUE, 100));
        assertEquals(1, handler.history(entry.id(), admin, 0, 25).totalElements());
    }
    @Test void correctionsPreserveAuditAndRejectStaleVersionsAndOverlaps() {
        Entry original = shift();
        Entry corrected = handler.correct(original.id(), new CorrectionRequest(original.version(), start.minusSeconds(300), start.plusSeconds(3900), "Fix missed minutes"), admin);
        assertEquals(original.createdAt(), corrected.createdAt()); assertEquals(original.createdBy(), corrected.createdBy());
        assertEquals(original.version() + 1, corrected.version());
        var audit = handler.history(original.id(), admin, 0, 25).entries();
        assertEquals(2, audit.size()); assertEquals("CORRECTION", audit.getLast().action());
        assertEquals("Fix missed minutes", audit.getLast().reason());
        assertEquals(original.clockIn(), audit.getLast().previousValues().clockIn());
        assertEquals(corrected.clockIn(), audit.getLast().newValues().clockIn());
        assertEquals(corrected.version().longValue(), audit.getLast().newValues().version());
        conflict(() -> handler.correct(original.id(), new CorrectionRequest(original.version(), start, start.plusSeconds(1), "Stale"), admin));
        manual(staff, start.plusSeconds(4000), start.plusSeconds(5000));
        conflict(() -> handler.correct(original.id(), new CorrectionRequest(corrected.version(), start, start.plusSeconds(4500), "Overlap"), admin));
        assertEquals(2, handler.history(original.id(), admin, 0, 25).totalElements());
    }
    @Test void noOpCorrectionsAdvanceVersionAndReopeningStillChecksOverlaps() {
        Entry first = shift();
        Entry same = handler.correct(first.id(), new CorrectionRequest(first.version(), first.clockIn(), first.clockOut(), "Verified unchanged times"), admin);
        assertEquals(first.version() + 1, same.version());
        Entry reopened = handler.correct(first.id(), new CorrectionRequest(same.version(), first.clockIn(), null, "Clock-out was accidental"), admin);
        assertTrue(reopened.open());
        conflict(() -> manual(staff, first.clockOut(), first.clockOut().plusSeconds(1)));
        assertEquals(3, handler.history(first.id(), admin, 0, 25).totalElements());
        assertEquals(1, handler.history(first.id(), admin, 1, 1).entries().size());
    }
    @Test void openShiftsExcludedFromTotalsAndRangeUsesIntersectionAndExclusiveEnd() {
        Entry closed = shift(); Entry open = handler.clockIn(staff, admin);
        var all = handler.mine(employee, null, null, 0, 25);
        assertEquals(1, all.openShiftCount()); assertEquals(0, new BigDecimal("3600").compareTo(all.completedSeconds()));
        var filtered = handler.mine(employee, start.plusSeconds(1800), start.plusSeconds(5400), 0, 25);
        assertEquals(List.of(closed.id()), filtered.entries().stream().map(Entry::id).toList());
        assertEquals(0, handler.mine(employee, null, start, 0, 25).totalElements());
        assertEquals(0, handler.mine(employee, closed.clockOut(), open.clockIn(), 0, 25).totalElements());
        assertEquals(1, handler.mine(employee, open.clockIn(), null, 0, 25).totalElements());
        assertEquals(0, handler.mine(username(other), null, null, 0, 25).totalElements());
    }
    @Test void concurrentClockInsAndManualEntriesCannotOverlap() throws Exception {
        List<Object> live = race(() -> handler.clockIn(staff, admin), () -> handler.clockIn(staff, admin));
        assertOneWinner(live);
        assertEquals(1, handler.all(admin, staff, null, null, 0, 25).totalElements());
        List<Object> historical = race(() -> manual(other, start, start.plusSeconds(3600)), () -> manual(other, start, start.plusSeconds(3600)));
        assertOneWinner(historical);
        assertEquals(1, handler.all(admin, other, null, null, 0, 25).totalElements());
    }
    @Test void concurrentCorrectionsHaveOneWinnerAndOneAudit() throws Exception {
        var original = shift();
        var request = new CorrectionRequest(original.version(), start, start.plusSeconds(4200), "Correct duration");
        assertOneWinner(race(() -> handler.correct(original.id(), request, admin), () -> handler.correct(original.id(), request, admin)));
        assertEquals(2, handler.history(original.id(), admin, 0, 25).totalElements());
    }
    @Test void concurrentClockOutHasOneWinner() throws Exception {
        Entry open = handler.clockIn(staff, admin);
        assertOneWinner(race(() -> handler.clockOut(staff, admin), () -> handler.clockOut(staff, admin)));
        assertEquals(2, handler.history(open.id(), admin, 0, 25).totalElements());
    }
    List<Object> race(Supplier<Entry> first, Supplier<Entry> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            var futures = new ArrayList<Future<Object>>();
            for (var action : List.of(first, second)) futures.add(executor.submit(() -> {
                ready.countDown(); if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
                try { return action.get(); } catch (ResponseStatusException e) { return e; }
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); go.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
    void assertOneWinner(List<Object> results) {
        assertEquals(1, results.stream().filter(Entry.class::isInstance).count());
        assertEquals(1, results.stream().filter(v -> v instanceof ResponseStatusException e && e.getStatusCode().value() == 409).count());
    }
    @Test void databaseConstraintsRejectInvalidRowsAndAuditIsAppendOnly() {
        var entry = shift();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> rawAttendance(staff, start, start.plusSeconds(1)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> rawAttendance(staff, start, start));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> rawAttendance(UUID.randomUUID(), start, start.plusSeconds(1)));
        var open = handler.clockIn(other, admin);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> rawAttendance(other, open.clockIn().plusSeconds(1), null));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("update staff_attendance_audit set reason='tamper' where attendance_id=?", entry.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("delete from staff_attendance_audit where attendance_id=?", entry.id()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.execute("truncate staff_attendance_audit"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("insert into staff_attendance_audit(id,attendance_id,action,actor_id,occurred_at,new_values,reason) values (?,?,'CORRECTION',?,now(),'{}'::jsonb,' ')", UUID.randomUUID(), entry.id(), adminId));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("insert into staff_attendance_audit(id,attendance_id,action,actor_id,occurred_at,new_values,reason) values (?,?,'MANUAL_ENTRY',?,now(),'{}'::jsonb,?)", UUID.randomUUID(), entry.id(), adminId, "\t\n"));
        assertEquals(1, handler.history(entry.id(), admin, 0, 25).totalElements());
        assertEquals(1, jdbc.queryForObject("select count(*) from flyway_schema_history where success and version='38'", Integer.class));
    }
    void rawAttendance(UUID user, Instant in, Instant out) {
        jdbc.update("insert into staff_attendance(id,staff_user_id,clock_in,clock_out,created_by,created_at,updated_at) values (?,?,?,?,?,now(),now())",
                UUID.randomUUID(), user, java.sql.Timestamp.from(in), out == null ? null : java.sql.Timestamp.from(out), adminId);
    }
    @Test void mutationAndAuditRollbackTogether() {
        var tx = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> { handler.clockIn(staff, admin); throw new IllegalStateException("abort"); }));
        assertEquals(0, handler.all(admin, staff, null, null, 0, 25).totalElements());
        assertEquals(0, jdbc.queryForObject("select count(*) from staff_attendance_audit where actor_id=?", Long.class, adminId));
    }
    @Test void realIdentitySelfViewCannotReadAnotherStaffAndServiceAlsoChecksAdmin() throws Exception {
        shift(); manual(other, start, start.plusSeconds(3600));
        mvc.perform(get("/api/staff/timekeeping/me").with(user(employee).roles("FOH")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.entries[0].staffUserId").value(staff.toString()));
        mvc.perform(get("/api/staff/timekeeping/me").param("staffUserId", other.toString()).with(user(employee).roles("FOH"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/staff/timekeeping/entries").param("staffUserId", other.toString()).with(user(employee).roles("FOH"))).andExpect(status().isForbidden());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> handler.clockIn(staff, employee)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> handler.all(employee, other, null, null, 0, 25)).getStatusCode().value());
    }
    String token(UUID userId) {
        UUID session = UUID.randomUUID();
        jdbc.update("insert into staff_session(id,user_id,refresh_hash,created_at,expires_at) values (?,?,?,now(),now()+interval '1 hour')",
                session, userId, "a".repeat(64));
        return jwt.issue(users.findById(userId).orElseThrow(), session);
    }
    @Test void bearerIdentityAndLiveDatabaseRoleProtectRealHttpFlow() throws Exception {
        String adminToken = token(adminId), staffToken = token(staff);
        String path = "/api/staff/timekeeping";
        String result = mvc.perform(post(path + "/entries").header("Authorization", "Bearer " + adminToken).with(csrf())
                .contentType("application/json").content("{\"staffUserId\":\"" + staff + "\",\"clockIn\":\"2025-06-01T10:00:00Z\",\"clockOut\":\"2025-06-01T11:00:00Z\",\"reason\":\"Historical shift\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.workedSeconds").value(3600))
                .andReturn().getResponse().getContentAsString();
        String entryId = com.jayway.jsonpath.JsonPath.read(result, "$.id");
        mvc.perform(get(path + "/me").header("Authorization", "Bearer " + staffToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].staffUserId").value(staff.toString()));
        mvc.perform(get(path + "/entries/" + entryId + "/audit").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].newValues.clockIn").value("2025-06-01T10:00:00Z"))
                .andExpect(jsonPath("$.entries[0].actorId").value(adminId.toString()));
        mvc.perform(get(path + "/entries/" + entryId + "/audit").header("Authorization", "Bearer " + staffToken)).andExpect(status().isForbidden());
        jdbc.update("update staff_user set role='FOH' where id=?", adminId);
        mvc.perform(post(path + "/clock-in").header("Authorization", "Bearer " + adminToken).with(csrf())
                .contentType("application/json").content("{\"staffUserId\":\"" + other + "\"}")).andExpect(status().isForbidden());
        jdbc.update("update staff_user set enabled=false where id=?", staff);
        mvc.perform(get(path + "/me").header("Authorization", "Bearer " + staffToken)).andExpect(status().isUnauthorized());
    }
}
