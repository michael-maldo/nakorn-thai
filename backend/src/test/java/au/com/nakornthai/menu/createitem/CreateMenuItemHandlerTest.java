package au.com.nakornthai.menu.createitem;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class CreateMenuItemHandlerTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final String slug = "crud-test-" + UUID.randomUUID();
    private static final String STAFF = "/api/staff/menu/items";
    private final UUID COLLECTION = UUID.randomUUID();
    private final UUID CATEGORY = UUID.randomUUID();
    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO menu_category(id,name,slug) VALUES (?,'CRUD category',?)", CATEGORY,"category-"+CATEGORY);
        jdbc.update("INSERT INTO menu_collection(id,name,slug,status) VALUES (?,'CRUD collection',?,'PUBLISHED')",COLLECTION,"collection-"+COLLECTION);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM menu_collection_item WHERE menu_item_id IN (SELECT id FROM menu_item WHERE slug = ?)", slug);
        jdbc.update("DELETE FROM menu_item_variation WHERE menu_item_id IN (SELECT id FROM menu_item WHERE slug = ?)", slug);
        jdbc.update("DELETE FROM menu_item WHERE slug = ?", slug);
        jdbc.update("DELETE FROM menu_collection WHERE id=?",COLLECTION);
        jdbc.update("DELETE FROM menu_category WHERE id=?",CATEGORY);
    }

    private String body(String name, String status, String version, String collections) {
        return """
            {"name":"%s","slug":"%s","description":"Freshly prepared test dish",
            "categoryId":"%s","status":"%s",
            "available":true,"displayOrder":99,"collectionIds":%s,"version":%s}
            """.formatted(name, slug, CATEGORY, status, collections, version);
    }
    private String selected() { return "[\"" + COLLECTION + "\"]"; }
    private UUID create() throws Exception {
        mvc.perform(post(STAFF).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Test curry", "PUBLISHED", "null", selected())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty());
        return jdbc.queryForObject("SELECT id FROM menu_item WHERE slug = ?", UUID.class, slug);
    }
    private long version(UUID id) { return jdbc.queryForObject("SELECT version FROM menu_item WHERE id = ?", Long.class, id); }

    @Test
    void persistsCreateUpdateArchiveAndRestoreAndRejectsStaleEdits() throws Exception {
        var id = create();
        mvc.perform(get("/api/menu/collections/collection-"+COLLECTION+"/items"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].name").value("Test curry"));
        long before = version(id);
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Changed curry", "PUBLISHED", "" + before, selected())))
                .andExpect(status().isNoContent());
        assertTrue(version(id) > before);
        assertEquals("NEEDS_REVIEW", jdbc.queryForObject("SELECT allergen_review_status FROM menu_item WHERE id = ?", String.class, id));
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Lost edit", "PUBLISHED", "" + before, selected())))
                .andExpect(status().isConflict());
        mvc.perform(delete(STAFF + "/" + id).param("version", "" + version(id))
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/menu/collections/collection-"+COLLECTION+"/items"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')]").isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM menu_collection_item WHERE menu_item_id = ?", Integer.class, id));
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Restored curry", "PUBLISHED", "" + version(id), selected())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/menu/collections/collection-"+COLLECTION+"/items"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].name").value("Restored curry"));
    }

    @Test
    void collectionOnlyEditAdvancesVersionAndRemovesHomepageMembership() throws Exception {
        var id = create();
        long before = version(id);
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Test curry", "PUBLISHED", "" + before, "[]")))
                .andExpect(status().isNoContent());
        assertTrue(version(id) > before);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM menu_collection_item WHERE menu_item_id = ?", Integer.class, id));
    }

    @Test
    void createsAndUpdatesPriceInCentsWithoutChangingFoodReview() throws Exception {
        var id = create();
        String payload = body("Test curry", "PUBLISHED", "" + version(id), selected()).trim();
        payload = payload.substring(0, payload.length() - 1) + ",\"prices\":[{\"id\":null,\"amount\":\"24.90\"}]}";
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(payload)).andExpect(status().isNoContent());
        var variation = jdbc.queryForObject("SELECT id FROM menu_item_variation WHERE menu_item_id = ?", UUID.class, id);
        assertEquals(2490L, jdbc.queryForObject("SELECT price_minor FROM menu_item_variation WHERE id = ?", Long.class, variation));
        long previous = version(id);
        payload = body("Test curry", "PUBLISHED", "" + previous, selected()).trim();
        payload = payload.substring(0, payload.length() - 1) + ",\"prices\":[{\"id\":\"" + variation + "\",\"amount\":\"26.05\"}]}";
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(payload)).andExpect(status().isNoContent());
        assertEquals(2605L, jdbc.queryForObject("SELECT price_minor FROM menu_item_variation WHERE id = ?", Long.class, variation));
        assertEquals("NOT_REVIEWED", jdbc.queryForObject("SELECT allergen_review_status FROM menu_item_variation WHERE id = ?", String.class, variation));
        mvc.perform(get("/api/menu/collections/collection-"+COLLECTION+"/items"))
                .andExpect(jsonPath("$.items[?(@.slug == '" + slug + "')].variations[0].priceMinor").value(2605));
        mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(payload)).andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidPricePrecisionAndOtherDishVariation() throws Exception {
        var id = create();
        for (String prices : new String[]{"[{\"amount\":\"1.001\"}]", "[{\"amount\":-1}]",
                "[{\"id\":\"" + UUID.randomUUID() + "\",\"amount\":12.00}]"}) {
            String payload = body("Test curry", "PUBLISHED", "" + version(id), selected()).trim();
            payload = payload.substring(0, payload.length() - 1) + ",\"prices\":" + prices + "}";
            mvc.perform(put(STAFF + "/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM menu_item_variation WHERE menu_item_id = ?", Integer.class, id));
    }

    @Test
    void duplicateSlugConflictsAndUnknownCollectionRollsBack() throws Exception {
        String unknown = "[\"" + UUID.randomUUID() + "\"]";
        mvc.perform(post(STAFF).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Test curry", "DRAFT", "null", unknown)))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM menu_item WHERE slug = ?", Integer.class, slug));
        create();
        mvc.perform(post(STAFF).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body("Duplicate curry", "DRAFT", "null", selected())))
                .andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM menu_item WHERE slug = ?", Integer.class, slug));
    }
}
