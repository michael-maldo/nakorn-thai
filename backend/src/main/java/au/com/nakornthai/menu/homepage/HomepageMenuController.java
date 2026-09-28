package au.com.nakornthai.menu.homepage;

import au.com.nakornthai.menu.listmenu.MenuResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class HomepageMenuController {
    private final HomepageMenuHandler handler;

    @GetMapping("/api/menu/homepage")
    public ResponseEntity<List<MenuResponse>> publicCollections() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.publicCollections());
    }

    @GetMapping("/api/staff/menu/homepage")
    public ResponseEntity<HomepageMenuHandler.Settings> read() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.read());
    }

    @PutMapping("/api/staff/menu/homepage")
    public HomepageMenuHandler.Settings save(@Valid @RequestBody HomepageMenuRequest request) {
        return handler.save(request);
    }
}
