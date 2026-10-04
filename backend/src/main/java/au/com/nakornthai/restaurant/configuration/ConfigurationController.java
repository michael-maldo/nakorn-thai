package au.com.nakornthai.restaurant.configuration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/staff/restaurant/configuration") @RequiredArgsConstructor
public class ConfigurationController {
 private final ConfigurationHandler handler;
 @GetMapping public ResponseEntity<?> read(){return response(handler.read());}
 @PutMapping("/{category}") public ResponseEntity<?> save(@PathVariable String category,@Valid @RequestBody ConfigurationHandler.Update request,Authentication actor){return response(handler.save(category,request,actor.getName()));}
 public record Test(@NotNull @PositiveOrZero Long version){}
 public record TestEmail(@NotNull @PositiveOrZero Long version,@NotBlank @Email @Size(max=254) String recipient){}
 @PostMapping("/{category}/test") public ResponseEntity<?> test(@PathVariable String category,@Valid @RequestBody Test request,Authentication actor){return response(handler.test(category,request.version(),actor.getName(),null));}
 @PostMapping("/SMTP/test-email") public ResponseEntity<?> email(@Valid @RequestBody TestEmail request,Authentication actor){return response(handler.test("SMTP",request.version(),actor.getName(),request.recipient()));}
 @GetMapping("/audit") public ResponseEntity<?> audit(){return response(handler.audit());}
 private ResponseEntity<?> response(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
