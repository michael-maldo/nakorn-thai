package au.com.nakornthai.menu.updateitem;
import au.com.nakornthai.menu.infrastructure.MenuAdminService;
import au.com.nakornthai.menu.createitem.CreateMenuItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class UpdateMenuItemHandler {
    private final MenuAdminService service;
    public void handle(UUID id, CreateMenuItemRequest request) { service.update(id, request); }
}
