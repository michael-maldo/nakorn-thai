package au.com.nakornthai.ordering.trackorder;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.UUID;
@RestController
public class TrackOrderController {
 private final TrackOrderHandler handler;private final OrderViewTokens tokens;
 public TrackOrderController(TrackOrderHandler handler,OrderViewTokens tokens){this.handler=handler;this.tokens=tokens;}
 @GetMapping("/api/order-tracking") public ResponseEntity<TrackOrderHandler.View> track(@RequestHeader(value="X-Order-View-Token",required=false) String token){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(handler.handle(token));
 }
 @PostMapping("/api/staff/orders/{id}/tracking/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void revoke(@PathVariable UUID id){tokens.revoke(id);}
}
