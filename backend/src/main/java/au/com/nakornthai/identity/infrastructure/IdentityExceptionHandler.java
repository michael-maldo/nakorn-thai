package au.com.nakornthai.identity.infrastructure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestControllerAdvice(basePackages="au.com.nakornthai.identity")
public class IdentityExceptionHandler {
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid() {return ResponseEntity.badRequest().body(Map.of("message","Check the required account fields"));}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> conflict() {return ResponseEntity.status(409).body(Map.of("message","Username already exists or account data conflicts"));}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> failure(ResponseStatusException e) {return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Account request failed":e.getReason()));}
}
