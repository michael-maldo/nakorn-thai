package au.com.nakornthai.shared.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import au.com.nakornthai.identity.infrastructure.UserJpaEntity;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.*;
public class JwtService {
    private final SecretKey key;
    public JwtService(String encodedKey, boolean production) {
        if (encodedKey.isBlank()) {
            if (production) throw new IllegalArgumentException("JWT_SECRET_BASE64 is required in production");
            key=Jwts.SIG.HS256.key().build();
        } else {
            byte[] bytes=Base64.getDecoder().decode(encodedKey);
            if (bytes.length<32) throw new IllegalArgumentException("JWT_SECRET_BASE64 must encode at least 32 random bytes");
            key=Keys.hmacShaKeyFor(bytes);
        }
    }
    public String issue(UserJpaEntity user, UUID sessionId) {
        Instant now=Instant.now();
        return Jwts.builder().issuer("nakorn-thai").subject(user.getId().toString())
                .claim("sid",sessionId.toString()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900))).id(UUID.randomUUID().toString())
                .signWith(key,Jwts.SIG.HS256).compact();
    }
    public Claims verify(String token) {
        var parsed=Jwts.parser().verifyWith(key).requireIssuer("nakorn-thai").build().parseSignedClaims(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm()) || parsed.getPayload().getExpiration()==null)
            throw new JwtException("Invalid access token");
        return parsed.getPayload();
    }
}
