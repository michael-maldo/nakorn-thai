package au.com.nakornthai.menu.createitem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/staff/menu/items")
@RequiredArgsConstructor
public class CreateMenuItemController {
    private final CreateMenuItemHandler handler;
    @PostMapping
    ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(201).body(Map.of("id", handler.handle(request)));
    }
}
