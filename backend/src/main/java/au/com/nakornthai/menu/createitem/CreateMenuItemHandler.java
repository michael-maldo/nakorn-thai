package au.com.nakornthai.menu.createitem;
import au.com.nakornthai.menu.infrastructure.MenuAdminService;
import au.com.nakornthai.menu.createitem.CreateMenuItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class CreateMenuItemHandler {
    private final MenuAdminService service;
    public UUID handle(CreateMenuItemRequest request) { return service.create(request); }
}
