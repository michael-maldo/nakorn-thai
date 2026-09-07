package au.com.nakornthai.restaurant.domain;

import java.time.*;
import java.util.*;

/** A consistent restaurant-owned snapshot; all business time comes from its timezone. */
public record RestaurantSchedule(ZoneId timezone, List<OpeningHours> hours, Set<LocalDate> closedDates) {
    public RestaurantSchedule {
        Objects.requireNonNull(timezone);
        hours = List.copyOf(hours);
        closedDates = Set.copyOf(closedDates);
    }
    public boolean isOpen(Instant instant) {
        var local = instant.atZone(timezone);
        if (closedDates.contains(local.toLocalDate())) return false;
        int today = local.getDayOfWeek().getValue();
        int yesterday = local.minusDays(1).getDayOfWeek().getValue();
        var time = local.toLocalTime();
        return hours.stream().filter(OpeningHours::active).anyMatch(window -> {
            if (window.dayOfWeek() == today && !time.isBefore(window.opensAt()))
                return window.overnight() || time.isBefore(window.closesAt());
            return window.overnight() && window.dayOfWeek() == yesterday && time.isBefore(window.closesAt());
        });
    }
    /** Local booking requests have no offset, so gaps/overlaps cannot be silently resolved. */
    public Instant requestedInstant(LocalDateTime requested) {
        var offsets = timezone.getRules().getValidOffsets(requested);
        if (offsets.size() != 1)
            throw new IllegalArgumentException("Choose an unambiguous restaurant local time outside the daylight-saving transition");
        return requested.toInstant(offsets.getFirst());
    }
}
