package au.com.nakornthai.reservation.createreservation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.util.Map;

@RestController @RequestMapping("/api/functions") @RequiredArgsConstructor
public class CreateFunctionEnquiryController {
    private final CreateFunctionEnquiryHandler handler;
    @GetMapping("/csrf")
    ResponseEntity<CsrfToken> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(token);
    }
    @PostMapping
    ResponseEntity<Map<String,Object>> create(@Valid @RequestBody CreateFunctionEnquiryRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.handle(request));
    }
}
