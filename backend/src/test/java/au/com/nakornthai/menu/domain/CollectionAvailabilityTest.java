package au.com.nakornthai.menu.domain;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionAvailabilityTest {
    private static final String ZONE="Australia/Melbourne";
    private Instant instant(String local) { return LocalDateTime.parse(local).atZone(ZoneId.of(ZONE)).toInstant(); }
    private boolean available(List<CollectionAvailability.Rule> rules, String local) {
        return CollectionAvailability.evaluate("PUBLISHED",true,null,null,ZONE,rules,instant(local)).available();
    }
    private CollectionAvailability.Rule weekly(int day,String start,String end,boolean active) {
        return new CollectionAvailability.Rule("WEEKLY",(short)day,null,start==null?null:LocalTime.parse(start),end==null?null:LocalTime.parse(end),active);
    }
    @Test void broadBoundsAndLifecycle() {
        var start=instant("2026-09-07T17:00"); var end=instant("2026-09-07T18:00");
        assertTrue(CollectionAvailability.evaluate("PUBLISHED",true,start,end,ZONE,List.of(),start).available());
        assertFalse(CollectionAvailability.evaluate("PUBLISHED",true,start,end,ZONE,List.of(),end).available());
        assertFalse(CollectionAvailability.evaluate("DRAFT",true,null,null,ZONE,List.of(),start).available());
        assertFalse(CollectionAvailability.evaluate("ARCHIVED",true,null,null,ZONE,List.of(),start).available());
        assertFalse(CollectionAvailability.evaluate("PUBLISHED",false,null,null,ZONE,List.of(),start).available());
        assertFalse(CollectionAvailability.evaluate("PUBLISHED",true,start,end,ZONE,List.of(),start.minusNanos(1)).available());
    }
    @Test void overnightBelongsToStartingDayAndEndIsExclusive() {
        var rules=List.of(weekly(1,"17:00","01:00",true));
        assertFalse(available(rules,"2026-09-07T00:30"));
        assertTrue(available(rules,"2026-09-07T17:00"));
        assertTrue(available(rules,"2026-09-08T00:59"));
        assertFalse(available(rules,"2026-09-08T01:00"));
        assertFalse(available(rules,"2026-09-08T17:00"));
    }
    @Test void specificDateAddsAWindowAndCanCrossMidnight() {
        var rules=List.of(weekly(1,"11:00","14:00",true),new CollectionAvailability.Rule("SPECIFIC_DATE",null,
                LocalDate.parse("2026-09-08"),LocalTime.of(17,0),LocalTime.of(1,0),true));
        assertTrue(available(rules,"2026-09-07T11:00"));
        assertFalse(available(rules,"2026-09-07T14:00"));
        assertTrue(available(rules,"2026-09-09T00:30"));
        assertFalse(available(rules,"2026-09-09T01:00"));
    }
    @Test void noRowsIsUnrestrictedButDisabledOrInvalidRowsAreNot() {
        assertTrue(available(List.of(),"2026-09-07T12:00"));
        assertFalse(available(List.of(weekly(1,null,null,false)),"2026-09-07T12:00"));
        assertTrue(available(List.of(weekly(1,null,null,true)),"2026-09-07T12:00"));
        assertFalse(available(List.of(weekly(1,"12:00","12:00",true)),"2026-09-07T12:00"));
        assertFalse(CollectionAvailability.evaluate("PUBLISHED",true,null,null,"invalid/zone",List.of(),Instant.now()).available());
    }
    @Test void timezoneAndDstUseActualLocalWallClock() {
        var rules=List.of(weekly(7,"02:00","03:00",true));
        // Both occurrences of 02:30 at the autumn overlap are inside the window.
        for(String now:List.of("2026-04-04T15:30:00Z","2026-04-04T16:30:00Z"))
            assertTrue(CollectionAvailability.evaluate("PUBLISHED",true,null,null,ZONE,rules,Instant.parse(now)).available());
        // At the spring jump, 03:00 is the exclusive end; 02:00 never occurs.
        assertFalse(CollectionAvailability.evaluate("PUBLISHED",true,null,null,ZONE,rules,Instant.parse("2026-10-03T16:00:00Z")).available());
    }
}
