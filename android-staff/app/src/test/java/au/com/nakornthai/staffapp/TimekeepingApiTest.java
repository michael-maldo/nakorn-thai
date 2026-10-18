package au.com.nakornthai.staffapp;

import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.shared.network.*;
import au.com.nakornthai.staffapp.timekeeping.data.*;
import au.com.nakornthai.staffapp.timekeeping.model.*;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

public class TimekeepingApiTest {
  MockWebServer server;
  IdentityRepository identity;
  TimekeepingRepository repository;
  static final String CSRF="{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"attendance-csrf\"}";
  static final String ENTRY="{\"id\":\"entry\",\"staffUserId\":\"staff\",\"clockIn\":\"2026-10-17T11:00:00Z\",\"clockOut\":null,\"open\":true,\"version\":4}";
  static final String SHEET="{\"entries\":["+ENTRY+"],\"page\":0,\"size\":25,\"totalElements\":1,\"openShiftCount\":1,\"completedSeconds\":0}";
  @Before public void setup() throws Exception {
    server=new MockWebServer();server.start();
    SessionCookieJar jar=new SessionCookieJar(new ApiContractTest.MemoryStore(),server.url("/"));
    StaffApi api=new Retrofit.Builder().baseUrl(server.url("/"))
        .client(new OkHttpClient.Builder().cookieJar(jar).retryOnConnectionFailure(false).readTimeout(2,TimeUnit.SECONDS).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(StaffApi.class);
    identity=new IdentityRepository(api,jar);repository=new TimekeepingRepository(api,identity);
  }
  @After public void close() throws Exception {server.shutdown();}
  void json(String text) {server.enqueue(new MockResponse().setHeader("Content-Type","application/json").setBody(text));}
  String session(String role,String token) {
    return "{\"accessToken\":\""+token+"\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"staff\",\"username\":\"staff\",\"enabled\":true,\"role\":\""+role+"\"}}";
  }
  void login(String role) throws Exception {
    json(CSRF);server.enqueue(new MockResponse().setBody(session(role,"first")).addHeader("Set-Cookie","nakorn_staff_refresh=fixture; Path=/api/identity; Max-Age=43200"));
    identity.login("staff","fixture-password");server.takeRequest();server.takeRequest();
  }
  @Test public void staffReadsUseMeWithoutAnyClientSuppliedUserId() throws Exception {
    login("FOH");json(SHEET);
    assertEquals(1,repository.sheet(identity.generation(),"another-user","2026-10-01T00:00:00Z","2026-11-01T00:00:00Z",0).openShiftCount);
    RecordedRequest request=server.takeRequest();assertEquals("/api/staff/timekeeping/me",request.getRequestUrl().encodedPath());
    assertNull(request.getRequestUrl().queryParameter("staffUserId"));assertEquals("Bearer first",request.getHeader("Authorization"));
    assertEquals("2026-10-01T00:00:00Z",request.getRequestUrl().queryParameter("from"));
  }
  @Test public void adminListingUsesStaffFilterAndPagination() throws Exception {
    login("ADMIN");json(SHEET);repository.sheet(identity.generation(),"employee",null,null,3);
    RecordedRequest r=server.takeRequest();assertEquals("/api/staff/timekeeping/entries",r.getRequestUrl().encodedPath());
    assertEquals("employee",r.getRequestUrl().queryParameter("staffUserId"));assertEquals("3",r.getRequestUrl().queryParameter("page"));
  }
  @Test public void liveInAndOutSubmitOnlyStaffAndNeverDeviceTimes() throws Exception {
    login("ADMIN");
    for(String operation:new String[]{"clock-in","clock-out"}) {
      json(CSRF);json(ENTRY);repository.mutate(identity.generation(),AttendanceCommand.live(operation,"employee"));
      assertEquals("/api/staff/timekeeping/csrf",server.takeRequest().getPath());
      RecordedRequest r=server.takeRequest();assertEquals("POST",r.getMethod());assertEquals("/api/staff/timekeeping/"+operation,r.getPath());
      assertEquals("attendance-csrf",r.getHeader("X-CSRF-TOKEN"));assertEquals("Bearer first",r.getHeader("Authorization"));
      assertEquals("{\"staffUserId\":\"employee\"}",r.getBody().readUtf8());
    }
  }
  @Test public void correctionSendsVersionReasonAndExplicitNullClockOut() throws Exception {
    login("ADMIN");json(CSRF);json(ENTRY);
    Attendance.Entry e=new Attendance.Entry();e.id="entry";e.staffUserId="staff";e.version=4;
    repository.mutate(identity.generation(),AttendanceCommand.entry(e,null,"2026-10-17T22:00:00+11:00","",true,"Reopen accidental end",ZoneId.of("Australia/Melbourne")));
    server.takeRequest();RecordedRequest r=server.takeRequest();assertEquals("PATCH",r.getMethod());assertEquals("/api/staff/timekeeping/entries/entry",r.getPath());
    com.google.gson.JsonObject body=com.google.gson.JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
    assertEquals(4,body.get("version").getAsInt());assertEquals("Reopen accidental end",body.get("reason").getAsString());
    assertTrue(body.has("clockOut"));assertTrue(body.get("clockOut").isJsonNull());assertFalse(body.has("staffUserId"));
  }
  @Test public void manualEntryUsesBackendDtoAndAuditReadsArePaged() throws Exception {
    login("ADMIN");json(CSRF);json(ENTRY);
    repository.mutate(identity.generation(),AttendanceCommand.entry(null,"staff","2026-10-17T22:00:00+11:00","2026-10-18T02:00:00+11:00",false,"Missed shift",ZoneId.of("Australia/Melbourne")));
    server.takeRequest();RecordedRequest r=server.takeRequest();assertEquals("/api/staff/timekeeping/entries",r.getPath());
    assertTrue(r.getBody().readUtf8().contains("2026-10-17T15:00:00Z"));
    json("{\"entries\":[{\"id\":\"audit\",\"actorId\":\"admin\",\"action\":\"CORRECTION\",\"reason\":\"Fix\",\"previousValues\":{\"version\":0},\"newValues\":{\"version\":1}}],\"page\":1,\"size\":25,\"totalElements\":26}");
    assertEquals("Fix",repository.audit(identity.generation(),"entry",1).entries.get(0).reason);
    assertEquals("/api/staff/timekeeping/entries/entry/audit?page=1&size=25",server.takeRequest().getPath());
  }
  @Test public void duplicateOverlapNoOpenAndForbiddenErrorsAreNotReplayed() throws Exception {
    login("ADMIN");
    for(String message:new String[]{"Staff member already has an open shift","Staff member has no open shift","Attendance overlaps an existing shift","Attendance changed; reload before saving"}) {
      json(CSRF);server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"message\":\""+message+"\"}"));
      ApiFailure failure=assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-in","staff")));
      assertEquals(409,failure.status);assertEquals(message,failure.getMessage());server.takeRequest();server.takeRequest();
    }
    json(CSRF);server.enqueue(new MockResponse().setResponseCode(403));
    assertEquals(403,assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-out","staff"))).status);
    server.takeRequest();server.takeRequest();assertEquals(12,server.getRequestCount()); // two login requests plus five one-shot mutations
  }
  @Test public void lostMutationResponseIsUncertainAndSentOnce() throws Exception {
    login("ADMIN");json(CSRF);server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    ApiFailure failure=assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-in","staff")));
    assertTrue(failure.uncertain);assertEquals(4,server.getRequestCount());
  }
  @Test public void staffCannotMutateOrReadAuditEvenThroughRepository() throws Exception {
    login("BOH");assertEquals(403,assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-in","staff"))).status);
    assertEquals(403,assertThrows(ApiFailure.class,()->repository.audit(identity.generation(),"entry",0)).status);
    assertEquals(2,server.getRequestCount());
  }
  @Test public void expiredReadRefreshesOnceButExpiredMutationIsNeverReplayed() throws Exception {
    login("FOH");server.enqueue(new MockResponse().setResponseCode(401));json(CSRF);json(session("FOH","renewed"));json(SHEET);
    repository.sheet(identity.generation(),null,null,null,0);
    assertEquals("Bearer first",server.takeRequest().getHeader("Authorization"));server.takeRequest();server.takeRequest();
    assertEquals("Bearer renewed",server.takeRequest().getHeader("Authorization"));
    login("ADMIN");json(CSRF);server.enqueue(new MockResponse().setResponseCode(401));
    assertEquals(401,assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-in","staff"))).status);
    assertNull(identity.current());
  }
  @Test public void configuredTimezoneUsesServerResponseAndOpenScanHasNoRange() throws Exception {
    login("ADMIN");json("{\"timezone\":\"Australia/Perth\",\"evaluatedAt\":\"2026-10-17T23:30:00Z\"}");
    assertEquals("Australia/Perth",repository.restaurant(identity.generation()).timezone);
    assertEquals("/api/restaurant/availability",server.takeRequest().getPath());json(SHEET);
    assertEquals(1,repository.openShifts(identity.generation()).size());RecordedRequest r=server.takeRequest();
    assertNull(r.getRequestUrl().queryParameter("from"));assertNull(r.getRequestUrl().queryParameter("to"));
    assertEquals("100",r.getRequestUrl().queryParameter("size"));
  }
  @Test public void expiredCsrfRequestClearsSessionBeforeAnyMutation() throws Exception {
    login("ADMIN");server.enqueue(new MockResponse().setResponseCode(401));
    assertEquals(401,assertThrows(ApiFailure.class,()->repository.mutate(identity.generation(),AttendanceCommand.live("clock-in","staff"))).status);
    assertNull(identity.current());assertEquals(3,server.getRequestCount());
  }
}
