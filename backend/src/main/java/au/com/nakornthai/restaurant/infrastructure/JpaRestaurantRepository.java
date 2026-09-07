package au.com.nakornthai.restaurant.infrastructure;

import au.com.nakornthai.restaurant.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Repository @RequiredArgsConstructor
public class JpaRestaurantRepository implements RestaurantRepository {
    private final EntityManager em;
    // Settings is also the schedule lock: all admin writes take WRITE before touching rows.
    public RestaurantSettingsJpaEntity settings(LockModeType lock) {
        var settings = em.find(RestaurantSettingsJpaEntity.class, (short) 1, lock);
        if (settings == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Restaurant scheduling is not configured");
        return settings;
    }
    public List<OpeningHoursJpaEntity> hours() {
        return em.createQuery("from OpeningHoursJpaEntity order by dayOfWeek, displayOrder, opensAt, id", OpeningHoursJpaEntity.class).getResultList();
    }
    public List<ClosedDateJpaEntity> closedDates() {
        return em.createQuery("from ClosedDateJpaEntity order by closedDate", ClosedDateJpaEntity.class).getResultList();
    }
    @Override @Transactional
    public RestaurantSchedule schedule() {
        var settings = settings(LockModeType.PESSIMISTIC_READ);
        return new RestaurantSchedule(ZoneId.of(settings.getTimezone()), hours().stream().map(h ->
                new OpeningHours(h.getDayOfWeek(), h.getOpensAt(), h.getClosesAt(), h.isActive())).toList(),
                closedDates().stream().map(ClosedDateJpaEntity::getClosedDate).collect(Collectors.toSet()));
    }
}
