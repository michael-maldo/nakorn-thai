package au.com.nakornthai.menu.getitem;
import au.com.nakornthai.menu.infrastructure.MenuAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service
@RequiredArgsConstructor
public class GetMenuItemHandler {
    private final MenuAdminService service;
    public MenuItemResponse.Dashboard handle() { return service.list(); }
}
