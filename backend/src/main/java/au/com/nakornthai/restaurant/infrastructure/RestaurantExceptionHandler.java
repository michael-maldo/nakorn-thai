package au.com.nakornthai.restaurant.infrastructure;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackages = "au.com.nakornthai.restaurant")
public class RestaurantExceptionHandler {
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<Map<String,String>> invalid() { return ResponseEntity.badRequest().body(Map.of("message", "Check the timezone, dates and opening-hour fields")); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> failure(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("message", e.getReason() == null ? "Scheduling request failed" : e.getReason()));
    }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String,String>> conflict() {
        return ResponseEntity.status(409).body(Map.of("message", "Schedule entry changed or the date is already closed; reload before saving"));
    }
}
