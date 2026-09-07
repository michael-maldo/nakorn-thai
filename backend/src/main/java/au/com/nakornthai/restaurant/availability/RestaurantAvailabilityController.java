package au.com.nakornthai.restaurant.availability;

import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController @RequiredArgsConstructor
public class RestaurantAvailabilityController {
    private final RestaurantAvailabilityService availability;
    private final Clock clock;
    public record AvailabilityResponse(String timezone, Instant evaluatedAt, boolean open) {}
    @GetMapping("/api/restaurant/availability")
    public ResponseEntity<AvailabilityResponse> availability() {
        var schedule = availability.schedule();
        var instant = clock.instant();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AvailabilityResponse(schedule.timezone().getId(), instant, schedule.isOpen(instant)));
    }
}
