package au.com.nakornthai.staffapp.timekeeping.model;

import java.time.*;
import java.util.*;

public final class AttendanceCommand {
  public final String operation, entryId, staffUserId;
  public final Map<String, Object> body;
  private AttendanceCommand(String operation, String entryId, String staff, Map<String,Object> body) {
    this.operation = operation; this.entryId = entryId; this.staffUserId = staff;
    this.body = Collections.unmodifiableMap(new LinkedHashMap<>(body));
  }
  public static AttendanceCommand live(String operation, String staff) {
    if (!"clock-in".equals(operation) && !"clock-out".equals(operation)) throw new IllegalArgumentException("Unknown live action");
    if (staff == null || staff.isEmpty()) throw new IllegalArgumentException("Select an active staff account.");
    return new AttendanceCommand(operation, null, staff, Map.of("staffUserId", staff));
  }
  public static AttendanceCommand entry(Attendance.Entry existing, String staff, String in, String out,
      boolean open, String reason, ZoneId zone) {
    if (reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("Enter an audit reason of 1–1000 characters.");
    String start = AttendanceTime.input(in, zone);
    if (open && existing == null) throw new IllegalArgumentException("Manual entries require an end time.");
    String end = open ? null : AttendanceTime.input(out, zone);
    if (end != null && !Instant.parse(end).isAfter(Instant.parse(start))) throw new IllegalArgumentException("End time must be after start time, including the date for overnight shifts.");
    Map<String,Object> body = new LinkedHashMap<>();
    if (existing == null) {
      if (staff == null || staff.isEmpty()) throw new IllegalArgumentException("Select an active staff account.");
      body.put("staffUserId", staff);
    } else body.put("version", existing.version);
    // JsonNull is intentional: Gson's default map adapter otherwise drops null clockOut.
    body.put("clockIn", start); body.put("clockOut", end == null ? com.google.gson.JsonNull.INSTANCE : end);
    body.put("reason", reason.trim());
    return new AttendanceCommand(existing == null ? "manual" : "correct", existing == null ? null : existing.id,
        existing == null ? staff : existing.staffUserId, body);
  }
}
