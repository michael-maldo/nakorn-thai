package au.com.nakornthai.identity.login;
import java.time.Instant;
import java.util.UUID;
public record LoginResponse(String accessToken, Instant expiresAt, User user) {
    public record User(UUID id,String username,String role,boolean enabled,Long version) {}
}
