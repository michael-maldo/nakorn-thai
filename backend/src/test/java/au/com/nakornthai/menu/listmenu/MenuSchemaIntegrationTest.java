package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs against a disposable PostgreSQL database, never an H2 substitute. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class MenuSchemaIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ListMenuHandler handler;
    @Autowired MockMvc mvc;
    UUID category, item, collection;

    @BeforeEach
    void fixture() {
        category = UUID.randomUUID(); item = UUID.randomUUID(); collection = UUID.randomUUID();
        jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?, 'Test category', ?)", category, "category-" + category);
        jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Test dish',?,'Test preparation','PUBLISHED')",
                item, category, "dish-" + item);
        jdbc.update("INSERT INTO menu_collection(id,name,slug,status) VALUES (?, 'Test collection', ?, 'PUBLISHED')",
                collection, slug());
        jdbc.update("INSERT INTO menu_collection_item(collection_id,menu_item_id) VALUES (?,?)", collection, item);
    }

    String slug() { return "collection-" + collection; }
    MenuItem readItem() { entityManager.clear(); return handler.handle(new ListMenuQuery(slug())).items().getFirst(); }

    @Test
    void undeclaredRoutesAndWritesAreDenied() throws Exception {
        mvc.perform(get("/api/staff/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/private").with(user("test-staff")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/menu/collections/{slug}/items", slug()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/menu/collections/{slug}/items", slug()).with(user("test-staff")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void jjwtRuntimeAdapterWorksAlongsideJackson3Api() throws Exception {
        var key = io.jsonwebtoken.Jwts.SIG.HS256.key().build();
        var token = io.jsonwebtoken.Jwts.builder().subject("compatibility-test")
                .claim("scope", "test").signWith(key).compact();
        var claims = io.jsonwebtoken.Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        assertEquals("compatibility-test", claims.getSubject());
        assertEquals("test", claims.get("scope", String.class));
        var wrongKey = io.jsonwebtoken.Jwts.SIG.HS256.key().build();
        assertThrows(io.jsonwebtoken.security.SignatureException.class,
                () -> io.jsonwebtoken.Jwts.parser().verifyWith(wrongKey).build().parseSignedClaims(token));
        mvc.perform(get("/api/menu/collections/{slug}/items", slug()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("Test dish"));
    }

    @Test
    void flywayCreatesMenuAndOrderingTablesAndEndpointReturnsDish() throws Exception {
        assertEquals(17, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name <> 'flyway_schema_history'", Integer.class));
        mvc.perform(get("/api/menu/collections/{slug}/items", slug()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value(item.toString()))
                .andExpect(jsonPath("$.items[0].profileScope").value("ITEM"))
                .andExpect(jsonPath("$.items[0].profile.allergenReviewStatus").value("NOT_REVIEWED"));
    }

    @Test
    void seededSignatureDishesHaveNoInventedClaimsOrPrices() {
        var response = handler.handle(new ListMenuQuery("signature-dishes"));
        assertEquals(4, response.items().size());
        assertEquals("Yellow Curry", response.items().getFirst().name());
        for (var dish : response.items()) {
            assertNull(dish.image());
            if (dish.slug().equals("crispy-pork-broccoli")) {
                assertEquals(1, dish.variations().size());
                assertEquals(2490, dish.variations().getFirst().priceMinor());
                assertEquals("NOT_REVIEWED", dish.variations().getFirst().profile().allergenReviewStatus());
                assertTrue(dish.variations().getFirst().profile().dietaryTags().isEmpty());
                assertTrue(dish.variations().getFirst().profile().allergens().isEmpty());
                continue;
            }
            assertTrue(dish.variations().isEmpty());
            assertEquals("NOT_REVIEWED", dish.profile().allergenReviewStatus());
            assertTrue(dish.profile().dietaryTags().isEmpty());
            assertTrue(dish.profile().allergens().isEmpty());
        }
    }

    @Test
    void importedChefMenuMatchesPrintedPricesAndChoices() {
        var menu = handler.handle(new ListMenuQuery("chefs-special-recommendations"));
        assertEquals(20, menu.items().size());
        long[] prices = {1190,1190,1190,1590,1190,1190,1790,1790,2790,2790,
                2890,2890,3190,2890,2390,2990,2490,2390,2590,2590};
        int variations = 0;
        for (int i = 0; i < prices.length; i++) {
            var dish = menu.items().get(i);
            for (var variation : dish.variations()) {
                assertEquals(prices[i], variation.priceMinor());
                assertEquals("AUD", variation.currency());
                assertEquals("NOT_REVIEWED", variation.profile().allergenReviewStatus());
                assertTrue(variation.profile().dietaryTags().isEmpty());
                assertTrue(variation.profile().allergens().isEmpty());
                variations++;
            }
        }
        assertEquals(23, variations);
        var lamb = menu.items().get(12);
        assertEquals("Lamb Shank with Curry", lamb.name());
        assertEquals(java.util.List.of("Green Curry", "Red Curry", "Yellow Curry", "Massaman Curry"),
                lamb.variations().stream().map(v -> v.name()).toList());
        assertTrue(lamb.variations().stream().noneMatch(v -> v.defaultVariation()));
        assertEquals(java.util.UUID.fromString("20000000-0000-0000-0000-000000000004"), menu.items().get(16).id());
    }

    @Test
    void unknownDraftFutureAndExpiredCollectionsAreNotFound() throws Exception {
        mvc.perform(get("/api/menu/collections/missing/items")).andExpect(status().isNotFound());
        for (String change : new String[]{"status='DRAFT'", "status='PUBLISHED', starts_at=CURRENT_TIMESTAMP + interval '1 day'",
                "starts_at=NULL, ends_at=CURRENT_TIMESTAMP"}) {
            jdbc.update("UPDATE menu_collection SET " + change + " WHERE id=?", collection);
            entityManager.clear();
            mvc.perform(get("/api/menu/collections/{slug}/items", slug())).andExpect(status().isNotFound());
        }
    }

    @Test
    void startIsInclusiveAndUnavailableDishStaysVisible() {
        jdbc.update("UPDATE menu_collection SET starts_at=CURRENT_TIMESTAMP WHERE id=?", collection);
        jdbc.update("UPDATE menu_item SET is_available=false WHERE id=?", item);
        assertFalse(readItem().available());
    }

    @Test
    void hiddenItemsAndInactiveCategoriesAreExcluded() {
        jdbc.update("UPDATE menu_item SET status='ARCHIVED' WHERE id=?", item);
        assertTrue(handler.handle(new ListMenuQuery(slug())).items().isEmpty());
        jdbc.update("UPDATE menu_item SET status='PUBLISHED' WHERE id=?", item);
        jdbc.update("UPDATE menu_category SET is_active=false WHERE id=?", category);
        assertTrue(handler.handle(new ListMenuQuery(slug())).items().isEmpty());
    }

    @Test
    void collectionMembershipIsIndependentAndOrdered() {
        UUID second = UUID.randomUUID(), otherCollection = UUID.randomUUID();
        jdbc.update("INSERT INTO menu_item(id,category_id,name,slug,description,status) VALUES (?,?,'Second',?,'Description','PUBLISHED')", second, category, "dish-" + second);
        jdbc.update("UPDATE menu_collection_item SET display_order=5 WHERE collection_id=?", collection);
        jdbc.update("INSERT INTO menu_collection_item(collection_id,menu_item_id,display_order) VALUES (?,?,1)", collection, second);
        assertEquals(second, readItem().id());
        jdbc.update("INSERT INTO menu_collection(id,name,slug,status) VALUES (?,'Other',?,'PUBLISHED')", otherCollection, "other-" + otherCollection);
        jdbc.update("INSERT INTO menu_collection_item(collection_id,menu_item_id) VALUES (?,?)", otherCollection, item);
        jdbc.update("DELETE FROM menu_collection WHERE id=?", otherCollection);
        assertEquals(2, handler.handle(new ListMenuQuery(slug())).items().size());
    }

    @Test
    void variationProfilesDoNotInheritAndStaleWarningsRemain() {
        UUID badge = UUID.randomUUID(), allergen = UUID.randomUUID(), variation = UUID.randomUUID();
        jdbc.update("INSERT INTO dietary_tag(id,code,name,description) VALUES (?,'TEST_BADGE','Test badge','Test only')", badge);
        jdbc.update("INSERT INTO menu_item_dietary_tag(menu_item_id,dietary_tag_id,verified_at) VALUES (?,?,CURRENT_TIMESTAMP)", item, badge);
        assertEquals(1, readItem().profile().dietaryTags().size());
        jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Pork',2450,true)", variation, item);
        var varied = readItem();
        assertEquals("VARIATION_REQUIRED", varied.profileScope());
        assertNull(varied.profile());
        assertTrue(varied.variations().getFirst().profile().dietaryTags().isEmpty());
        jdbc.update("INSERT INTO allergen(id,code,name,is_active) VALUES (?,'TEST_ALLERGEN','Test allergen',false)", allergen);
        jdbc.update("INSERT INTO menu_item_variation_allergen(variation_id,allergen_id,declaration,verified_at) VALUES (?,?,'CONTAINS',CURRENT_TIMESTAMP)", variation, allergen);
        jdbc.update("UPDATE menu_item_variation SET allergen_review_status='NEEDS_REVIEW' WHERE id=?", variation);
        var profile = readItem().variations().getFirst().profile();
        assertEquals("NEEDS_REVIEW", profile.allergenReviewStatus());
        assertEquals("CONTAINS", profile.allergens().getFirst().declaration());
    }

    @Test
    void unverifiedBadgesAreHiddenAndInactiveVariationsExcluded() {
        UUID badge = UUID.randomUUID(), variation = UUID.randomUUID();
        jdbc.update("INSERT INTO dietary_tag(id,code,name,description) VALUES (?,'TEST_BADGE','Test','Test')", badge);
        jdbc.update("INSERT INTO menu_item_dietary_tag(menu_item_id,dietary_tag_id) VALUES (?,?)", item, badge);
        jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_active) VALUES (?,?,'Inactive',100,false)", variation, item);
        assertTrue(readItem().profile().dietaryTags().isEmpty());
        assertTrue(readItem().variations().isEmpty());
    }

    @Test
    void negativePriceIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor) VALUES (?,?,'Standard',-1)", UUID.randomUUID(), item));
    }

    @Test
    void duplicateDefaultIsRejected() {
        jdbc.update("INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Small',100,true)", UUID.randomUUID(), item);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO menu_item_variation(id,menu_item_id,name,price_minor,is_default) VALUES (?,?,'Large',200,true)", UUID.randomUUID(), item));
    }

    @Test
    void reviewRequiresTimestamp() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE menu_item SET allergen_review_status='REVIEWED' WHERE id=?", item));
    }

    @Test
    void referencedCategoryCannotBeDeleted() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM menu_category WHERE id=?", category));
    }

    @Test
    void timestampTriggerRuns() {
        jdbc.update("UPDATE menu_item SET updated_at='2000-01-01'::timestamptz, version=version+1 WHERE id=?", item);
        assertTrue(jdbc.queryForObject("SELECT updated_at > '2000-01-02'::timestamptz FROM menu_item WHERE id=?", Boolean.class, item));
        assertEquals(1L, jdbc.queryForObject("SELECT version FROM menu_item WHERE id=?", Long.class, item));
    }
}
