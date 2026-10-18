package au.com.nakornthai.staffapp.timekeeping.model;

import java.math.*;
import java.time.*;
import java.time.format.*;
import java.util.Locale;

public final class AttendanceTime {
  private AttendanceTime() {}
  public static String display(String timestamp, ZoneId zone) {
    if (timestamp == null) return "Open — not completed";
    return DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss XXX '['VV']'", Locale.UK)
        .withZone(zone).format(Instant.parse(timestamp));
  }
  public static String hours(BigDecimal seconds) {
    return seconds == null ? "Incomplete" : seconds.divide(BigDecimal.valueOf(3600), 4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " h";
  }
  public static String boundary(String date, ZoneId zone, boolean afterDay) {
    LocalDate day = LocalDate.parse(date);
    if (afterDay) day = day.plusDays(1);
    return day.atStartOfDay(zone).toInstant().toString();
  }
  public static void range(String start, String end) {
    if (LocalDate.parse(end).isBefore(LocalDate.parse(start))) throw new IllegalArgumentException("End date must be on or after start date.");
  }
  /** Explicit offsets disambiguate repeated daylight-saving times; also verify the restaurant offset. */
  public static String input(String text, ZoneId zone) {
    OffsetDateTime time;
    try { time = OffsetDateTime.parse(text.trim()); }
    catch (DateTimeException e) { throw new IllegalArgumentException("Use a full date and time with offset, e.g. 2026-10-18T09:00:00+11:00."); }
    if (!zone.getRules().getValidOffsets(time.toLocalDateTime()).contains(time.getOffset()))
      throw new IllegalArgumentException("That time/offset does not exist in " + zone + ". Check daylight saving.");
    if (time.getYear() < 1 || time.getYear() > 9999 || time.getNano() % 1000 != 0)
      throw new IllegalArgumentException("Use years 0001–9999 and at most six fractional digits.");
    return time.toInstant().toString();
  }
  public static String edit(String timestamp, ZoneId zone) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.parse(timestamp).atZone(zone));
  }
}
