package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.infrastructure.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class MenuEntityIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired SpringDataMenuItemRepository items;
    @Autowired SpringDataMenuCollectionItemRepository memberships;
    @Autowired ListMenuHandler handler;

    private MenuCategoryJpaEntity category() {
        var category = new MenuCategoryJpaEntity();
        category.setName("Test category");
        category.setSlug("test-" + UUID.randomUUID());
        em.persist(category);
        return category;
    }

    private MenuItemJpaEntity item(MenuCategoryJpaEntity category) {
        var item = new MenuItemJpaEntity();
        item.setCategory(category);
        item.setName("Test Fried Rice");
        item.setSlug("test-" + UUID.randomUUID());
        item.setDescription("Test description");
        item.setStatus("PUBLISHED");
        return items.saveAndFlush(item);
    }

    private MenuCollectionJpaEntity collection() {
        var collection = new MenuCollectionJpaEntity();
        collection.setName("Test collection");
        collection.setSlug("test-" + UUID.randomUUID());
        collection.setStatus("PUBLISHED");
        em.persist(collection);
        return collection;
    }

    private void membership(MenuCollectionJpaEntity collection, MenuItemJpaEntity item, int order) {
        var membership = new MenuCollectionItemJpaEntity();
        membership.setCollection(collection);
        membership.setMenuItem(item);
        membership.setDisplayOrder(order);
        memberships.saveAndFlush(membership);
    }

    @Test
    void savesAndUpdatesWithDatabaseTimestampsAndVersion() {
        var item = item(category());
        assertNotNull(item.getId());
        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());
        assertEquals(0L, item.getVersion());
        var created = item.getCreatedAt();
        item.setName("Updated name");
        items.saveAndFlush(item);
        assertEquals(1L, item.getVersion());
        assertEquals(created, item.getCreatedAt());
        assertFalse(item.getUpdatedAt().isBefore(created));
        em.clear();
        assertEquals("Updated name", items.findById(item.getId()).orElseThrow().getName());
    }

    @Test
    void staleDetachedSaveIsRejected() {
        var item = item(category());
        em.detach(item);
        jdbc.update("UPDATE menu_item SET name='Concurrent edit', version=version+1 WHERE id=?", item.getId());
        item.setName("Stale edit");
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> items.saveAndFlush(item));
    }

    @Test
    void savesAllRelationshipTypesAndMapsIndependentProfiles() {
        var item = item(category());
        var collection = collection();
        membership(collection, item, 1);
        var secondCollection = collection();
        membership(secondCollection, item, 2);

        var variation = new MenuItemVariationJpaEntity();
        variation.setMenuItem(item);
        variation.setName("Pork & Seafood");
        variation.setPriceMinor(2500L);
        variation.setDefaultVariation(true);
        em.persist(variation);

        var image = new MenuItemImageJpaEntity();
        image.setMenuItem(item);
        image.setStorageKey("menu/test-rice.jpg");
        image.setAltText("Test rice");
        image.setPrimary(true);
        em.persist(image);

        var tag = new DietaryTagJpaEntity();
        tag.setCode("TEST_" + UUID.randomUUID().toString().replace('-', '_').toUpperCase());
        tag.setName("Test badge");
        tag.setDescription("Synthetic test badge, not a food claim");
        em.persist(tag);
        var allergen = new AllergenJpaEntity();
        allergen.setCode("TEST_" + UUID.randomUUID().toString().replace('-', '_').toUpperCase());
        allergen.setName("Test allergen");
        em.persist(allergen);

        var itemTag = new MenuItemDietaryTagJpaEntity();
        itemTag.setMenuItem(item);
        itemTag.setDietaryTag(tag);
        itemTag.setVerifiedAt(Instant.now());
        em.persist(itemTag);
        var itemAllergen = new MenuItemAllergenJpaEntity();
        itemAllergen.setMenuItem(item);
        itemAllergen.setAllergen(allergen);
        itemAllergen.setDeclaration("CONTAINS");
        em.persist(itemAllergen);
        var variationTag = new MenuItemVariationDietaryTagJpaEntity();
        variationTag.setVariation(variation);
        variationTag.setDietaryTag(tag);
        // Unverified at variation level: must not inherit the verified item claim.
        em.persist(variationTag);
        var variationAllergen = new MenuItemVariationAllergenJpaEntity();
        variationAllergen.setVariation(variation);
        variationAllergen.setAllergen(allergen);
        variationAllergen.setDeclaration("MAY_CONTAIN");
        em.persist(variationAllergen);
        em.flush();
        em.clear();

        var result = handler.handle(new ListMenuQuery(collection.getSlug())).items().getFirst();
        assertEquals("/media/menu/test-rice.jpg", result.image().url());
        assertNull(result.profile());
        assertEquals(2500, result.variations().getFirst().priceMinor());
        assertTrue(result.variations().getFirst().profile().dietaryTags().isEmpty());
        assertEquals("MAY_CONTAIN", result.variations().getFirst().profile().allergens().getFirst().declaration());
        assertEquals(1, items.findById(item.getId()).orElseThrow().getDietaryTags().size());
        assertEquals("CONTAINS", items.findById(item.getId()).orElseThrow().getAllergens().getFirst().getDeclaration());
        memberships.deleteById(new MenuAssociationId(secondCollection.getId(), item.getId()));
        memberships.flush();
        em.clear();
        assertEquals(1, handler.handle(new ListMenuQuery(collection.getSlug())).items().size());
        assertTrue(items.existsById(item.getId()));
    }

    @Test
    void batchLoadsCollectionsWithoutOneQueryPerDish() {
        var category = category();
        var collection = collection();
        for (int index = 0; index < 32; index++) membership(collection, item(category), index);
        em.flush();
        em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = handler.handle(new ListMenuQuery(collection.getSlug()));
        assertEquals(32, result.items().size());
        assertTrue(statistics.getPrepareStatementCount() <= 10,
                "Expected batched reads, got " + statistics.getPrepareStatementCount());
    }
}
