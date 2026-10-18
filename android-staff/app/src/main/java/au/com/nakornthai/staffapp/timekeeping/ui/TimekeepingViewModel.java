package au.com.nakornthai.staffapp.timekeeping.ui;

import androidx.lifecycle.*;
import au.com.nakornthai.staffapp.app.AppGraph;
import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.shared.network.ApiFailure;
import au.com.nakornthai.staffapp.timekeeping.data.TimekeepingRepository;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import au.com.nakornthai.staffapp.timekeeping.model.Attendance.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executor;

public final class TimekeepingViewModel extends ViewModel {
  public enum Notice { INFO, LOADING, SUCCESS, ERROR }
  public static final class Draft {
    public Entry existing;
    public String staffId, in = "", out = "", reason = "";
    public String error = "";
    public boolean open;
  }
  public static final class State {
    public final boolean admin, busy, fresh, uncertain;
    public final boolean filtersPending;
    public final String message, from, to, selectedStaff;
    public final String rangePreset;
    public final Notice notice;
    public final ZoneId zone;
    public final List<Session.User> staff;
    public final List<Entry> open;
    public final Timesheet sheet;
    public final AuditPage audit;
    public final String auditId;
    public final AttendanceCommand pending;
    public final Draft draft;
    State(TimekeepingViewModel v) {
      admin = v.admin(); busy = v.working; fresh = v.fresh; uncertain = v.uncertain;
      message = v.message; from = v.fromDate; to = v.toDate; selectedStaff = v.staffId;
      rangePreset = v.rangePreset; notice = v.notice;
      filtersPending = v.sheet != null && (!Objects.equals(v.fromDate, v.appliedFrom)
          || !Objects.equals(v.toDate, v.appliedTo) || !Objects.equals(v.staffId, v.appliedStaff));
      zone = v.zone; staff = new ArrayList<>(v.staff); open = new ArrayList<>(v.open);
      sheet = v.sheet; audit = v.audit; auditId = v.auditId; pending = v.pending; draft = v.draft;
    }
    public boolean canWrite() { return admin && !busy && fresh && !uncertain; }
    public boolean activeSelected() { return selectedStaff != null && staff.stream().anyMatch(u -> u.enabled && selectedStaff.equals(u.id)); }
    public boolean selectedOpen() { return TimekeepingPresentation.clockedIn(selectedStaff, open); }
    public boolean canClockIn() { return canWrite() && pending == null && activeSelected() && !selectedOpen(); }
    public boolean canClockOut() { return canWrite() && pending == null && activeSelected() && selectedOpen(); }
  }
  private final IdentityRepository identity;
  private final TimekeepingRepository repository;
  private final Executor executor;
  private java.util.concurrent.ExecutorService ownedExecutor;
  private final MutableLiveData<State> mutable = new MutableLiveData<>();
  public final LiveData<State> state = mutable;
  private long owner = -1;
  private boolean working, fresh, uncertain, closed;
  private String message = "Loading attendance…";
  private Notice notice = Notice.LOADING;
  private String rangePreset = "TODAY";
  private LocalDate serverDay;
  private String appliedFrom, appliedTo, appliedStaff;
  public String fromDate = "", toDate = "", staffId;
  private ZoneId zone;
  private List<Session.User> staff = new ArrayList<>();
  private List<Entry> open = new ArrayList<>();
  private Timesheet sheet;
  private AuditPage audit;
  private String auditId;
  private AttendanceCommand pending;
  public Draft draft;

