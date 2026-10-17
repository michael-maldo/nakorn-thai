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
