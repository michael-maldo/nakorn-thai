package au.com.nakornthai.staffapp;

import au.com.nakornthai.staffapp.app.*;
import au.com.nakornthai.staffapp.shared.security.CredentialStore;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.*;

/** Instrumentation-only local HTTP fixture. Never included in the application APK. */
public final class FixtureApplication extends StaffApplication {
  public MockWebServer server;
  public volatile String status = "NEW", role = "FOH";
  public volatile boolean paid, offline, expired;
  public final AtomicInteger mutations = new AtomicInteger(), reads = new AtomicInteger();
  public final AtomicInteger attendanceMutations = new AtomicInteger();
  public volatile int attendanceWriteFailure;
  private com.google.gson.JsonObject attendanceEntry;
  private final com.google.gson.JsonArray attendanceEvents = new com.google.gson.JsonArray();

  public synchronized void resetAttendance() {
    attendanceEntry = null;
    attendanceEvents.asList().clear();
    attendanceMutations.set(0);
    attendanceWriteFailure = 0;
  }

  @Override
  protected AppGraph createGraph() {
    server = new MockWebServer();
    try {
      server.start();
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            String path = request.getPath();
            if (path.endsWith("/csrf"))
              return json("{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fixture-csrf\"}");
            if (path.endsWith("/login")) {
              String loginBody = request.getBody().readUtf8();
              role =
                  loginBody.contains("fixture-admin")
                      ? "ADMIN"
                      : loginBody.contains("fixture-boh") ? "BOH" : "FOH";
              expired = false;
              return json(session())
                  .addHeader(
                      "Set-Cookie",
                      "nakorn_staff_refresh=fixture; Path=/api/identity; Max-Age=43200");
            }
            if (path.endsWith("/logout")) return new MockResponse().setResponseCode(204);
            if (path.endsWith("/refresh"))
              return expired ? new MockResponse().setResponseCode(401) : json(session());
            if (expired) return new MockResponse().setResponseCode(401);
            if (path.equals("/api/restaurant/availability"))
              return json("{\"timezone\":\"Australia/Melbourne\",\"evaluatedAt\":\"2026-10-18T05:00:00Z\",\"open\":true}");
            if (path.equals("/api/identity/users"))
              return role.equals("ADMIN") ? json("[{\"id\":\"fixture-staff\",\"username\":\"Fixture Staff\",\"role\":\"FOH\",\"enabled\":true},{\"id\":\"disabled\",\"username\":\"Disabled Staff\",\"role\":\"BOH\",\"enabled\":false}]") : new MockResponse().setResponseCode(403);
            if (path.startsWith("/api/staff/timekeeping/")) return attendance(request);
            if (path.contains("/status")) {
              String body = request.getBody().readUtf8();
              for (String next :
                  new String[] {"ACCEPTED", "PREPARING", "READY", "COMPLETED", "CANCELLED"})
                if (body.contains(next)) status = next;
              mutations.incrementAndGet();
              return new MockResponse().setResponseCode(204);
            }
            if (path.contains("/payid-confirm") || path.endsWith("/check")) {
              paid = true;
              mutations.incrementAndGet();
              return json("{\"paid\":true}");
            }
            if (path.contains("/orders")) {
              reads.incrementAndGet();
              if (offline) return new MockResponse().setResponseCode(503);
              return json(
                  status.equals("COMPLETED")
                          || status.equals("CANCELLED")
                          || role.equals("BOH") && status.equals("NEW")
                      ? "[]"
                      : "[" + order() + "]");
            }
            return new MockResponse().setResponseCode(404);
          }
        });
    return new AppGraph(
        server.url("/").toString(),
        new CredentialStore() {
          String value = "";

          public String read() {
            return value;
          }

          public void replace(String v) {
            value = v;
          }

          public void clear() {
            value = "";
          }
        });
  }

  private MockResponse json(String value) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(value);
  }

  private synchronized MockResponse attendance(RecordedRequest request) {
    String path = request.getRequestUrl().encodedPath();
    boolean write = !"GET".equals(request.getMethod());
    if (!role.equals("ADMIN") && !path.endsWith("/me")) return new MockResponse().setResponseCode(403);
    if (write) {
      attendanceMutations.incrementAndGet();
      if (attendanceWriteFailure != 0) return new MockResponse().setResponseCode(attendanceWriteFailure).setBody("{\"message\":\"Fixture attendance conflict. Refresh attendance.\"}");
      com.google.gson.JsonObject body = com.google.gson.JsonParser.parseString(request.getBody().readUtf8()).getAsJsonObject();
      com.google.gson.JsonObject before = attendanceEntry == null ? null : attendanceEntry.deepCopy();
      if (path.endsWith("clock-in")) {
        if (attendanceEntry != null && attendanceEntry.get("clockOut").isJsonNull()) return new MockResponse().setResponseCode(409);
        attendanceEntry = com.google.gson.JsonParser.parseString("{\"id\":\"fixture-entry\",\"staffUserId\":\"fixture-staff\",\"clockIn\":\"2026-10-18T05:00:00Z\",\"clockOut\":null,\"version\":0}").getAsJsonObject();
      } else if (path.endsWith("clock-out")) {
        if (attendanceEntry == null || !attendanceEntry.get("clockOut").isJsonNull()) return new MockResponse().setResponseCode(409);
        attendanceEntry.addProperty("clockOut", "2026-10-18T06:00:00Z");
        attendanceEntry.addProperty("version", attendanceEntry.get("version").getAsLong() + 1);
      } else {
        if (!body.has("reason") || body.get("reason").getAsString().isBlank()) return new MockResponse().setResponseCode(400);
        if ("PATCH".equals(request.getMethod()) && (attendanceEntry == null || body.get("version").getAsLong() != attendanceEntry.get("version").getAsLong())) return new MockResponse().setResponseCode(409);
        long version = "PATCH".equals(request.getMethod()) ? attendanceEntry.get("version").getAsLong() + 1 : 0;
        attendanceEntry = body.deepCopy(); attendanceEntry.addProperty("id", "fixture-entry");
        attendanceEntry.addProperty("staffUserId", "fixture-staff");attendanceEntry.addProperty("version", version);
      }
      boolean open = attendanceEntry.get("clockOut").isJsonNull();
      attendanceEntry.addProperty("open", open);
      attendanceEntry.addProperty("createdBy", "fixture-staff");
      attendanceEntry.addProperty("createdAt", "2026-10-18T05:00:00Z");
      attendanceEntry.addProperty("updatedAt", "2026-10-18T06:00:00Z");
      if (open) attendanceEntry.add("workedSeconds", com.google.gson.JsonNull.INSTANCE);
      else attendanceEntry.addProperty("workedSeconds", java.time.Duration.between(java.time.Instant.parse(attendanceEntry.get("clockIn").getAsString()), java.time.Instant.parse(attendanceEntry.get("clockOut").getAsString())).getSeconds());
      com.google.gson.JsonObject event = new com.google.gson.JsonObject();
      event.addProperty("id", "event-" + attendanceMutations.get()); event.addProperty("attendanceId", "fixture-entry");
      event.addProperty("actorId", "fixture-staff");event.addProperty("occurredAt", "2026-10-18T06:00:00Z");
      event.addProperty("action", path.endsWith("clock-in") ? "CLOCK_IN" : path.endsWith("clock-out") ? "CLOCK_OUT" : "PATCH".equals(request.getMethod()) ? "CORRECTION" : "MANUAL_ENTRY");
      event.add("previousValues", before);event.add("newValues", attendanceEntry.deepCopy());
      event.add("reason", body.get("reason"));attendanceEvents.add(event);
      return json(attendanceEntry.toString()).setResponseCode(path.endsWith("clock-in") || "POST".equals(request.getMethod()) && path.endsWith("entries") ? 201 : 200);
    }
    if (path.endsWith("/audit")) return json("{\"entries\":" + attendanceEvents + ",\"page\":0,\"size\":25,\"totalElements\":" + attendanceEvents.size() + "}");
    boolean match = attendanceEntry != null;
    String from = request.getRequestUrl().queryParameter("from"), to = request.getRequestUrl().queryParameter("to");
    if (match && from != null && !attendanceEntry.get("clockOut").isJsonNull()) match = java.time.Instant.parse(attendanceEntry.get("clockOut").getAsString()).isAfter(java.time.Instant.parse(from));
    if (match && to != null) match = java.time.Instant.parse(attendanceEntry.get("clockIn").getAsString()).isBefore(java.time.Instant.parse(to));
    String size = request.getRequestUrl().queryParameter("size");
    boolean open = match && attendanceEntry.get("clockOut").isJsonNull();
    return json("{\"entries\":" + (match ? "[" + attendanceEntry + "]" : "[]") + ",\"page\":0,\"size\":" + (size == null ? "25" : size) + ",\"totalElements\":" + (match ? 1 : 0) + ",\"openShiftCount\":" + (open ? 1 : 0) + ",\"completedSeconds\":" + (match && !open ? attendanceEntry.get("workedSeconds") : "0") + "}");
  }

  private String session() {
    return "{\"accessToken\":\"fixture-access\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"fixture-staff\",\"username\":\"fixture\",\"role\":\""
        + role
        + "\",\"enabled\":true}}";
  }

  private String order() {
    return "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"reference\":\"TEST0001\",\"status\":\""
        + status
        + "\",\"version\":"
        + mutations.get()
        + ",\"paymentMethod\":\"PAY_AT_RESTAURANT\",\"totalMinor\":2400,\"createdAt\":\"2026-10-17T06:00:00Z\",\"customerName\":"
        + (role.equals("BOH") ? "null" : "\"Fixture Customer\"")
        + ",\"items\":[{\"dishName\":\"Fixture curry\",\"quantity\":1,\"selectedOptions\":[]}]}";
  }
}
