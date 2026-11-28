package au.com.nakornthai.restaurant.holidays;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/restaurant/holidays")
@RequiredArgsConstructor
public class HolidayDefinitionController {
    private final HolidayDefinitionService holidays;

    public record HolidayList(List<HolidayDefinitionResponse> holidays) {}

    @GetMapping
    public ResponseEntity<HolidayList> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new HolidayList(holidays.list()));
    }

    @PostMapping
    public ResponseEntity<HolidayDefinitionResponse> create(@Valid @RequestBody HolidayDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(holidays.save(null, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HolidayDefinitionResponse> update(@PathVariable UUID id,
                                                           @Valid @RequestBody HolidayDefinitionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(holidays.save(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @RequestParam Long version) {
        holidays.remove(id, version);
    }
}
