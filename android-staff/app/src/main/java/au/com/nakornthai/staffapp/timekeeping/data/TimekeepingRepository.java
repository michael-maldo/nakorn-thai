package au.com.nakornthai.staffapp.timekeeping.data;

import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.shared.network.*;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.*;
import java.io.IOException;
import java.time.*;
import java.util.*;

public final class TimekeepingRepository {
  private final TimekeepingApi api;
  private final IdentityRepository identity;
  public TimekeepingRepository(TimekeepingApi api, IdentityRepository identity) { this.api = api; this.identity = identity; }
  public static boolean admin(Session session) { return session != null && session.valid() && "ADMIN".equals(session.user.role); }
  public static String title(Session session) { return admin(session) ? "Timekeeping" : "My Timesheet"; }
  private interface Read<T> { T run(String auth) throws IOException; }
  private <T> T read(long owner, Read<T> action) throws IOException {
    synchronized (identity) {
      for (int attempt = 0; ; attempt++) {
        identity.assertOwner(owner);
        try {
          String auth = identity.authorization(); identity.assertOwner(owner);
          T value = action.run(auth); identity.assertOwner(owner);
          if (value == null) throw new IOException("Empty timekeeping response. Refresh to retry.");
          return value;
        } catch (ApiFailure e) {
          if (e.status == 401 && attempt == 0) identity.refresh();
          else { if (e.status == 401) identity.clear(); throw e; }
        }
      }
    }
  }
  public Restaurant restaurant(long owner) throws IOException {
    return read(owner, auth -> {
      Restaurant r = HttpResult.execute(api.restaurant());
      if (r == null || r.timezone == null || r.evaluatedAt == null) throw new IOException("Restaurant timezone unavailable. Refresh to retry.");
      try { ZoneId.of(r.timezone); Instant.parse(r.evaluatedAt); }
      catch (DateTimeException e) { throw new IOException("Invalid restaurant timezone response."); }
      return r;
    });
  }
  public List<Session.User> staff(long owner) throws IOException {
    return read(owner, auth -> { requireAdmin(); return HttpResult.execute(api.staff(auth)); });
  }
  public Timesheet sheet(long owner, String staff, String from, String to, int page) throws IOException {
    return read(owner, auth -> validate(HttpResult.execute(admin(identity.current())
        ? api.attendance(auth, staff, from, to, page, 25) : api.myTimesheet(auth, from, to, page, 25))));
  }
  private Timesheet validate(Timesheet sheet) throws IOException {
    if (sheet == null || sheet.entries == null || sheet.completedSeconds == null || sheet.size < 1)
      throw new IOException("Invalid timesheet response. Refresh to retry.");
    for (Entry e : sheet.entries)
      if (e.id == null || e.staffUserId == null || e.clockIn == null || e.open != (e.clockOut == null))
        throw new IOException("Invalid attendance response. Refresh to retry.");
    return sheet;
  }
  /** Backend has no open-only filter: scan unfiltered pages until all reported open entries are found. */
  public List<Entry> openShifts(long owner) throws IOException {
    Map<String,Entry> open = new LinkedHashMap<>();
    for (int page = 0; ; page++) {
      int requestedPage = page;
      // Release the identity monitor between pages so order monitoring can continue.
      Timesheet sheet = read(owner, auth -> {
        requireAdmin();
        return validate(HttpResult.execute(api.attendance(auth, null, null, null, requestedPage, 100)));
      });
      for (Entry entry : sheet.entries) if (entry.open) open.put(entry.id, entry);
      if (open.size() >= sheet.openShiftCount || (long) (page + 1) * 100 >= sheet.totalElements)
        return new ArrayList<>(open.values());
    }
  }
  public AuditPage audit(long owner, String id, int page) throws IOException {
    return read(owner, auth -> {
      requireAdmin(); AuditPage result = HttpResult.execute(api.attendanceAudit(id, auth, page, 25));
      if (result == null || result.entries == null || result.size < 1) throw new IOException("Invalid audit response. Refresh attendance to retry.");
      return result;
    });
  }
  private void requireAdmin() throws ApiFailure {
    if (!admin(identity.current())) throw new ApiFailure(403, "Administrator access is required for attendance changes and audit history.");
  }
  public Entry mutate(long owner, AttendanceCommand command) throws IOException {
    synchronized (identity) {
      identity.assertOwner(owner); requireAdmin();
      String auth = identity.authorization(); identity.assertOwner(owner); requireAdmin();
      StaffApi.Csrf csrf;
      try { csrf = HttpResult.execute(api.timekeepingCsrf(auth)); }
      catch (ApiFailure failure) { if (failure.status == 401) identity.clear(); throw failure; }
      Map<String,String> headers = new HashMap<>(IdentityRepository.header(csrf));
      headers.put("Authorization", auth); identity.assertOwner(owner);
      try {
        Entry result = HttpResult.execute(switch (command.operation) {
          case "clock-in" -> api.clockIn(headers, command.body);
          case "clock-out" -> api.clockOut(headers, command.body);
          case "manual" -> api.manualAttendance(headers, command.body);
          // An explicit null clockOut reopens a shift. Isolate null serialization from other domains.
          case "correct" -> api.correctAttendance(command.entryId, headers, okhttp3.RequestBody.create(
              okhttp3.MediaType.get("application/json"), new com.google.gson.GsonBuilder().serializeNulls().create().toJson(command.body)));
          default -> throw new IllegalArgumentException("Unknown attendance operation");
        });
        if (result == null || result.id == null || result.clockIn == null) throw new IOException("Invalid mutation response");
        return result;
      } catch (ApiFailure e) {
        if (e.status == 401) identity.clear();
        throw e;
      } catch (IOException | RuntimeException e) {
        throw new ApiFailure(0, "Attendance outcome is unknown. Refresh and inspect attendance/audit before starting another action. Do not repeat the action automatically.", true);
      }
    }
  }
}
