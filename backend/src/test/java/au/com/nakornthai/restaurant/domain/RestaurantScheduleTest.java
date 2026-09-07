package au.com.nakornthai.restaurant.domain;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RestaurantScheduleTest {
    final ZoneId melbourne = ZoneId.of("Australia/Melbourne");
    OpeningHours window(String opens, String closes) { return new OpeningHours(1, LocalTime.parse(opens), LocalTime.parse(closes), true); }
    RestaurantSchedule schedule(List<OpeningHours> hours, LocalDate... closures) { return new RestaurantSchedule(melbourne, hours, Set.of(closures)); }
    Instant at(String local) { return LocalDateTime.parse(local).atZone(melbourne).toInstant(); }
    @Test void normalWindowHasInclusiveOpeningAndExclusiveClosing() {
        var s = schedule(List.of(window("11:30", "14:30")));
        assertFalse(s.isOpen(at("2026-09-07T11:29:59")));
        assertTrue(s.isOpen(at("2026-09-07T11:30:00")));
        assertTrue(s.isOpen(at("2026-09-07T14:29:59")));
        assertFalse(s.isOpen(at("2026-09-07T14:30:00")));
        assertFalse(s.isOpen(at("2026-09-08T12:00:00")));
    }
    @Test void multipleWindowsKeepTheGapClosed() {
        var s = schedule(List.of(window("11:30", "14:30"), window("17:00", "22:00")));
        assertTrue(s.isOpen(at("2026-09-07T12:00:00")));
        assertFalse(s.isOpen(at("2026-09-07T15:00:00")));
        assertTrue(s.isOpen(at("2026-09-07T18:00:00")));
    }
    @Test void overnightBelongsToStartingWeekday() {
        var s = schedule(List.of(window("17:00", "01:00")));
        assertFalse(s.isOpen(at("2026-09-07T16:59:00")));
        assertTrue(s.isOpen(at("2026-09-07T17:00:00")));
        assertTrue(s.isOpen(at("2026-09-07T23:00:00")));
        assertTrue(s.isOpen(at("2026-09-08T00:30:00")));
        assertFalse(s.isOpen(at("2026-09-08T01:00:00")));
    }
    @Test void overnightWrapsFromSundayToMonday() {
        var s = schedule(List.of(new OpeningHours(7, LocalTime.of(17, 0), LocalTime.of(1, 0), true)));
        assertTrue(s.isOpen(at("2026-09-07T00:30:00")));
    }
    @Test void closedLocalDateOverridesNormalHours() {
        var s = schedule(List.of(window("11:30", "14:30")), LocalDate.of(2026, 9, 7));
        assertFalse(s.isOpen(at("2026-09-07T12:00:00")));
    }
    @Test void closedTuesdayBlocksOnlyTuesdayPortionOfMondayOvernightWindow() {
        var s = schedule(List.of(window("17:00", "01:00")), LocalDate.of(2026, 9, 8));
        assertTrue(s.isOpen(at("2026-09-07T23:00:00")));
        assertFalse(s.isOpen(at("2026-09-08T00:30:00")));
    }
    @Test void closedDateIsLocalRatherThanUtcAndDoesNotExtendToNextDate() {
        var s = schedule(List.of(window("17:00", "01:00")), LocalDate.of(2026, 9, 7));
        assertFalse(s.isOpen(at("2026-09-07T23:00:00")));
        assertTrue(s.isOpen(at("2026-09-08T00:30:00")));
    }
    @Test void missingAndInactiveHoursFailClosed() {
        assertFalse(schedule(List.of()).isOpen(at("2026-09-07T12:00:00")));
        assertFalse(schedule(List.of(new OpeningHours(1, LocalTime.of(11,30), LocalTime.of(14,30), false)))
                .isOpen(at("2026-09-07T12:00:00")));
    }
    @Test void configuredTimezoneIgnoresJvmDefaultAndObservesSummerOffset() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            var s = schedule(List.of(window("11:30", "14:30")));
            assertTrue(s.isOpen(Instant.parse("2026-09-07T01:30:00Z")));
            assertFalse(s.isOpen(Instant.parse("2026-09-07T11:30:00Z")));
            assertTrue(s.isOpen(Instant.parse("2026-01-05T00:30:00Z")));
        } finally { TimeZone.setDefault(original); }
    }
    @Test void otherConfiguredTimezoneIsAuthoritative() {
        var s = new RestaurantSchedule(ZoneId.of("Pacific/Auckland"), List.of(window("11:30", "14:30")), Set.of());
        assertTrue(s.isOpen(Instant.parse("2026-09-06T23:30:00Z")));
        assertFalse(s.isOpen(Instant.parse("2026-09-07T11:30:00Z")));
    }
    @Test void localRequestsRejectDstGapAndOverlapAndConvertNormalTimes() {
        var s = schedule(List.of());
        assertThrows(IllegalArgumentException.class, () -> s.requestedInstant(LocalDateTime.parse("2026-10-04T02:30:00")));
        assertThrows(IllegalArgumentException.class, () -> s.requestedInstant(LocalDateTime.parse("2026-04-05T02:30:00")));
        assertEquals(Instant.parse("2026-09-07T08:00:00Z"), s.requestedInstant(LocalDateTime.parse("2026-09-07T18:00:00")));
    }
    @Test void instantEvaluationHandlesBothDstOverlapOffsets() {
        var s = schedule(List.of(new OpeningHours(7, LocalTime.of(1,0), LocalTime.of(3,0), true)));
        assertTrue(s.isOpen(Instant.parse("2026-04-04T15:30:00Z")));
        assertTrue(s.isOpen(Instant.parse("2026-04-04T16:30:00Z")));
    }
    @Test void equalEndpointsAndInvalidWeekdaysAreInvalid() {
        assertThrows(IllegalArgumentException.class, () -> window("17:00", "17:00"));
        assertThrows(IllegalArgumentException.class, () -> new OpeningHours(0, LocalTime.NOON, LocalTime.MIDNIGHT, true));
    }
}
