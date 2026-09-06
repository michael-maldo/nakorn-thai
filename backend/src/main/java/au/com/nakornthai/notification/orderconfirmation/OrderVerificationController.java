package au.com.nakornthai.notification.orderconfirmation;
import au.com.nakornthai.notification.infrastructure.TwilioVerifyClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/order-verification")
public class OrderVerificationController {
 private final OrderVerificationHandler handler;private final TwilioVerifyClient provider;
 public record Start(@NotNull UUID orderId,@NotNull @Pattern(regexp="sms|email") String channel){}
 public record Check(@NotNull UUID challengeId,@NotNull @Pattern(regexp="[0-9]{4,10}") String code){}
 @GetMapping("/options") ResponseEntity<?> options(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("sms",provider.enabled("sms"),"email",provider.enabled("email")));}
 @PostMapping("/start") ResponseEntity<?> start(@Valid @RequestBody Start request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("challengeId",handler.start(request.orderId(),request.channel()),"message","If that order has a contact for this channel, a verification code has been sent."));}
 @PostMapping("/check") ResponseEntity<?> check(@Valid @RequestBody Check request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.check(request.challengeId(),request.code()));}
}
