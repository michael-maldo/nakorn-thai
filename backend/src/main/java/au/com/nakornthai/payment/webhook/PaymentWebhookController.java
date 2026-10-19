package au.com.nakornthai.payment.webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class PaymentWebhookController {
 private final PayPalWebhookInbox inbox;
 @PostMapping(value="/api/payments/paypal/webhook",consumes=MediaType.APPLICATION_JSON_VALUE)
 ResponseEntity<Void> receive(@RequestHeader HttpHeaders headers,@RequestBody String body){inbox.receive(headers,body);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
}
