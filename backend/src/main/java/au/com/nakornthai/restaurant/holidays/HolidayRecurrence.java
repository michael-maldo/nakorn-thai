package au.com.nakornthai.restaurant.holidays;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Optional;

public enum HolidayRecurrence {
    ONE_TIME, ANNUAL;

    public boolean supports(LocalDate start, LocalDate end) {
        if (start.getYear() < 1 || end.getYear() > 9999 || end.isBefore(start)) return false;
        return this == ONE_TIME || end.getYear() == start.getYear()
                || (end.getYear() == start.getYear() + 1 && MonthDay.from(end).isBefore(MonthDay.from(start)));
    }

    /** Annual occurrences use fixed calendar endpoints and skip years with a missing endpoint. */
    public Optional<Period> occurrenceStartingIn(LocalDate start, LocalDate end, int year) {
        if (this == ONE_TIME) {
            return year == start.getYear() ? Optional.of(new Period(start, end)) : Optional.empty();
        }
        int endYear = year + end.getYear() - start.getYear();
        var first = MonthDay.from(start);
        var last = MonthDay.from(end);
        if (!first.isValidYear(year) || !last.isValidYear(endYear)) return Optional.empty();
        return Optional.of(new Period(first.atYear(year), last.atYear(endYear)));
    }

    public record Period(LocalDate start, LocalDate end) {}
}
