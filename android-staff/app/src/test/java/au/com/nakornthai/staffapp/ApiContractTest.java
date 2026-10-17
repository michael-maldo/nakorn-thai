package au.com.nakornthai.staffapp;

import static org.junit.Assert.*;

import au.com.nakornthai.staffapp.identity.data.*;
import au.com.nakornthai.staffapp.ordering.data.*;
import au.com.nakornthai.staffapp.ordering.model.*;
import au.com.nakornthai.staffapp.shared.network.*;
import au.com.nakornthai.staffapp.shared.security.CredentialStore;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiContractTest {
  MockWebServer server;
  IdentityRepository identity;
  ApiOrderRepository orders;
  MemoryStore store;
  SessionCookieJar jar;
  static final String CSRF = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"csrf\"}";

  static String session(String token) {
    return "{\"accessToken\":\""
        + token
        + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"staff\",\"username\":\"staff\",\"role\":\"FOH\",\"enabled\":true}}";
  }

  static class MemoryStore implements CredentialStore {
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
  }

  @Before
  public void setup() throws Exception {
    server = new MockWebServer();
    server.start();
    store = new MemoryStore();
    jar = new SessionCookieJar(store, server.url("/"));
    StaffApi api =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                new OkHttpClient.Builder().cookieJar(jar).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StaffApi.class);
    identity = new IdentityRepository(api, jar);
    orders = new ApiOrderRepository(api, identity);
  }

  @After
  public void tearDown() throws Exception {
    server.shutdown();
  }

  void json(String body) {
    server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
  }

  void login() throws Exception {
    json(CSRF);
    server.enqueue(
        new MockResponse()
            .setBody(session("first"))
            .addHeader(
                "Set-Cookie",
                "nakorn_staff_refresh=rotating; Path=/api/identity; Max-Age=43200; HttpOnly"));
    identity.login("staff", "not-a-real-password");
  }

  @Test
  public void loginCsrfCookiesAnd204StatusContract() throws Exception {
    login();
    assertTrue(store.read().contains("nakorn_staff_refresh"));
    server.takeRequest();
    RecordedRequest request = server.takeRequest();
    assertEquals("/api/identity/login", request.getPath());
    assertEquals("csrf", request.getHeader("X-CSRF-TOKEN"));
    json(CSRF);
    server.enqueue(new MockResponse().setResponseCode(204));
    OrderDraft draft = new OrderDraft(OrderWorkflowTest.order("a", "NEW", 3), "ACCEPTED");
    orders.mutate(draft, identity.generation());
    assertEquals("/api/staff/orders/csrf", server.takeRequest().getPath());
    request = server.takeRequest();
    assertEquals("PATCH", request.getMethod());
    assertEquals("Bearer first", request.getHeader("Authorization"));
    assertNull(request.getHeader("Cookie"));
    assertTrue(request.getBody().readUtf8().contains("\"version\":3"));
  }

  @Test
  public void concurrent401sRefreshOnceAndUseRotatedToken() throws Exception {
    login();
    server.takeRequest();
    server.takeRequest();
    server.enqueue(new MockResponse().setResponseCode(401));
    json(CSRF);
    server.enqueue(
        new MockResponse()
            .setBody(session("second"))
            .addHeader(
                "Set-Cookie", "nakorn_staff_refresh=new; Path=/api/identity; Max-Age=43200"));
    json("[]");
    json("[]");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> a =
          pool.submit(
              () -> {
                try {
                  orders.load();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      Future<?> b =
          pool.submit(
              () -> {
                try {
                  orders.load();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      a.get(5, TimeUnit.SECONDS);
      b.get(5, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    int refreshes = 0;
    for (int i = 0; i < 5; i++) {
      RecordedRequest request = server.takeRequest();
      if (request.getPath().equals("/api/identity/refresh")) {
        refreshes++;
        assertTrue(request.getHeader("Cookie").contains("rotating"));
      }
    }
    assertEquals(1, refreshes);
    assertTrue(store.read().contains("new"));
  }

  @Test
  public void persistedRefreshRestoresSessionAndExpiryClearsIt() throws Exception {
    store.replace(
        server.url("/") + "\nnakorn_staff_refresh=saved; path=/api/identity; max-age=43200");
    SessionCookieJar restored = new SessionCookieJar(store, server.url("/"));
    StaffApi api =
        new Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(new OkHttpClient.Builder().cookieJar(restored).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StaffApi.class);
    identity = new IdentityRepository(api, restored);
    json(CSRF);
    json(session("restored"));
    identity.restore();
    assertNotNull(identity.current());
    json(CSRF);
    server.enqueue(new MockResponse().setResponseCode(401));
    try {
      identity.refresh();
      fail();
    } catch (ApiFailure failure) {
      assertEquals(401, failure.status);
    }
    assertNull(identity.current());
    assertEquals("", store.read());
  }

  @Test
  public void logoutClearsEvenIfServerRejects() throws Exception {
    login();
    json(CSRF);
    server.enqueue(new MockResponse().setResponseCode(403));
    try {
      identity.logout();
      fail();
    } catch (ApiFailure expected) {
    }
    assertNull(identity.current());
    assertEquals("", store.read());
  }

  @Test
  public void conflictAndPayidCommandPreserveVersion() throws Exception {
    login();
    server.takeRequest();
    server.takeRequest();
    json(CSRF);
    server.enqueue(
        new MockResponse().setResponseCode(409).setBody("{\"message\":\"Order changed\"}"));
    OrderDraft draft = new OrderDraft(OrderWorkflowTest.order("a", "NEW", 9), "PAYID");
    draft.bankReference = "BANK";
    draft.bankReceiptChecked = true;
    try {
      orders.mutate(draft, identity.generation());
      fail();
    } catch (ApiFailure error) {
      assertEquals(409, error.status);
    }
    server.takeRequest();
    RecordedRequest request = server.takeRequest();
    assertEquals("/api/staff/payments/a/payid-confirm", request.getPath());
    assertTrue(request.getBody().readUtf8().contains("bankReceiptChecked"));
  }

  @Test
  public void cookieJarDoesNotLeakToOtherOriginsOrOrderPaths() throws Exception {
    login();
    assertTrue(
        jar.loadForRequest(HttpUrl.get("https://example.org/api/identity/refresh")).isEmpty());
    assertTrue(jar.loadForRequest(server.url("/api/staff/orders")).isEmpty());
    assertFalse(jar.loadForRequest(server.url("/api/identity/refresh")).isEmpty());
  }

  @Test
  public void disconnectedMutationIsNotAutomaticallyRetried() throws Exception {
    login();
    server.takeRequest();
    server.takeRequest();
    json(CSRF);
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    try {
      orders.mutate(
          new OrderDraft(OrderWorkflowTest.order("a", "NEW", 1), "ACCEPTED"),
          identity.generation());
      fail();
    } catch (ApiFailure failure) {
      assertTrue(failure.uncertain);
    }
    assertEquals(4, server.getRequestCount());
  }

  @Test
  public void bohUsesKitchenEndpointAndCannotVerifyPayment() throws Exception {
    json(CSRF);
    server.enqueue(new MockResponse().setBody(session("boh").replace("FOH", "BOH")));
    identity.login("fixture-boh", "fixture");
    server.takeRequest();
    server.takeRequest();
    json("[]");
    orders.load();
    assertEquals("/api/staff/kitchen/orders", server.takeRequest().getPath());
    try {
      orders.mutate(
          new OrderDraft(OrderWorkflowTest.order("a", "NEW", 0), "PAYID"), identity.generation());
      fail();
    } catch (ApiFailure failure) {
      assertEquals(403, failure.status);
    }
    assertEquals(3, server.getRequestCount());
  }

  @Test
  public void changedPrincipalCancelsQueuedMutationBeforeDispatch() throws Exception {
    login();
    long owner = identity.generation();
    identity.clear();
    try {
      orders.mutate(new OrderDraft(OrderWorkflowTest.order("a", "NEW", 0), "ACCEPTED"), owner);
      fail();
    } catch (ApiFailure failure) {
      assertEquals(409, failure.status);
    }
    assertEquals(2, server.getRequestCount());
  }

  @Test
  public void repeated401ClearsSessionAfterOneRefresh() throws Exception {
    login();
    server.enqueue(new MockResponse().setResponseCode(401));
    json(CSRF);
    json(session("second"));
    server.enqueue(new MockResponse().setResponseCode(401));
    try {
      orders.load();
      fail();
    } catch (ApiFailure failure) {
      assertEquals(401, failure.status);
    }
    assertNull(identity.current());
    assertEquals("", store.read());
    assertEquals(6, server.getRequestCount());
  }

  @Test
  public void csrfSessionCookieIsSentButNeverPersisted() throws Exception {
    server.enqueue(
        new MockResponse()
            .setBody(CSRF)
            .addHeader("Set-Cookie", "JSESSIONID=memory-csrf-session; Path=/; HttpOnly"));
    server.enqueue(
        new MockResponse()
            .setBody(session("access"))
            .addHeader(
                "Set-Cookie",
                "nakorn_staff_refresh=refresh-only; Path=/api/identity; Max-Age=43200; HttpOnly"));
    identity.login("fixture", "fixture");
    server.takeRequest();
    assertTrue(server.takeRequest().getHeader("Cookie").contains("JSESSIONID=memory-csrf-session"));
    assertFalse(store.read().contains("JSESSIONID"));
    assertTrue(store.read().contains("refresh-only"));
    json(CSRF);
    server.enqueue(new MockResponse().setResponseCode(204));
    orders.mutate(
        new OrderDraft(OrderWorkflowTest.order("a", "NEW", 0), "ACCEPTED"), identity.generation());
    server.takeRequest();
    String cookie = server.takeRequest().getHeader("Cookie");
    assertTrue(cookie.contains("JSESSIONID"));
    assertFalse(cookie.contains("nakorn_staff_refresh"));
  }
}
