package au.com.nakornthai.restaurant.orderingsettings;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/staff/restaurant/ordering") @RequiredArgsConstructor
public class OrderingSettingsController {
    private final OrderingSettingsHandler handler;
    @GetMapping
    public ResponseEntity<OrderingSettingsHandler.Settings> read() { return response(handler.read()); }
    @PutMapping
    public ResponseEntity<OrderingSettingsHandler.Settings> save(@Valid @RequestBody OrderingSettingsHandler.Update request) {
        return response(handler.save(request));
    }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
