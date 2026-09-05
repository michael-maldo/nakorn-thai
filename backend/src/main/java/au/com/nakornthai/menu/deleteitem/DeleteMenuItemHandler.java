package au.com.nakornthai.menu.deleteitem;
import au.com.nakornthai.menu.infrastructure.MenuAdminService;
import au.com.nakornthai.menu.createitem.CreateMenuItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class DeleteMenuItemHandler {
    private final MenuAdminService service;
    public void handle(UUID id, Long version) { service.archive(id, version); }
}
