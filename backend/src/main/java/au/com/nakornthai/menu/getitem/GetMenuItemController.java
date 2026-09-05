package au.com.nakornthai.menu.getitem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/staff/menu")
@RequiredArgsConstructor
public class GetMenuItemController {
    private final GetMenuItemHandler handler;
    @GetMapping("/items")
    ResponseEntity<MenuItemResponse.Dashboard> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.handle());
    }
    @GetMapping("/csrf")
    ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
