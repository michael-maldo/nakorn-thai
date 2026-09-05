package au.com.nakornthai.menu.infrastructure;

import au.com.nakornthai.menu.createitem.CreateMenuItemRequest;
import au.com.nakornthai.menu.getitem.MenuItemResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuAdminService {
    private final SpringDataMenuItemRepository items;
    private final SpringDataMenuCategoryRepository categories;
    private final SpringDataMenuCollectionRepository collections;
    private final SpringDataMenuCollectionItemRepository memberships;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public MenuItemResponse.Dashboard list() {
        var links = memberships.findAll();
        var dishes = items.findAll().stream()
                .sorted(Comparator.comparingInt(MenuItemJpaEntity::getDisplayOrder)
                        .thenComparing(MenuItemJpaEntity::getName).thenComparing(MenuItemJpaEntity::getId))
                .map(item -> response(item, links)).toList();
        return new MenuItemResponse.Dashboard(dishes,
                categories.findAll().stream().sorted(Comparator.comparing(MenuCategoryJpaEntity::getName))
                        .map(c -> new MenuItemResponse.Option(c.getId(), c.getName())).toList(),
                collections.findAll().stream().sorted(Comparator.comparing(MenuCollectionJpaEntity::getName))
                        .map(c -> new MenuItemResponse.Option(c.getId(), c.getName())).toList());
    }

    @Transactional
    public UUID create(CreateMenuItemRequest request) {
        if (request.version() != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New dishes must not have a version");
        var item = new MenuItemJpaEntity();
        apply(item, request);
        items.saveAndFlush(item);
        syncCollections(item, request);
        return item.getId();
    }

    @Transactional
    public void update(UUID id, CreateMenuItemRequest request) {
        var item = locked(id, request.version());
        if (!Objects.equals(item.getDescription(), request.description()) || !Objects.equals(item.getName(), request.name())) {
            // A recipe-description change requires a new review; retain allergen warnings.
            item.setAllergenReviewStatus("NEEDS_REVIEW");
            item.setAllergenReviewedAt(null);
            item.getDietaryTags().forEach(tag -> tag.setVerifiedAt(null));
            item.getVariations().forEach(variation -> {
                variation.setAllergenReviewStatus("NEEDS_REVIEW");
                variation.setAllergenReviewedAt(null);
                variation.getDietaryTags().forEach(tag -> tag.setVerifiedAt(null));
            });
        }
        apply(item, request);
        syncCollections(item, request);
    }

    @Transactional
    public void archive(UUID id, Long version) {
        var item = locked(id, version);
        item.setStatus("ARCHIVED");
    }

    private MenuItemJpaEntity locked(UUID id, Long version) {
        if (version == null || version < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version is required");
        var item = entityManager.find(MenuItemJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dish not found");
        if (!version.equals(item.getVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Dish changed; reload before saving");
        entityManager.lock(item, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        return item;
    }

    private void apply(MenuItemJpaEntity item, CreateMenuItemRequest request) {
        var category = categories.findById(request.categoryId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"));
        item.setName(request.name());
        item.setSlug(request.slug());
        item.setDescription(request.description());
        item.setCategory(category);
        item.setStatus(request.status());
        item.setAvailable(request.available());
        item.setDisplayOrder(request.displayOrder());
    }

    private void syncCollections(MenuItemJpaEntity item, CreateMenuItemRequest request) {
        var selected = collections.findAllById(request.collectionIds());
        if (selected.size() != request.collectionIds().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown collection");
        var existing = memberships.findAll().stream().filter(m -> m.getMenuItem().getId().equals(item.getId())).toList();
        existing.stream().filter(m -> !request.collectionIds().contains(m.getCollection().getId())).forEach(memberships::delete);
        for (var collection : selected) {
            var membership = existing.stream().filter(m -> m.getCollection().getId().equals(collection.getId())).findFirst().orElseGet(() -> {
                var link = new MenuCollectionItemJpaEntity();
                link.setCollection(collection);
                link.setMenuItem(item);
                return link;
            });
            membership.setDisplayOrder(request.displayOrder());
            memberships.save(membership);
        }
    }

    private MenuItemResponse response(MenuItemJpaEntity item, List<MenuCollectionItemJpaEntity> links) {
        var ids = links.stream().filter(m -> m.getMenuItem().getId().equals(item.getId()))
                .map(m -> m.getCollection().getId()).collect(Collectors.toSet());
        return new MenuItemResponse(item.getId(), item.getName(), item.getSlug(), item.getDescription(),
                item.getCategory().getId(), item.getStatus(), item.isAvailable(), item.getDisplayOrder(), ids, item.getVersion());
    }
}
