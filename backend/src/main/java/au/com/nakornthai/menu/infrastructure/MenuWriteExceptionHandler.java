package au.com.nakornthai.menu.infrastructure;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestControllerAdvice(basePackages = "au.com.nakornthai.menu")
public class MenuWriteExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Map<String, String>> failure(org.springframework.web.server.ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("message", exception.getReason() == null ? "Menu request failed" : exception.getReason()));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> conflict(RuntimeException exception) {
        return ResponseEntity.status(409).body(Map.of("message", "Dish conflicts with existing data. Check the slug or reload before saving."));
    }
}
