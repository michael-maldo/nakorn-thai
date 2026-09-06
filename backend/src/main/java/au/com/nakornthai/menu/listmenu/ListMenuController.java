package au.com.nakornthai.menu.listmenu;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/menu/collections")
public class ListMenuController {
    private final ListMenuHandler handler;

    @GetMapping
    public ResponseEntity<java.util.List<au.com.nakornthai.menu.domain.MenuItem.CollectionSummary>> discover() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(handler.discover());
    }

    @GetMapping("/{slug}/items")
    public ResponseEntity<MenuResponse> list(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(handler.handle(new ListMenuQuery(slug)));
    }
}
