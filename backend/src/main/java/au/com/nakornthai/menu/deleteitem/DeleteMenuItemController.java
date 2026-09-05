package au.com.nakornthai.menu.deleteitem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController
@RequestMapping("/api/staff/menu/items")
@RequiredArgsConstructor
public class DeleteMenuItemController {
    private final DeleteMenuItemHandler handler;
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void archive(@PathVariable UUID id, @RequestParam Long version) { handler.handle(id, version); }
}
