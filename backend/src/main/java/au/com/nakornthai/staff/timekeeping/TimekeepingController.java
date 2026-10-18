package au.com.nakornthai.staff.timekeeping;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static au.com.nakornthai.staff.timekeeping.TimekeepingDtos.*;

@RestController @RequestMapping("/api/staff/timekeeping") @RequiredArgsConstructor
public class TimekeepingController {
    private final TimekeepingHandler handler;
    @GetMapping("/csrf")
    public ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return response(HttpStatus.OK, Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }
    @PostMapping("/clock-in")
    public ResponseEntity<Entry> clockIn(@Valid @RequestBody StaffRequest request, Authentication actor) {
        return response(HttpStatus.CREATED, handler.clockIn(request.staffUserId(), actor.getName()));
    }
    @PostMapping("/clock-out")
    public ResponseEntity<Entry> clockOut(@Valid @RequestBody StaffRequest request, Authentication actor) {
        return response(HttpStatus.OK, handler.clockOut(request.staffUserId(), actor.getName()));
    }
    @PostMapping("/entries")
    public ResponseEntity<Entry> manual(@Valid @RequestBody ManualRequest request, Authentication actor) {
        return response(HttpStatus.CREATED, handler.manual(request, actor.getName()));
    }
    @PatchMapping("/entries/{id}")
    public ResponseEntity<Entry> correct(@PathVariable UUID id, @Valid @RequestBody CorrectionRequest request, Authentication actor) {
        return response(HttpStatus.OK, handler.correct(id, request, actor.getName()));
    }
    @GetMapping("/me")
    public ResponseEntity<Timesheet> mine(Authentication actor, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size, @RequestParam Map<String,String> parameters) {
        if (!Set.of("from", "to", "page", "size").containsAll(parameters.keySet()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Self-service accepts only from, to, page and size");
        return response(HttpStatus.OK, handler.mine(actor.getName(), from, to, page, size));
    }
    @GetMapping("/entries")
    public ResponseEntity<Timesheet> all(Authentication actor, @RequestParam(required = false) UUID staffUserId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return response(HttpStatus.OK, handler.all(actor.getName(), staffUserId, from, to, page, size));
    }
    @GetMapping("/entries/{id}/audit")
    public ResponseEntity<AuditPage> audit(@PathVariable UUID id, Authentication actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return response(HttpStatus.OK, handler.history(id, actor.getName(), page, size));
    }
    private <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
