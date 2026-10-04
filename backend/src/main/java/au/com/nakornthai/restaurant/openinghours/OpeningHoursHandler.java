package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class OpeningHoursHandler {
    private final JpaRestaurantRepository restaurant;
    private final EntityManager em;
    private au.com.nakornthai.restaurant.configuration.ConfigurationHandler audit;
    @org.springframework.beans.factory.annotation.Autowired
    public void configureAudit(au.com.nakornthai.restaurant.configuration.ConfigurationHandler audit) { this.audit = audit; }
    private void audit(String action, Set<String> fields) {
        if (audit == null) return;
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        audit.recordChange("SETTINGS", action, fields, actor == null ? "SYSTEM" : actor.getName());
    }
    public record ScheduleView(RestaurantSettingsJpaEntity settings, List<OpeningHoursJpaEntity> hours,
                               List<ClosedDateJpaEntity> closedDates) {}
    @Transactional
    public ScheduleView read() {
        var settings = restaurant.settings(LockModeType.PESSIMISTIC_READ);
        return new ScheduleView(settings, restaurant.hours(), restaurant.closedDates());
    }
    @Transactional
    public RestaurantSettingsJpaEntity saveSettings(OpeningHoursRequest.Settings request) {
        try {
            if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) throw new DateTimeException("Unknown zone");
            ZoneId.of(request.timezone());
        } catch (DateTimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid IANA timezone, such as Australia/Melbourne");
        }
        var settings = restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        version(settings, request.version());
        settings.setTimezone(request.timezone());
        em.flush(); audit("RESTAURANT_SETTINGS_UPDATED", Set.of("timezone")); return settings;
    }
    @Transactional
    public OpeningHoursJpaEntity saveWindow(UUID id, OpeningHoursRequest.Window request) {
        restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        if (request.opensAt().equals(request.closesAt()) || request.opensAt().getNano() != 0 || request.closesAt().getNano() != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opening and closing times must differ and use whole seconds");
        var window = id == null ? new OpeningHoursJpaEntity() : find(OpeningHoursJpaEntity.class, id);
        if (id != null) version(window, request.version());
        window.setDayOfWeek(request.dayOfWeek()); window.setOpensAt(request.opensAt()); window.setClosesAt(request.closesAt());
        window.setActive(request.active()); window.setDisplayOrder(request.displayOrder());
        if (id == null) em.persist(window);
        em.flush(); audit("OPENING_HOURS_UPDATED", Set.of("dayOfWeek", "opensAt", "closesAt", "active", "displayOrder")); return window;
    }
    @Transactional
    public ClosedDateJpaEntity saveClosure(UUID id, OpeningHoursRequest.Closure request) {
        restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        var closure = id == null ? new ClosedDateJpaEntity() : find(ClosedDateJpaEntity.class, id);
        if (id != null) version(closure, request.version());
        boolean duplicate = restaurant.closedDates().stream().anyMatch(c -> c.getClosedDate().equals(request.closedDate()) && !c.getId().equals(id));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "This date is already closed");
        closure.setClosedDate(request.closedDate());
        closure.setReason(request.reason() == null || request.reason().isBlank() ? null : request.reason().trim());
        if (id == null) em.persist(closure);
        em.flush(); audit("CLOSED_DATE_UPDATED", Set.of("closedDate", "reason")); return closure;
    }
    @Transactional
    public void deleteWindow(UUID id, Long version) { delete(OpeningHoursJpaEntity.class, id, version); }
    @Transactional
    public void deleteClosure(UUID id, Long version) { delete(ClosedDateJpaEntity.class, id, version); }
    private <T extends RestaurantAuditJpaEntity> void delete(Class<T> type, UUID id, Long expectedVersion) {
        restaurant.settings(LockModeType.PESSIMISTIC_WRITE);
        var entity = find(type, id); version(entity, expectedVersion); em.remove(entity); em.flush();
        audit(type == OpeningHoursJpaEntity.class ? "OPENING_HOURS_DELETED" : "CLOSED_DATE_DELETED", Set.of("scheduleEntry"));
    }
    private <T> T find(Class<T> type, UUID id) {
        var entity = em.find(type, id);
        if (entity == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule entry was not found");
        return entity;
    }
    private void version(RestaurantAuditJpaEntity entity, Long expected) {
        if (expected == null || !expected.equals(entity.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Schedule entry changed; reload before saving");
    }
}
