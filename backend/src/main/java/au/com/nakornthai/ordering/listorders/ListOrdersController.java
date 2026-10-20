package au.com.nakornthai.ordering.listorders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.*;
@RestController @RequiredArgsConstructor
public class ListOrdersController {
    private final ListOrdersHandler handler;
    @GetMapping("/api/staff/foh/orders")
    ResponseEntity<List<StaffOrderResponse>> front(@RequestParam(defaultValue="false") boolean history) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.handle(false,history));
    }
    @GetMapping("/api/staff/kitchen/orders")
    ResponseEntity<List<StaffOrderResponse>> kitchen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.handle(true,false));
    }
    @GetMapping("/api/staff/orders/{id}")
    ResponseEntity<StaffOrderResponse> detail(@PathVariable UUID id,org.springframework.security.core.Authentication authentication){
        boolean kitchen=authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_BOH"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.detail(id,kitchen));
    }
    @GetMapping("/api/staff/orders/csrf")
    ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("headerName",token.getHeaderName(),"token",token.getToken()));
    }
}
