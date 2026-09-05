package au.com.nakornthai.reservation.infrastructure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestControllerAdvice(basePackages="au.com.nakornthai.reservation")
public class ReservationExceptionHandler {
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Check your booking date, contact details and party size (1–20)"));}
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<Map<String,String>> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Booking request failed":e.getReason()));}
}
