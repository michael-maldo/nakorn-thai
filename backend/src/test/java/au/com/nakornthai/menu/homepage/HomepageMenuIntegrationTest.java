package au.com.nakornthai.menu.homepage;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TEST_URL", matches = ".+")
class HomepageMenuIntegrationTest {
    @DynamicPropertySource static void db(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> System.getenv("DB_TEST_URL"));
        p.add("spring.datasource.username", () -> System.getenv().getOrDefault("DB_TEST_USERNAME", "nakorn_test"));
        p.add("spring.datasource.password", () -> System.getenv().getOrDefault("DB_TEST_PASSWORD", ""));
    }
    @Autowired HomepageMenuHandler handler;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    UUID collection(String status, boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO menu_collection(id,name,slug,status,is_active) VALUES (?, 'Featured test', ?, ?, ?)", id, "home-" + id, status, active);
        return id;
    }

    @Test void selectionAndReorderingPersistWithVersionAndEmptyHidesAll() {
        UUID first = collection("PUBLISHED", true), second = collection("PUBLISHED", false);
        var initial = handler.read();
        var saved = handler.save(new HomepageMenuRequest(initial.version(), List.of(first, second)));
        assertTrue(saved.version() > initial.version());
        em.clear();
        assertEquals(List.of(first, second), handler.read().collectionIds());
        var reordered = handler.save(new HomepageMenuRequest(saved.version(), List.of(second, first)));
        em.clear();
        jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        assertEquals(List.of(second, first), handler.read().collectionIds());
        var visible = handler.publicCollections();
        assertEquals(List.of(second, first), visible.stream().map(c -> c.id()).toList());
        assertFalse(visible.getFirst().availability().available());
        handler.save(new HomepageMenuRequest(reordered.version(), List.of()));
        em.clear();
        assertTrue(handler.publicCollections().isEmpty());
    }

    @Test void publicReadExcludesUnselectedDraftAndArchivedCollections() {
        UUID published = collection("PUBLISHED", true), draft = collection("DRAFT", true), archived = collection("ARCHIVED", true);
        collection("PUBLISHED", true);
        handler.save(new HomepageMenuRequest(handler.read().version(), List.of(draft, published, archived)));
        em.clear();
        assertEquals(List.of(published), handler.publicCollections().stream().map(c -> c.id()).toList());
        assertEquals(3, handler.read().collectionIds().size());
    }

    @Test void staleVersionCannotOverwriteSelection() {
        var initial = handler.read();
        UUID id = collection("PUBLISHED", true);
        handler.save(new HomepageMenuRequest(initial.version(), List.of(id)));
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> handler.save(new HomepageMenuRequest(initial.version(), List.of()))).getStatusCode().value());
    }

    @Test void duplicatesAreRejected() {
        UUID id = collection("PUBLISHED", true);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> handler.save(new HomepageMenuRequest(handler.read().version(), List.of(id, id)))).getStatusCode().value());
    }

    @Test void missingCollectionIsRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> handler.save(new HomepageMenuRequest(handler.read().version(), List.of(UUID.randomUUID())))).getStatusCode().value());
    }
}
