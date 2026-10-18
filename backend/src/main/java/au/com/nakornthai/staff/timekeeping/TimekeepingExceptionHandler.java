package au.com.nakornthai.staff.timekeeping;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(basePackages = "au.com.nakornthai.staff.timekeeping")
public class TimekeepingExceptionHandler {
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> validation(org.springframework.web.bind.MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream().map(error -> error.getField()).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        return response(HttpStatus.BAD_REQUEST, "Check required fields and allowed values: " + fields);
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<Map<String,String>> invalid() {
        return response(HttpStatus.BAD_REQUEST, "Check identifiers, pagination and offset-aware timestamps");
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> failure(ResponseStatusException e) {
        return response(e.getStatusCode(), e.getReason() == null ? "Timekeeping request failed" : e.getReason());
    }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.ConcurrencyFailureException.class, jakarta.persistence.OptimisticLockException.class,
            jakarta.persistence.PessimisticLockException.class, jakarta.persistence.LockTimeoutException.class,
            org.hibernate.exception.ConstraintViolationException.class})
    ResponseEntity<Map<String,String>> conflict() {
        return response(HttpStatus.CONFLICT, "Attendance conflicts with existing data or changed concurrently; reload before saving");
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, jakarta.persistence.PersistenceException.class})
    ResponseEntity<Map<String,String>> unavailable() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "Timekeeping is temporarily unavailable; reload to check the result before retrying");
    }
    private ResponseEntity<Map<String,String>> response(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message", message));
    }
}
