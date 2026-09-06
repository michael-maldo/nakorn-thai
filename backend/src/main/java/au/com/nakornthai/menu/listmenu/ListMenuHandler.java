package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.MenuItemRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class ListMenuHandler {
    private final MenuItemRepository repository;

    private final Validator validator;

    @Transactional(readOnly = true)
    public java.util.List<au.com.nakornthai.menu.domain.MenuItem.CollectionSummary> discover() {
        return repository.findPublishedCollections();
    }

    @Transactional(readOnly = true)
    public MenuResponse handle(ListMenuQuery query) {
        if (query == null || !validator.validate(query).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return repository.findVisibleCollection(query.collectionSlug())
                .map(MenuResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
