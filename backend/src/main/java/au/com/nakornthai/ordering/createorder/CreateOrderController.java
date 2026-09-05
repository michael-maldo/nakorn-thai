package au.com.nakornthai.ordering.createorder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/orders") @RequiredArgsConstructor
public class CreateOrderController {
    private final CreateOrderHandler handler;
    @GetMapping("/options")
    ResponseEntity<Map<String,Object>> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("enabled", handler.enabled(), "fulfilment", "PICKUP", "payment", "PAY_AT_RESTAURANT"));
    }
    @GetMapping("/csrf")
    ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("headerName",token.getHeaderName(),"token",token.getToken()));
    }
    @PostMapping
    ResponseEntity<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.handle(request));
    }
}