  public TimekeepingViewModel(AppGraph graph) {
    this(graph.timekeeping, graph.identity, java.util.concurrent.Executors.newSingleThreadExecutor());
    ownedExecutor = (java.util.concurrent.ExecutorService) executor;
  }
  public TimekeepingViewModel(TimekeepingRepository repository, IdentityRepository identity, Executor executor) {
    this.repository = repository; this.identity = identity; this.executor = executor;
  }
  public boolean admin() { return TimekeepingRepository.admin(identity.current()); }
  private void publish() { mutable.postValue(new State(this)); }
  public synchronized void sessionChanged() {
    long generation = identity.generation();
    if (owner == generation) return;
    owner = generation; working = false; fresh = false; uncertain = false;
    sheet = null; audit = null; auditId = null; pending = null; draft = null;
    staff = new ArrayList<>(); open = new ArrayList<>(); zone = null;
    fromDate = ""; toDate = ""; staffId = null;
    serverDay = null; rangePreset = "TODAY"; notice = Notice.LOADING;
    appliedFrom = null; appliedTo = null; appliedStaff = null;
    message = identity.current() == null ? "Please sign in again." : "Loading attendance…";
    publish();
    if (identity.current() != null) refresh(0);
  }
  private boolean owns(long expected) { return !closed && owner == expected && identity.generation() == expected && identity.current() != null; }
  public synchronized void error(String text) { message = text; notice = Notice.ERROR; if (draft != null) draft.error = text; publish(); }
  public synchronized void selectStaff(String selected) {
    if (working || Objects.equals(staffId, selected)) return;
    staffId = selected; pending = null; draft = null; publish();
  }
  /** Prepares filters only. Apply/Refresh remain the only read triggers. */
  public synchronized void selectRange(String preset) {
    if (working || serverDay == null) return;
    if (!"CUSTOM".equals(preset)) {
      LocalDate[] dates = TimekeepingPresentation.range(preset, serverDay);
      fromDate = dates[0].toString(); toDate = dates[1].toString();
    }
    rangePreset = preset; pending = null; draft = null; publish();
  }
  public synchronized void customDate(boolean start, String date) {
    if (working) return;
    LocalDate.parse(date);
    if (start) fromDate = date; else toDate = date;
    rangePreset = "CUSTOM"; pending = null; draft = null; publish();
  }
  public synchronized void filter(String from, String to, String staff) {
    if (working) return;
    try { AttendanceTime.range(from, to); }
    catch (RuntimeException e) { error("Choose valid dates (YYYY-MM-DD). End date must be on or after start date."); return; }
    fromDate = from; toDate = to; staffId = staff; draft = null; refresh(0);
  }
  public synchronized void today() {
    if (working) return;
    fromDate = ""; toDate = ""; refresh(0);
  }
  public synchronized void refresh(int page) {
    if (working || closed || identity.current() == null || page < 0) return;
    long expected = owner; working = true; pending = null; draft = null; audit = null; auditId = null; fresh = false;
    notice = Notice.LOADING;
    String from = fromDate, to = toDate, staffFilter = staffId;
    publish();
    executor.execute(() -> {
      try { load(expected, from, to, staffFilter, page, "Attendance refreshed. Review entries before starting a new action."); }
      catch (Exception e) { failed(expected, e); }
    });
  }
  private void load(long expected, String from, String to, String staffFilter, int page, String notice) throws Exception {
    Restaurant restaurant = repository.restaurant(expected);
    ZoneId restaurantZone = ZoneId.of(restaurant.timezone);
    if (from.isEmpty() || to.isEmpty()) {
      LocalDate day = Instant.parse(restaurant.evaluatedAt).atZone(restaurantZone).toLocalDate();
      from = day.toString(); to = day.toString();
    }
    AttendanceTime.range(from, to);
    List<Session.User> users = admin() ? repository.staff(expected) : Collections.emptyList();
    Timesheet next = repository.sheet(expected, staffFilter, AttendanceTime.boundary(from, restaurantZone, false),
        AttendanceTime.boundary(to, restaurantZone, true), page);
    List<Entry> opens = admin() ? repository.openShifts(expected) : Collections.emptyList();
    synchronized (this) {
      if (!owns(expected)) return;
      zone = restaurantZone; fromDate = from; toDate = to; staff = users; sheet = next; open = opens;
      serverDay = Instant.parse(restaurant.evaluatedAt).atZone(restaurantZone).toLocalDate();
      appliedFrom = from; appliedTo = to; appliedStaff = staffFilter;
      this.notice = notice.startsWith("Confirmed by server") ? Notice.SUCCESS : Notice.INFO;
      working = false; fresh = true; uncertain = false; message = notice; audit = null; auditId = null; publish();
    }
  }
  private synchronized void failed(long expected, Exception e) {
    if (!owns(expected)) return;
    working = false; fresh = false; pending = null;
    notice = Notice.ERROR;
    uncertain |= e instanceof ApiFailure && (((ApiFailure) e).uncertain || ((ApiFailure) e).status >= 500);
    message = e.getMessage() == null ? "Connection interrupted. Refresh to check attendance." : e.getMessage();
    if (e instanceof ApiFailure && ((ApiFailure) e).status == 409)
      message += " Refresh attendance and start a new correction using the latest version.";
    if (draft != null) draft.error = message;
    publish();
  }
  private boolean writable() { return admin() && !working && fresh && !uncertain && zone != null; }
  public synchronized void edit(Entry entry, String staffSelection) {
    if (!writable()) return;
    if (entry == null && staffSelection == null) { error("Select an active staff account before entering attendance."); return; }
    Draft next = new Draft(); next.existing = entry; next.staffId = entry == null ? staffSelection : entry.staffUserId;
    if (entry != null) {
      next.in = AttendanceTime.edit(entry.clockIn, zone); next.open = entry.open;
      next.out = entry.clockOut == null ? "" : AttendanceTime.edit(entry.clockOut, zone);
    }
    draft = next; pending = null; publish();
  }
  public synchronized void cancel() { if (!working) { pending = null; draft = null; publish(); } }
  public synchronized void reviewLive(String operation, String staffSelection) {
    if (!writable()) return;
    boolean active = staffSelection != null && staff.stream().anyMatch(u -> u.enabled && staffSelection.equals(u.id));
    boolean isOpen = TimekeepingPresentation.clockedIn(staffSelection, open);
    if (!active || ("clock-in".equals(operation) && isOpen) || ("clock-out".equals(operation) && !isOpen)) return;
    try { pending = AttendanceCommand.live(operation, staffSelection); draft = null; publish(); }
    catch (IllegalArgumentException e) { error(e.getMessage()); }
  }
  public synchronized void reviewDraft() {
    if (!writable() || draft == null) return;
    try { pending = AttendanceCommand.entry(draft.existing, draft.staffId, draft.in, draft.out, draft.open, draft.reason, zone); draft.error = ""; publish(); }
    catch (IllegalArgumentException e) { error(e.getMessage()); }
  }
  public synchronized boolean confirm() {
    if (!writable() || pending == null) return false;
    AttendanceCommand command = pending; pending = null;
    long expected = owner; working = true; fresh = false;
    String from = fromDate, to = toDate, staffFilter = staffId;
    message = "Submitting attendance once…"; notice = Notice.LOADING; publish();
    executor.execute(() -> {
      Entry saved;
      try { saved = repository.mutate(expected, command); }
      catch (Exception e) { failed(expected, e); return; }
      String notice;
      synchronized (this) {
        if (!owns(expected)) return;
        draft = null;
        notice = "Confirmed by server: " + command.operation + " · " + AttendanceTime.display(saved.clockIn, zone)
            + " → " + AttendanceTime.display(saved.clockOut, zone);
        message = notice; this.notice = Notice.SUCCESS; publish();
      }
      try { load(expected, from, to, staffFilter, 0, notice); }
      catch (Exception e) { failed(expected, new java.io.IOException(notice + "\nAttendance refresh failed. Refresh to load the latest list.")); }
    });
    return true;
  }
  public synchronized void audit(String id, int page) {
    if (!writable() || page < 0) return;
    long expected = owner; working = true; publish();
    executor.execute(() -> {
      try {
        AuditPage result = repository.audit(expected, id, page);
        synchronized (this) {
          if (!owns(expected)) return;
          audit = result; auditId = id; working = false; publish();
        }
      } catch (Exception e) { failed(expected, e); }
    });
  }
  @Override protected synchronized void onCleared() {
    closed = true; pending = null; draft = null;
    // Let an already accepted bounded mutation finish; never replay it on a recreated screen.
    if (ownedExecutor != null) ownedExecutor.shutdown();
  }
}
