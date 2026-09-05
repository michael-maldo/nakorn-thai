package au.com.nakornthai.menu.updateitem;
import au.com.nakornthai.menu.infrastructure.MenuImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.core.io.FileSystemResource;
import java.util.UUID;
import java.io.IOException;
@RestController
@RequiredArgsConstructor
public class MenuImageController {
    private final MenuImageService images;
    @PostMapping(value = "/api/staff/menu/items/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void save(@PathVariable UUID id, @RequestParam long version,
            @RequestParam(required = false) MultipartFile file, @RequestParam String alt,
            @RequestParam int focusX, @RequestParam int focusY, @RequestParam double zoom) throws IOException {
        images.save(id, version, file, alt, focusX, focusY, zoom);
    }
    @GetMapping("/media/menu/{name}")
    ResponseEntity<FileSystemResource> read(@PathVariable String name) {
        var resource = new FileSystemResource(images.file(name));
        if (!resource.isReadable()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noCache()).body(resource);
    }
}
