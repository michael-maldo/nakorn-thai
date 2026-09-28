package au.com.nakornthai.menu.homepage;

import au.com.nakornthai.menu.domain.MenuItemRepository;
import au.com.nakornthai.menu.infrastructure.*;
import au.com.nakornthai.menu.listmenu.MenuResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class HomepageMenuHandler {
    private final EntityManager em;
    private final MenuItemRepository menu;

    public record Choice(UUID id, String name, String status) {}
    public record Settings(Long version, List<UUID> collectionIds, List<Choice> collections) {}

    public Settings read() {
        MenuCatalogLock.read(em);
        return view(settings(LockModeType.PESSIMISTIC_READ));
    }

    public Settings save(HomepageMenuRequest request) {
        MenuCatalogLock.read(em);
        var settings = settings(LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(request.version(), settings.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Home page settings changed. Reload before saving.");
        if (new HashSet<>(request.collectionIds()).size() != request.collectionIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose each collection only once.");
        for (var id : request.collectionIds()) {
            if (em.find(MenuCollectionJpaEntity.class, id) == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected collection no longer exists.");
        }
        settings.getCollectionIds().clear();
        settings.getCollectionIds().addAll(request.collectionIds());
        em.flush();
        return view(settings);
    }

    public List<MenuResponse> publicCollections() {
        var ids = settings(LockModeType.PESSIMISTIC_READ).getCollectionIds();
        var result = new ArrayList<MenuResponse>();
        for (var id : ids) {
            var collection = em.find(MenuCollectionJpaEntity.class, id);
            if (collection != null && "PUBLISHED".equals(collection.getStatus())) {
                menu.findVisibleCollection(collection.getSlug()).map(MenuResponse::from).ifPresent(result::add);
            }
        }
        return List.copyOf(result);
    }

    private MenuHomepageSettingsJpaEntity settings(LockModeType lock) {
        var settings = em.find(MenuHomepageSettingsJpaEntity.class, (short) 1, lock);
        if (settings == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Home page settings are unavailable.");
        return settings;
    }

    private Settings view(MenuHomepageSettingsJpaEntity settings) {
        var choices = em.createQuery("from MenuCollectionJpaEntity order by displayOrder, id", MenuCollectionJpaEntity.class)
                .getResultList().stream().map(c -> new Choice(c.getId(), c.getName(), c.getStatus())).toList();
        return new Settings(settings.getVersion(), List.copyOf(settings.getCollectionIds()), choices);
    }
}
