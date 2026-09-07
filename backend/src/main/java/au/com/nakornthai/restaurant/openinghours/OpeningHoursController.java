package au.com.nakornthai.restaurant.openinghours;

import au.com.nakornthai.restaurant.infrastructure.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/staff/restaurant") @RequiredArgsConstructor
public class OpeningHoursController {
    private final OpeningHoursHandler handler;
    @GetMapping("/csrf") public ResponseEntity<CsrfToken> csrf(CsrfToken token) { return response(token); }
    @GetMapping("/schedule") public ResponseEntity<OpeningHoursHandler.ScheduleView> schedule() { return response(handler.read()); }
    @PutMapping("/settings") public ResponseEntity<RestaurantSettingsJpaEntity> settings(@Valid @RequestBody OpeningHoursRequest.Settings request) {
        return response(handler.saveSettings(request));
    }
    @PostMapping("/hours") public ResponseEntity<OpeningHoursJpaEntity> createWindow(@Valid @RequestBody OpeningHoursRequest.Window request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveWindow(null, request));
    }
    @PutMapping("/hours/{id}") public ResponseEntity<OpeningHoursJpaEntity> updateWindow(@PathVariable UUID id, @Valid @RequestBody OpeningHoursRequest.Window request) {
        return response(handler.saveWindow(id, request));
    }
    @DeleteMapping("/hours/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWindow(@PathVariable UUID id, @RequestParam Long version) { handler.deleteWindow(id, version); }
    @PostMapping("/closed-dates") public ResponseEntity<ClosedDateJpaEntity> createClosure(@Valid @RequestBody OpeningHoursRequest.Closure request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(handler.saveClosure(null, request));
    }
    @PutMapping("/closed-dates/{id}") public ResponseEntity<ClosedDateJpaEntity> updateClosure(@PathVariable UUID id, @Valid @RequestBody OpeningHoursRequest.Closure request) {
        return response(handler.saveClosure(id, request));
    }
    @DeleteMapping("/closed-dates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClosure(@PathVariable UUID id, @RequestParam Long version) { handler.deleteClosure(id, version); }
    private <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
