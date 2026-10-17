package au.com.nakornthai.staffapp.identity.data;

import au.com.nakornthai.staffapp.shared.network.*;
import java.io.IOException;
import java.util.*;

/**
 * All network/session operations share this monitor. Refresh is single-flight and CSRF writes
 * cannot race reads.
 */
public final class IdentityRepository {
  private final StaffApi api;
  private final SessionCookieJar cookies;
  private volatile Session session;
  private volatile long generation;
  private Runnable listener = () -> {};

  public IdentityRepository(StaffApi api, SessionCookieJar cookies) {
    this.api = api;
    this.cookies = cookies;
  }

  public synchronized void setListener(Runnable listener) {
    this.listener = listener;
  }

  public Session current() {
    return session;
  }

  public long generation() {
    return generation;
  }

  public synchronized void login(String username, String password) throws IOException {
    StaffApi.Csrf csrf = HttpResult.execute(api.identityCsrf());
    Session result =
        HttpResult.execute(
            api.login(header(csrf), Map.of("username", username, "password", password)));
    publish(result, true);
  }

  public synchronized void restore() throws IOException {
    if (session == null && cookies.hasRefresh()) refresh();
  }

  public synchronized void refresh() throws IOException {
    try {
      if (!cookies.hasRefresh()) throw new ApiFailure(401, "Please sign in to begin monitoring.");
      StaffApi.Csrf csrf = HttpResult.execute(api.identityCsrf());
      Session next = HttpResult.execute(api.refresh(header(csrf), Collections.emptyMap()));
      publish(next, session == null || !samePrincipal(session, next));
    } catch (IOException | RuntimeException failure) {
      // A lost rotation response cannot safely replay the old refresh credential.
      clear();
      throw new ApiFailure(
          401, "Session refresh could not be completed safely. Please sign in again.");
    }
  }

  private boolean samePrincipal(Session a, Session b) {
    return b != null
        && b.user != null
        && a.user.id.equals(b.user.id)
        && a.user.role.equals(b.user.role);
  }

  private void publish(Session value, boolean newPrincipal) throws IOException {
    if (value == null || !value.valid()) {
      clear();
      throw new ApiFailure(401, "A valid staff session is required.");
    }
    session = value;
    if (newPrincipal) generation++;
    listener.run();
  }

  public synchronized void logout() throws IOException {
    try {
      StaffApi.Csrf csrf = HttpResult.execute(api.identityCsrf());
      HttpResult.execute(api.logout(header(csrf), Collections.emptyMap()));
    } finally {
      clear();
    }
  }

  public synchronized void clear() {
    session = null;
    generation++;
    try {
      cookies.clear();
    } finally {
      listener.run();
    }
  }

  public synchronized String authorization() throws IOException {
    if (session == null) throw new ApiFailure(401, "Please sign in again.");
    if (session.refreshDue()) refresh();
    return "Bearer " + session.accessToken;
  }

  public synchronized void assertOwner(long expected) throws IOException {
    if (session == null || generation != expected)
      throw new ApiFailure(409, "Staff session changed; action cancelled.");
  }

  public static Map<String, String> header(StaffApi.Csrf csrf) throws IOException {
    if (csrf == null || csrf.headerName == null || csrf.token == null)
      throw new IOException("Security token unavailable");
    return Map.of(csrf.headerName, csrf.token);
  }
}
