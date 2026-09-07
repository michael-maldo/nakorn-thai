package au.com.nakornthai.restaurant.domain;

import java.time.LocalTime;

public record OpeningHours(int dayOfWeek, LocalTime opensAt, LocalTime closesAt, boolean active) {
    public OpeningHours {
        if (dayOfWeek < 1 || dayOfWeek > 7 || opensAt == null || closesAt == null || opensAt.equals(closesAt))
            throw new IllegalArgumentException("Choose a weekday and distinct opening and closing times");
    }
    public boolean overnight() { return closesAt.isBefore(opensAt); }
}
