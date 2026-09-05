package au.com.nakornthai.identity.currentuser;
import au.com.nakornthai.identity.infrastructure.*;
import au.com.nakornthai.identity.login.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class CurrentUserController {
    private final SpringDataUserRepository users;
    @GetMapping("/api/identity/me")
    ResponseEntity<LoginResponse.User> me(Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(JpaUserRepository.view(users.findByUsername(auth.getName()).orElseThrow()));
    }
}
