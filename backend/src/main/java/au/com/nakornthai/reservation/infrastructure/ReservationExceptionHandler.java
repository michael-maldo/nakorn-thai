package au.com.nakornthai.reservation.infrastructure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
@RestControllerAdvice(basePackages="au.com.nakornthai.reservation")
public class ReservationExceptionHandler {
 @ExceptionHandler(au.com.nakornthai.restaurant.domain.RestaurantClosedException.class)
 public ResponseEntity<Map<String,String>> restaurantClosed() {
  return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(Map.of("code","RESTAURANT_CLOSED","message","The restaurant is closed at the requested time. Please choose an opening time."));
 }
 @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
 ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Check the required fields, contact details, date and guest count"));}
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<Map<String,String>> failure(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Booking request failed":e.getReason()));}
}
