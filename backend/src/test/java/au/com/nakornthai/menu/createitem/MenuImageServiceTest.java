package au.com.nakornthai.menu.createitem;
import au.com.nakornthai.menu.infrastructure.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MenuImageServiceTest {
    @TempDir Path directory;
    EntityManager em;
    MenuImageService service;
    UUID id = UUID.randomUUID();
    MenuItemJpaEntity item;
    @BeforeEach void setup() {
        em = mock(EntityManager.class); item = mock(MenuItemJpaEntity.class);
        when(em.find(MenuItemJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE)).thenReturn(item);
        when(item.getVersion()).thenReturn(2L); when(item.getImages()).thenReturn(List.of());
        service = new MenuImageService(em, directory.toString());
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void finish() { TransactionSynchronizationManager.clearSynchronization(); }
    @Test void uploadReencodesImageAndPersistsFocusAndCleansRollback() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
        var file = new MockMultipartFile("file", "../../unsafe.png", "image/png", bytes.toByteArray());
        service.save(id, 2, file, "Curry", 25, 75, 1.5);
        var captured = org.mockito.ArgumentCaptor.forClass(MenuItemImageJpaEntity.class);
        verify(em).persist(captured.capture());
        var photo = captured.getValue();
        assertEquals(25, photo.getFocusX()); assertEquals(75, photo.getFocusY()); assertEquals(1.5, photo.getZoom());
        assertTrue(photo.getStorageKey().matches("menu/[a-f0-9-]{36}\\.jpg"));
        Path saved = service.file(photo.getStorageKey().substring(5));
        assertNotNull(ImageIO.read(saved.toFile()));
        verify(em).lock(item, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertFalse(Files.exists(saved));
    }
    @Test void focusOnlySavePreservesStoredFile() throws Exception {
        var photo = new MenuItemImageJpaEntity(); photo.setPrimary(true); photo.setStorageKey("menu/retained.jpg");
        when(item.getImages()).thenReturn(List.of(photo));
        service.save(id, 2, null, "Updated alt", 0, 100, 3);
        assertEquals("menu/retained.jpg", photo.getStorageKey()); assertEquals(100, photo.getFocusY());
    }
    @Test void rejectsStaleVersionAndInvalidFocus() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.save(id, 1, null, "Curry", 50, 50, 1)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.save(id, 2, null, "Curry", 101, 50, 1)).getStatusCode().value());
        verify(em, never()).persist(any());
    }
    @Test void rejectsNonImageAndTraversal() {
        var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not a photo".getBytes());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.save(id, 2, file, "Curry", 50, 50, 1)).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.file("../../backend.env"));
    }
}
