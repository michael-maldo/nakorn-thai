package au.com.nakornthai.ordering.getorder;
import au.com.nakornthai.ordering.createorder.CreateOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequiredArgsConstructor
public class GetOrderController {
    private final GetOrderHandler handler;
    @GetMapping("/api/orders/{id}")
    ResponseEntity<CreateOrderResponse> get(@PathVariable UUID id, @RequestHeader(value="X-Order-Token",required=false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.handle(id, token));
    }
}
