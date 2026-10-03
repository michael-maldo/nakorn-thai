package au.com.nakornthai.notification.contactverification;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/reservations/contact-verifications")
public class ContactVerificationController {
 private final ContactVerificationHandler handler;
 public record Start(@NotNull @Pattern(regexp="SMS|EMAIL") String channel,@NotBlank @Size(max=254) String destination) {}
 public record Check(@NotNull @Pattern(regexp="[0-9]{4,10}") String code) {}
 @GetMapping("/options") ResponseEntity<?> options(){return response(handler.options());}
 @PostMapping ResponseEntity<?> start(@Valid @RequestBody Start request){return response(handler.start(request.channel(),request.destination()));}
 @PostMapping("/{id}/verify") ResponseEntity<?> verify(@PathVariable UUID id,@Valid @RequestBody Check request){return response(handler.verify(id,request.code()));}
 private ResponseEntity<?> response(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
