package au.com.nakornthai.staffapp.timekeeping.model;

import java.math.BigDecimal;
import java.util.*;

/** Wire names deliberately match TimekeepingDtos in the backend. Timestamps remain ISO strings. */
public final class Attendance {
  private Attendance() {}
  public static final class Entry {
    public String id, staffUserId, clockIn, clockOut, createdBy, createdAt, updatedAt;
    public boolean open;
    public BigDecimal workedSeconds;
    public long version;
  }
  public static final class Timesheet {
    public List<Entry> entries = new ArrayList<>();
    public int page, size;
    public long totalElements, openShiftCount;
    public BigDecimal completedSeconds;
  }
  public static final class Values {
    public String clockIn, clockOut;
    public long version;
  }
  public static final class Audit {
    public String id, attendanceId, action, actorId, occurredAt, reason;
    public Values previousValues, newValues;
  }
  public static final class AuditPage {
    public List<Audit> entries = new ArrayList<>();
    public int page, size;
    public long totalElements;
  }
  public static final class Restaurant {
    public String timezone, evaluatedAt;
  }
}
