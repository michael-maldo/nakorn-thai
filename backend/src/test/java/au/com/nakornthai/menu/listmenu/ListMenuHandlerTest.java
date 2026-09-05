package au.com.nakornthai.menu.listmenu;

import au.com.nakornthai.menu.domain.MenuItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ListMenuHandlerTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setupValidation() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidation() { factory.close(); }

    @Test
    void returnsVisibleEmptyCollection() {
        var collection = new MenuItem.Collection(UUID.randomUUID(), "signature-dishes", "Signature Dishes", null, List.of());
        var response = new ListMenuHandler(slug -> Optional.of(collection), validator)
                .handle(new ListMenuQuery("signature-dishes"));
        assertEquals(collection.id(), response.id());
        assertTrue(response.items().isEmpty());
    }

    @Test
    void missingCollectionIsNotFound() {
        var error = assertThrows(ResponseStatusException.class,
                () -> new ListMenuHandler(slug -> Optional.empty(), validator).handle(new ListMenuQuery("missing")));
        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void invalidSlugNeverReachesRepository() {
        var handler = new ListMenuHandler(slug -> { throw new AssertionError("Repository must not be called"); }, validator);
        for (String slug : List.of("", " ", "../private", "x' OR true --", "a".repeat(181))) {
            assertThrows(ResponseStatusException.class, () -> handler.handle(new ListMenuQuery(slug)));
        }
    }
}
