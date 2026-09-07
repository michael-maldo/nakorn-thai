package au.com.nakornthai.menu.domain;

import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.restaurant.domain.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LunchSpecialAvailabilityTest {
    final ZoneId zone = ZoneId.of("Australia/Melbourne");
    MenuCollectionJpaEntity lunch() {
        var c = new MenuCollectionJpaEntity(); c.setStatus("PUBLISHED"); c.setDailyCutoffTime(LocalTime.of(14,30));
        c.setTimezone("America/Los_Angeles"); // Deliberately different from restaurant business time.
        return c;
    }
    RestaurantSchedule restaurant(boolean open, LocalDate... closures) {
        return new RestaurantSchedule(zone,List.of(new OpeningHours(1,LocalTime.of(11,30),LocalTime.of(22,0),open)),Set.of(closures));
    }
    Instant at(String time) { return LocalDateTime.parse("2026-09-07T"+time).atZone(zone).toInstant(); }
    @Test void cutoffIsEndExclusiveInRestaurantTimezoneRegardlessOfJvmOrCollectionZone() {
        var original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            assertTrue(MenuCatalogRules.availability(lunch(),at("14:29:00"),restaurant(true)).available());
            for(var time:List.of("14:30:00","14:31:00")) {
                var result=MenuCatalogRules.availability(lunch(),at(time),restaurant(true));
                assertFalse(result.available()); assertEquals("AFTER_CUTOFF",result.reason()); assertEquals(at(time),result.evaluatedAt());
            }
        } finally { TimeZone.setDefault(original); }
    }
    @Test void restaurantClosedAndClosedDateOverrideBeforeCutoff() {
        assertEquals("RESTAURANT_CLOSED",MenuCatalogRules.availability(lunch(),at("13:00:00"),restaurant(false)).reason());
        assertEquals("RESTAURANT_CLOSED",MenuCatalogRules.availability(lunch(),at("13:00:00"),restaurant(true,LocalDate.of(2026,9,7))).reason());
        assertTrue(MenuCatalogRules.availability(lunch(),at("13:00:00"),restaurant(true)).available());
    }
    @Test void changedRestaurantZoneIsAuthoritative() {
        var schedule=new RestaurantSchedule(ZoneId.of("Pacific/Auckland"),restaurant(true).hours(),Set.of());
        // 13:00 Melbourne is 15:00 Auckland.
        assertEquals("AFTER_CUTOFF",MenuCatalogRules.availability(lunch(),at("13:00:00"),schedule).reason());
    }
    @Test void lifecycleAndExistingScheduleRemainAdditionalRequirements() {
        var c=lunch(); c.setActive(false);
        assertEquals("INACTIVE",MenuCatalogRules.availability(c,at("13:00:00"),restaurant(true)).reason());
        c.setActive(true);c.setStatus("DRAFT");
        assertEquals("NOT_PUBLISHED",MenuCatalogRules.availability(c,at("13:00:00"),restaurant(true)).reason());
        c.setStatus("PUBLISHED");
        var rule=new MenuCollectionScheduleJpaEntity();rule.setRuleType("WEEKLY");rule.setDayOfWeek((short)1);rule.setActive(false);c.getSchedules().add(rule);
        assertEquals("OUTSIDE_SCHEDULE",MenuCatalogRules.availability(c,at("13:00:00"),restaurant(true)).reason());
    }
    @Test void noCutoffPreservesExistingCollectionBehaviorAndCutoffWithoutRestaurantFailsClosed() {
        var c=lunch(); assertFalse(MenuCatalogRules.availability(c,at("13:00:00")).available());
        c.setDailyCutoffTime(null);
        assertTrue(MenuCatalogRules.availability(c,at("18:00:00"),restaurant(false)).available());
    }
}
