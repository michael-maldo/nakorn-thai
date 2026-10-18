package au.com.nakornthai.staff.timekeeping;

import au.com.nakornthai.identity.infrastructure.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static au.com.nakornthai.staff.timekeeping.TimekeepingDtos.*;

@Service @RequiredArgsConstructor
@Transactional
public class TimekeepingHandler {
    private final EntityManager em;
    private final SpringDataUserRepository users;

    private UserJpaEntity identity(String username) {
        return users.findByUsername(username).filter(UserJpaEntity::isEnabled)
                .orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "Staff account is unavailable"));
    }
    private UUID administrator(String username) {
        var actor = identity(username);
        if (!"ADMIN".equals(actor.getRole())) throw error(HttpStatus.FORBIDDEN, "Administrator access required");
        return actor.getId();
    }
    private void lockStaff(UUID id) {
        if (em.find(UserJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
            throw error(HttpStatus.NOT_FOUND, "Staff account not found");
    }
    private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
    private Instant timestamp(Instant value) {
        // PostgreSQL stores microseconds; reject unsupported ranges and precision rather than silently rounding.
        if (value == null || value.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
                || !value.isBefore(Instant.parse("+10000-01-01T00:00:00Z")) || value.getNano() % 1000 != 0)
            throw error(HttpStatus.BAD_REQUEST, "Use an offset timestamp in years 0001–9999 with at most six fractional digits");
        return value;
    }
    private void ordering(Instant start, Instant end) {
        timestamp(start); if (end != null) timestamp(end);
        if (end != null && !end.isAfter(start)) throw error(HttpStatus.BAD_REQUEST, "Clock-out must be after clock-in");
    }
    private String reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw error(HttpStatus.BAD_REQUEST, "An audit reason of 1–1000 characters is required");
        return reason.strip();
    }
    private void overlaps(UUID staff, UUID excluded, Instant start, Instant end) {
        String hql = "select count(e) from AttendanceJpaEntity e where e.staffUserId = :staff "
                + "and (e.clockOut is null or e.clockOut > :start)"
                + (end == null ? "" : " and e.clockIn < :end")
                + (excluded == null ? "" : " and e.id <> :excluded");
        var query = em.createQuery(hql, Long.class).setParameter("staff", staff).setParameter("start", start);
        if (end != null) query.setParameter("end", end);
        if (excluded != null) query.setParameter("excluded", excluded);
        if (query.getSingleResult() > 0) throw error(HttpStatus.CONFLICT, "Attendance overlaps an existing shift");
    }
    private AttendanceJpaEntity open(UUID staff) {
        return em.createQuery("from AttendanceJpaEntity where staffUserId = :staff and clockOut is null", AttendanceJpaEntity.class)
                .setParameter("staff", staff).getResultStream().findFirst().orElse(null);
    }
    private Entry create(UUID staff, Instant start, Instant end, UUID actor, String action, String reason) {
        ordering(start, end); overlaps(staff, null, start, end);
        var entry = new AttendanceJpaEntity(); entry.setId(UUID.randomUUID()); entry.setStaffUserId(staff);
        entry.setClockIn(start); entry.setClockOut(end); entry.setCreatedBy(actor);
        Instant now = now(); entry.setCreatedAt(now); entry.setUpdatedAt(now);
        em.persist(entry); em.flush();
        audit(entry, action, actor, null, reason);
        return Entry.of(entry);
    }
    private void audit(AttendanceJpaEntity entry, String action, UUID actor, Values before, String reason) {
        em.persist(new AttendanceAuditJpaEntity(entry, action, actor, now(), before, reason));
        em.flush();
    }
    public Entry clockIn(UUID staff, String username) {
        UUID actor = administrator(username); lockStaff(staff);
        if (open(staff) != null) throw error(HttpStatus.CONFLICT, "Staff member already has an open shift");
        return create(staff, now(), null, actor, "CLOCK_IN", null);
    }
    public Entry clockOut(UUID staff, String username) {
        UUID actor = administrator(username); lockStaff(staff);
        var entry = open(staff);
        if (entry == null) throw error(HttpStatus.CONFLICT, "Staff member has no open shift");
        return change(entry, entry.getClockIn(), now(), actor, "CLOCK_OUT", null);
    }
    public Entry manual(ManualRequest request, String username) {
        UUID actor = administrator(username); lockStaff(request.staffUserId());
        if (request.clockOut() == null) throw error(HttpStatus.BAD_REQUEST, "Manual entry requires clock-out");
        return create(request.staffUserId(), request.clockIn(), request.clockOut(), actor, "MANUAL_ENTRY", reason(request.reason()));
    }
    public Entry correct(UUID id, CorrectionRequest request, String username) {
        UUID actor = administrator(username);
        var entry = em.find(AttendanceJpaEntity.class, id);
        if (entry == null) throw error(HttpStatus.NOT_FOUND, "Attendance entry not found");
        lockStaff(entry.getStaffUserId());
        // The initial lookup may precede another transaction's commit; reload under the employee lock.
        em.refresh(entry, LockModeType.PESSIMISTIC_WRITE);
        if (!entry.getVersion().equals(request.version())) throw error(HttpStatus.CONFLICT, "Attendance changed; reload before saving");
        return change(entry, request.clockIn(), request.clockOut(), actor, "CORRECTION", reason(request.reason()));
    }
    private Entry change(AttendanceJpaEntity entry, Instant start, Instant end, UUID actor, String action, String reason) {
        ordering(start, end); overlaps(entry.getStaffUserId(), entry.getId(), start, end);
        var before = new Values(entry.getClockIn(), entry.getClockOut(), entry.getVersion());
        Instant modified = now();
        // Ensure every accepted correction changes the row, even within one database microsecond.
        if (!modified.isAfter(entry.getUpdatedAt())) modified = entry.getUpdatedAt().plus(1, ChronoUnit.MICROS);
        entry.setClockIn(start); entry.setClockOut(end); entry.setUpdatedAt(modified);
        em.flush(); audit(entry, action, actor, before, reason);
        return Entry.of(entry);
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Timesheet mine(String username, Instant from, Instant to, int page, int size) {
        return timesheet(identity(username).getId(), from, to, page, size);
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Timesheet all(String username, UUID staff, Instant from, Instant to, int page, int size) {
        administrator(username); return timesheet(staff, from, to, page, size);
    }
    private Timesheet timesheet(UUID staff, Instant from, Instant to, int page, int size) {
        pagination(page, size);
        if (from != null) timestamp(from); if (to != null) timestamp(to);
        if (from != null && to != null && !to.isAfter(from)) throw error(HttpStatus.BAD_REQUEST, "Range end must be after range start");
        // Select intersecting shifts in [from,to); totals use full completed durations, independent of pagination.
        String where = " where 1=1" + (staff == null ? "" : " and e.staffUserId = :staff")
                + (from == null ? "" : " and (e.clockOut is null or e.clockOut > :from)")
                + (to == null ? "" : " and e.clockIn < :to");
        var params = new HashMap<String,Object>();
        if (staff != null) params.put("staff", staff); if (from != null) params.put("from", from); if (to != null) params.put("to", to);
        var rows = em.createQuery("from AttendanceJpaEntity e" + where + " order by e.clockIn desc, e.id desc", AttendanceJpaEntity.class);
        params.forEach(rows::setParameter);
        var count = em.createQuery("select count(e) from AttendanceJpaEntity e" + where, Long.class); params.forEach(count::setParameter);
        var opens = em.createQuery("select count(e) from AttendanceJpaEntity e" + where + " and e.clockOut is null", Long.class); params.forEach(opens::setParameter);
        // Hibernate timestamp subtraction is a duration; extract epoch in native SQL preserves fractional seconds.
        String sqlWhere = where.replace("e.staffUserId", "e.staff_user_id").replace("e.clockOut", "e.clock_out").replace("e.clockIn", "e.clock_in");
        var total = em.createNativeQuery("select coalesce(sum(extract(epoch from (e.clock_out - e.clock_in))),0) from staff_attendance e" + sqlWhere, BigDecimal.class);
        params.forEach(total::setParameter);
        return new Timesheet(rows.setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(Entry::of).toList(),
                page, size, count.getSingleResult(), (BigDecimal) total.getSingleResult(), opens.getSingleResult());
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public AuditPage history(UUID id, String username, int page, int size) {
        administrator(username); pagination(page, size);
        if (em.find(AttendanceJpaEntity.class, id) == null) throw error(HttpStatus.NOT_FOUND, "Attendance entry not found");
        var entries = em.createQuery("from AttendanceAuditJpaEntity where attendanceId = :id order by occurredAt, id", AttendanceAuditJpaEntity.class)
                .setParameter("id", id).setFirstResult(page * size).setMaxResults(size).getResultList().stream().map(AttendanceAuditJpaEntity::view).toList();
        long count = em.createQuery("select count(a) from AttendanceAuditJpaEntity a where a.attendanceId = :id", Long.class).setParameter("id", id).getSingleResult();
        return new AuditPage(entries, page, size, count);
    }
    private void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw error(HttpStatus.BAD_REQUEST, "Use page >= 0 and size between 1 and 100 within the supported offset");
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
