package au.com.nakornthai.payment.infrastructure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestControllerAdvice(basePackages={"au.com.nakornthai.payment","au.com.nakornthai.notification"})
public class PaymentExceptionHandler {
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.bind.MissingRequestHeaderException.class})
 ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Check required payment or verification fields"));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore()).body(Map.of("message",e.getReason()==null?"Request could not be completed":e.getReason()));}
}
