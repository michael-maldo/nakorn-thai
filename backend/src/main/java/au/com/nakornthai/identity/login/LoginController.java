package au.com.nakornthai.identity.login;
import au.com.nakornthai.identity.infrastructure.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;
@RestController @RequestMapping("/api/identity") @RequiredArgsConstructor
public class LoginController {
    private final LoginHandler handler;
    private final IdentityCookies cookies;
    @GetMapping("/csrf")
    ResponseEntity<Map<String,String>> csrf(CsrfToken token) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("headerName",token.getHeaderName(),"token",token.getToken()));}
    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        var tokens=handler.handle(request);
        if(tokens==null)return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).body(Map.of("message","Invalid username or password"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookies.issue(tokens.refreshToken(),tokens.refreshExpiry())).body(tokens.response());
    }
}
