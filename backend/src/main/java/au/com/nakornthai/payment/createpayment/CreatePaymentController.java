package au.com.nakornthai.payment.createpayment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@RestController @RequiredArgsConstructor
public class CreatePaymentController {
 private final CreatePaymentHandler handler;
 public record Start(@NotNull @Pattern(regexp="PAYPAL|PAYID|PAY_AT_RESTAURANT") String method){}
 public record Confirm(@Min(0) long version,@NotBlank @Size(max=150) String bankReference){}
 @GetMapping("/api/payments/options") ResponseEntity<?> options(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.options());}
 @PostMapping("/api/payments/{id}") ResponseEntity<?> start(@PathVariable UUID id,@RequestHeader("X-Order-Token") String token,@Valid @RequestBody Start request){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.start(id,token,request.method()));}
 @PostMapping("/api/payments/{id}/check") ResponseEntity<?> check(@PathVariable UUID id,@RequestHeader("X-Order-Token") String token){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.check(id,token,true,false));}
 @PostMapping("/api/staff/payments/{id}/check") ResponseEntity<?> staffCheck(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.check(id,null,false,true));}
 @PostMapping("/api/staff/payments/{id}/payid-confirm") ResponseEntity<?> confirm(@PathVariable UUID id,@Valid @RequestBody Confirm request,Authentication actor){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.confirmPayid(id,request.version(),request.bankReference(),actor.getName()));}
}
