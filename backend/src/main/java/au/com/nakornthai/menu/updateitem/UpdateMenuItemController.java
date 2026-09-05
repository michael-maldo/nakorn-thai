package au.com.nakornthai.menu.updateitem;
import au.com.nakornthai.menu.createitem.CreateMenuItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/staff/menu/items")
@RequiredArgsConstructor
public class UpdateMenuItemController {
    private final UpdateMenuItemHandler handler;
    @PutMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void update(@PathVariable UUID id, @Valid @RequestBody CreateMenuItemRequest request) { handler.handle(id, request); }
}
