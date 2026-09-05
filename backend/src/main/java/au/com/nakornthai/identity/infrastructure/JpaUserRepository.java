package au.com.nakornthai.identity.infrastructure;
import au.com.nakornthai.identity.login.*;
import au.com.nakornthai.shared.security.JwtService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class JpaUserRepository {
    private final SpringDataUserRepository users;
    private final SpringDataStaffSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final EntityManager em;
    public record Tokens(LoginResponse response,String refreshToken,Instant refreshExpiry) {}
    public static LoginResponse.User view(UserJpaEntity u) { return new LoginResponse.User(u.getId(),u.getUsername(),u.getRole(),u.isEnabled(),u.getVersion()); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Tokens rotate(StaffSessionJpaEntity session) {
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String refresh=session.getId()+"."+HexFormat.of().formatHex(random);
        session.setRefreshHash(digest(refresh));
        return new Tokens(new LoginResponse(jwt.issue(session.getUser(),session.getId()),Instant.now().plusSeconds(900),view(session.getUser())),refresh,session.getExpiresAt());
    }
    @Transactional
    public Tokens login(LoginRequest request) {
        var user=users.findByUsername(request.username().toLowerCase(Locale.ROOT));
        // Match against a fixed bcrypt hash for unknown usernames as well.
        String candidate=user.map(UserJpaEntity::getPasswordHash).orElse("$2a$12$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");
        boolean valid=request.password().getBytes(StandardCharsets.UTF_8).length<=72 && passwords.matches(request.password(),candidate);
        if (!valid || user.isEmpty() || !user.get().isEnabled()) return null;
        var session=new StaffSessionJpaEntity();session.setUser(user.get());session.setExpiresAt(Instant.now().plusSeconds(43200));
        var tokens=rotate(session);em.persist(session);return tokens;
    }
    @Transactional
    public Tokens refresh(String token) {
        var session=locked(token);
        if (session==null || session.getRevokedAt()!=null || !session.getExpiresAt().isAfter(Instant.now())) return null;
        if (!matches(session,token) || !session.getUser().isEnabled()) {
            session.setRevokedAt(Instant.now());return null; // Commit reuse revocation before returning 401.
        }
        return rotate(session);
    }
    @Transactional
    public void logout(String token) {
        var session=locked(token);
        if (session!=null && matches(session,token)) session.setRevokedAt(Instant.now());
    }
    private StaffSessionJpaEntity locked(String token) {
        if (token==null || !token.matches("[a-f0-9-]{36}\\.[a-f0-9]{64}")) return null;
        try { return em.find(StaffSessionJpaEntity.class,UUID.fromString(token.substring(0,36)),LockModeType.PESSIMISTIC_WRITE); }
        catch(IllegalArgumentException e) { return null; }
    }
    private boolean matches(StaffSessionJpaEntity session,String token) {
        return MessageDigest.isEqual(session.getRefreshHash().getBytes(StandardCharsets.UTF_8),digest(token).getBytes(StandardCharsets.UTF_8));
    }
}
