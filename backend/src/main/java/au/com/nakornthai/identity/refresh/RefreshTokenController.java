package au.com.nakornthai.identity.refresh;
import au.com.nakornthai.identity.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class RefreshTokenController {
    private final JpaUserRepository identity;
    private final IdentityCookies cookies;
    @PostMapping("/api/identity/refresh")
    ResponseEntity<?> refresh(@CookieValue(name=IdentityCookies.NAME,required=false) String token) {
        var tokens=identity.refresh(token);
        if(tokens==null)return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookies.clear()).build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookies.issue(tokens.refreshToken(),tokens.refreshExpiry())).body(tokens.response());
    }
}
