package au.com.nakornthai.identity.logout;
import au.com.nakornthai.identity.infrastructure.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class LogoutController {
    private final JpaUserRepository identity;
    private final IdentityCookies cookies;
    @PostMapping("/api/identity/logout")
    ResponseEntity<Void> logout(@CookieValue(name=IdentityCookies.NAME,required=false) String token) {
        identity.logout(token);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookies.clear()).build();
    }
}
