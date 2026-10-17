package au.com.nakornthai.staffapp.shared.network;

import au.com.nakornthai.staffapp.shared.security.CredentialStore;
import java.util.*;
import okhttp3.*;

/** Origin-bound cookie jar; session CSRF cookies remain in memory only. */
public final class SessionCookieJar implements CookieJar {
  private final List<Cookie> cookies = new ArrayList<>();
  private final CredentialStore store;
  private final HttpUrl origin;

  public SessionCookieJar(CredentialStore store, HttpUrl origin) {
    this.store = store;
    this.origin = origin;
    String saved = store.read();
    String[] pieces = saved.split("\n", 2);
    if (pieces.length == 2 && pieces[0].equals(origin.toString())) {
      Cookie cookie = Cookie.parse(origin, pieces[1]);
      if (cookie != null
          && cookie.name().equals("nakorn_staff_refresh")
          && cookie.expiresAt() > System.currentTimeMillis()) cookies.add(cookie);
    }
  }

  private boolean sameOrigin(HttpUrl url) {
    return url.scheme().equals(origin.scheme())
        && url.host().equals(origin.host())
        && url.port() == origin.port();
  }

  public synchronized void saveFromResponse(HttpUrl url, List<Cookie> received) {
    if (!sameOrigin(url)) return;
    for (Cookie cookie : received) {
      cookies.removeIf(
          old ->
              old.name().equals(cookie.name())
                  && old.path().equals(cookie.path())
                  && old.domain().equals(cookie.domain()));
      if (cookie.expiresAt() > System.currentTimeMillis()) cookies.add(cookie);
      if (cookie.name().equals("nakorn_staff_refresh")) {
        if (cookie.expiresAt() > System.currentTimeMillis()) store.replace(origin + "\n" + cookie);
        else store.clear();
      }
    }
  }

  public synchronized List<Cookie> loadForRequest(HttpUrl url) {
    if (!sameOrigin(url)) return Collections.emptyList();
    cookies.removeIf(cookie -> cookie.expiresAt() <= System.currentTimeMillis());
    List<Cookie> matching = new ArrayList<>();
    for (Cookie cookie : cookies) if (cookie.matches(url)) matching.add(cookie);
    return matching;
  }

  public synchronized boolean hasRefresh() {
    return cookies.stream()
        .anyMatch(
            cookie ->
                cookie.name().equals("nakorn_staff_refresh")
                    && cookie.expiresAt() > System.currentTimeMillis());
  }

  public synchronized void clear() {
    cookies.clear();
    store.clear();
  }
}
