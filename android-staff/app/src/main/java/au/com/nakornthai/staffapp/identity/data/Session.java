package au.com.nakornthai.staffapp.identity.data;

import java.time.Instant;

public final class Session {
  public String accessToken;
  public String expiresAt;
  public User user;

  public static final class User {
    public String id, username, role;
    public boolean enabled;
  }

  public boolean valid() {
    return accessToken != null
        && !accessToken.isEmpty()
        && user != null
        && user.id != null
        && user.username != null
        && user.enabled
        && ("ADMIN".equals(user.role) || "FOH".equals(user.role) || "BOH".equals(user.role));
  }

  public boolean refreshDue() {
    try {
      return Instant.parse(expiresAt).isBefore(Instant.now().plusSeconds(30));
    } catch (Exception invalid) {
      return true;
    }
  }
}
