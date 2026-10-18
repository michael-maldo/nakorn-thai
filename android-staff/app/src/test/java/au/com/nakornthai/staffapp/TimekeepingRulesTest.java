package au.com.nakornthai.staffapp;

import au.com.nakornthai.staffapp.identity.data.Session;
import au.com.nakornthai.staffapp.timekeeping.data.TimekeepingRepository;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimekeepingRulesTest {
  final ZoneId melbourne = ZoneId.of("Australia/Melbourne");
  @Test public void roleNavigationHasDistinctLabels() {
    for (String role : new String[]{"ADMIN", "FOH", "BOH"}) {
      Session s = session(role);
      assertEquals(role.equals("ADMIN") ? "Timekeeping" : "My Timesheet", TimekeepingRepository.title(s));
      assertEquals(role.equals("ADMIN"), TimekeepingRepository.admin(s));
    }
    assertFalse(TimekeepingRepository.admin(null));
  }
  static Session session(String role) {
    Session s = new Session(); s.accessToken = "token"; s.expiresAt = "2099-01-01T00:00:00Z";
    s.user = new Session.User(); s.user.id = "staff"; s.user.username = "staff"; s.user.enabled = true; s.user.role = role;
    return s;
  }
  @Test public void manualOvernightUsesInstantsAndRequiredReason() {
    AttendanceCommand c = AttendanceCommand.entry(null,"staff","2026-10-16T22:00:00+11:00","2026-10-17T02:00:00+11:00",false," Historical ",melbourne);
    assertEquals("manual", c.operation); assertEquals("2026-10-16T11:00:00Z", c.body.get("clockIn"));
    assertEquals("2026-10-16T15:00:00Z", c.body.get("clockOut")); assertEquals("Historical", c.body.get("reason"));
    for (String reason : new String[]{"", " \t", "x".repeat(1001)}) assertThrows(IllegalArgumentException.class,
        () -> AttendanceCommand.entry(null,"staff","2026-10-16T22:00:00+11:00","2026-10-17T02:00:00+11:00",false,reason,melbourne));
  }
  @Test public void correctionCarriesVersionAndExplicitNullButCannotChangeEmployee() {
    Attendance.Entry e = new Attendance.Entry(); e.id = "entry"; e.staffUserId = "owner"; e.version = 7;
    AttendanceCommand c = AttendanceCommand.entry(e,"someone-else","2026-10-16T22:00:00+11:00","",true,"Reopen accidental end",melbourne);
    assertEquals("owner", c.staffUserId); assertEquals(7L, c.body.get("version"));
    assertEquals(com.google.gson.JsonNull.INSTANCE,c.body.get("clockOut")); assertFalse(c.body.containsKey("staffUserId"));
    assertThrows(IllegalArgumentException.class, () -> AttendanceCommand.entry(null,"staff","2026-10-16T22:00:00+11:00","",true,"Open",melbourne));
  }
  @Test public void invalidRangesOrderingAndMissingOffsetsAreRejected() {
    assertThrows(RuntimeException.class, () -> AttendanceTime.range("2026-11-01","2026-10-01"));
    assertThrows(IllegalArgumentException.class, () -> AttendanceTime.input("2026-10-16T22:00:00",melbourne));
    assertThrows(IllegalArgumentException.class, () -> AttendanceTime.input("2026-10-16T22:00:00+10:00",melbourne));
    assertThrows(IllegalArgumentException.class, () -> AttendanceCommand.entry(null,"staff","2026-10-16T22:00:00+11:00","2026-10-16T21:00:00+11:00",false,"Mistake",melbourne));
    assertThrows(IllegalArgumentException.class, () -> AttendanceCommand.live("clock-in",null));
    assertEquals(java.util.Set.of("staffUserId"), AttendanceCommand.live("clock-in","staff").body.keySet());
  }
  @Test public void daylightSavingBoundariesUseActual23And25HourDays() {
    assertEquals(23, Duration.between(Instant.parse(AttendanceTime.boundary("2026-10-04",melbourne,false)),Instant.parse(AttendanceTime.boundary("2026-10-04",melbourne,true))).toHours());
    assertEquals(25, Duration.between(Instant.parse(AttendanceTime.boundary("2026-04-05",melbourne,false)),Instant.parse(AttendanceTime.boundary("2026-04-05",melbourne,true))).toHours());
    assertThrows(IllegalArgumentException.class, () -> AttendanceTime.input("2026-10-04T02:30:00+10:00",melbourne));
    assertNotEquals(AttendanceTime.input("2026-04-05T02:30:00+11:00",melbourne),AttendanceTime.input("2026-04-05T02:30:00+10:00",melbourne));
    assertTrue(AttendanceTime.display("2026-04-04T15:30:00Z",melbourne).contains("+11:00"));
    assertTrue(AttendanceTime.display("2026-04-04T16:30:00Z",melbourne).contains("+10:00"));
  }
  @Test public void hoursArePresentationOnlyAndOpenIsNeverZeroHours() {
    assertEquals("4 h",AttendanceTime.hours(new BigDecimal("14400")));
    assertEquals("0.0003 h",AttendanceTime.hours(new BigDecimal("1.123456")));
    assertEquals("Incomplete",AttendanceTime.hours(null));
    assertTrue(AttendanceTime.display(null,melbourne).contains("Open"));
  }
}
