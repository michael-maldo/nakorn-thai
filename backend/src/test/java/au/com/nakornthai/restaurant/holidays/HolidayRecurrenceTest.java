package au.com.nakornthai.restaurant.holidays;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class HolidayRecurrenceTest {
    private LocalDate date(String value) { return LocalDate.parse(value); }

    @Test void annualPeriodCrossesYearBoundary() {
        var start = date("2026-12-24");
        var end = date("2027-01-04");
        assertTrue(HolidayRecurrence.ANNUAL.supports(start, end));
        assertEquals(new HolidayRecurrence.Period(date("2028-12-24"), date("2029-01-04")),
                HolidayRecurrence.ANNUAL.occurrenceStartingIn(start, end, 2028).orElseThrow());
        assertFalse(HolidayRecurrence.ANNUAL.supports(start, date("2027-12-24")));
        assertTrue(HolidayRecurrence.ONE_TIME.supports(start, date("2027-12-24")));
    }

    @Test void annualLeapDayEndpointsSkipMissingDates() {
        var leap = date("2024-02-29");
        assertTrue(HolidayRecurrence.ANNUAL.occurrenceStartingIn(leap, leap, 2025).isEmpty());
        assertEquals(date("2028-02-29"),
                HolidayRecurrence.ANNUAL.occurrenceStartingIn(leap, leap, 2028).orElseThrow().start());
        assertTrue(HolidayRecurrence.ANNUAL.occurrenceStartingIn(date("2023-12-31"), leap, 2024).isEmpty());
        assertTrue(HolidayRecurrence.ANNUAL.occurrenceStartingIn(date("2023-12-31"), leap, 2027).isPresent());
        assertTrue(HolidayRecurrence.ONE_TIME.occurrenceStartingIn(leap, leap, 2028).isEmpty());
    }
}
