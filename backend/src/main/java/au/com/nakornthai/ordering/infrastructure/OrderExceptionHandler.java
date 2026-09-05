package au.com.nakornthai.ordering.infrastructure;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import java.util.Map;
@RestControllerAdvice(basePackages="au.com.nakornthai.ordering")
public class OrderExceptionHandler {
    // Avoid default validation logging of customer details or tracking tokens.
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("message","Check contact details, item quantities and required fields"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> failure(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("message",e.getReason()==null ? "Order request could not be completed" : e.getReason()));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> conflict() { return ResponseEntity.status(409).body(Map.of("message","Order changed; please retry")); }
}
