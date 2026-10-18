package au.com.nakornthai.staffapp;

import au.com.nakornthai.staffapp.timekeeping.ui.TimekeepingPresentation;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance;
import java.time.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimekeepingPresentationTest {
  @Test public void tableUsesAvailableContentAndHonorsFontScaling() {
    assertTrue(TimekeepingPresentation.table(1000,1));
    assertFalse(TimekeepingPresentation.table(700,1));
    assertFalse(TimekeepingPresentation.table(1000,2));
    assertFalse(TimekeepingPresentation.table(320,1));
    assertTrue(TimekeepingPresentation.sideBySide(600,1));
    assertFalse(TimekeepingPresentation.sideBySide(320,1));
    assertFalse(TimekeepingPresentation.sideBySide(600,2));
  }
  @Test public void serverDayPresetsAreInclusiveMondayBasedAndLeapSafe() {
    LocalDate sunday=LocalDate.of(2026,10,18);
    assertArrayEquals(new LocalDate[]{sunday,sunday},TimekeepingPresentation.range("TODAY",sunday));
    assertArrayEquals(new LocalDate[]{LocalDate.of(2026,10,12),sunday},TimekeepingPresentation.range("WEEK",sunday));
    assertArrayEquals(new LocalDate[]{LocalDate.of(2024,2,1),LocalDate.of(2024,2,29)},TimekeepingPresentation.range("MONTH",LocalDate.of(2024,2,10)));
    assertArrayEquals(new LocalDate[]{LocalDate.of(2025,12,29),LocalDate.of(2026,1,4)},TimekeepingPresentation.range("WEEK",LocalDate.of(2026,1,1)));
  }
  @Test public void compactDatesDistinguishOvernightAndRepeatedTimes() {
    ZoneId zone=ZoneId.of("Australia/Melbourne");
    String before=TimekeepingPresentation.timestamp("2026-04-04T15:30:00Z",zone,Locale.UK);
    String after=TimekeepingPresentation.timestamp("2026-04-04T16:30:00Z",zone,Locale.UK);
    assertTrue(before.contains("+11:00"));assertTrue(after.contains("+10:00"));
    assertTrue(TimekeepingPresentation.timestamp("2026-10-17T11:00:00Z",zone,Locale.UK).contains("17 Oct 2026"));
    assertTrue(TimekeepingPresentation.timestamp("2026-10-17T15:00:00Z",zone,Locale.UK).contains("18 Oct 2026"));
  }
  @Test public void selectedStatusUsesOnlyThatEmployeesOpenSnapshot() {
    Attendance.Entry e=new Attendance.Entry();e.staffUserId="a";e.open=true;
    assertFalse(TimekeepingPresentation.clockedIn(null,List.of(e)));
    assertFalse(TimekeepingPresentation.clockedIn("b",List.of(e)));
    assertTrue(TimekeepingPresentation.clockedIn("a",List.of(e)));
    e.open=false;assertFalse(TimekeepingPresentation.clockedIn("a",List.of(e)));
  }
}
