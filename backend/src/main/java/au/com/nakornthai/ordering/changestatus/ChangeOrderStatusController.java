package au.com.nakornthai.ordering.changestatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;
@RestController @RequiredArgsConstructor
public class ChangeOrderStatusController {
    private final ChangeOrderStatusHandler handler;
    @PatchMapping("/api/staff/orders/{id}/status") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void change(@PathVariable UUID id, @Valid @RequestBody ChangeOrderStatusCommand command, Authentication actor) {
        handler.handle(id,command,actor);
    }
}
