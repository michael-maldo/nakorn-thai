package au.com.nakornthai.menu.domain;

import java.time.*;
import java.util.List;

/** Local wall-clock windows are additive and anchored to their starting date. */
public final class CollectionAvailability {
    private CollectionAvailability() {}
    public record Rule(String type, Short dayOfWeek, LocalDate date,
                       LocalTime start, LocalTime end, boolean active) {}
    public record Result(boolean available, String reason, Instant evaluatedAt) {}

    public static Result evaluate(String status, boolean active, Instant start, Instant end,
                                  String timezone, List<Rule> rules, Instant now) {
        String reason = null;
        if (!"PUBLISHED".equals(status)) reason = "NOT_PUBLISHED";
        else if (!active) reason = "INACTIVE";
        else if (start != null && now.isBefore(start)) reason = "NOT_STARTED";
        else if (end != null && !now.isBefore(end)) reason = "ENDED";
        else {
            try {
                var local = now.atZone(ZoneId.of(timezone));
                if (!rules.isEmpty() && rules.stream().noneMatch(r -> matches(r, local.toLocalDate(), local.toLocalTime())))
                    reason = "OUTSIDE_SCHEDULE";
            } catch (DateTimeException | NullPointerException invalidZone) {
                reason = "INVALID_TIMEZONE";
            }
        }
        return new Result(reason == null, reason == null ? "AVAILABLE" : reason, now);
    }

    private static boolean matches(Rule rule, LocalDate date, LocalTime time) {
        if (!rule.active()) return false;
        if (rule.start() == null && rule.end() == null) return dayMatches(rule, date);
        if (rule.start() == null || rule.end() == null || rule.start().equals(rule.end())) return false;
        if (rule.start().isBefore(rule.end()))
            return dayMatches(rule, date) && !time.isBefore(rule.start()) && time.isBefore(rule.end());
        return (dayMatches(rule, date) && !time.isBefore(rule.start()))
                || (dayMatches(rule, date.minusDays(1)) && time.isBefore(rule.end()));
    }

    private static boolean dayMatches(Rule rule, LocalDate date) {
        return "WEEKLY".equals(rule.type()) && rule.dayOfWeek() != null && rule.dayOfWeek() == date.getDayOfWeek().getValue()
                || "SPECIFIC_DATE".equals(rule.type()) && date.equals(rule.date());
    }
}
