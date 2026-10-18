package au.com.nakornthai.staffapp.timekeeping.ui;

import au.com.nakornthai.staffapp.timekeeping.model.Attendance.Entry;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure presentation decisions, using the content viewport rather than device/screen size. */
public final class TimekeepingPresentation {
  private TimekeepingPresentation() {}
  public static boolean table(float contentWidthDp, float fontScale) {
    return contentWidthDp / Math.max(1f, fontScale) >= 840f;
  }
  public static boolean sideBySide(float contentWidthDp, float fontScale) {
    return contentWidthDp / Math.max(1f, fontScale) >= 480f;
  }
  public static boolean clockedIn(String staffId, List<Entry> open) {
    return staffId != null && open.stream().anyMatch(e -> staffId.equals(e.staffUserId) && e.open);
  }
  public static String timestamp(String value, ZoneId zone, Locale locale) {
    if (value == null) return null;
    return DateTimeFormatter.ofPattern("h:mm a\nd MMM uuuu · XXX", locale)
        .withZone(zone).format(Instant.parse(value));
  }
  /** Inclusive dates; Monday is the first day of a restaurant reporting week. */
  public static LocalDate[] range(String preset, LocalDate serverDay) {
    switch (preset) {
      case "WEEK":
        LocalDate monday = serverDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{monday, monday.plusDays(6)};
      case "MONTH": return new LocalDate[]{serverDay.withDayOfMonth(1), serverDay.withDayOfMonth(serverDay.lengthOfMonth())};
      case "TODAY": return new LocalDate[]{serverDay, serverDay};
      default: throw new IllegalArgumentException("Unknown date preset");
    }
  }
}
