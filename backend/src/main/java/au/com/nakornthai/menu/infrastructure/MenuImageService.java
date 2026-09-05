package au.com.nakornthai.menu.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.*;
import java.util.UUID;

@Service
public class MenuImageService {
    private final EntityManager em;
    private final Path directory;
    public MenuImageService(EntityManager em, @Value("${MENU_MEDIA_DIRECTORY:./menu-media}") String directory) {
        this.em = em; this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public Path file(String name) {
        if (!name.matches("[a-f0-9-]{36}\\.jpg")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return directory.resolve(name);
    }
    @Transactional
    public void save(UUID id, long version, MultipartFile file, String alt, int x, int y, double zoom) throws IOException {
        if (alt == null || alt.isBlank() || alt.length() > 255 || x < 0 || x > 100 || y < 0 || y > 100
                || !Double.isFinite(zoom) || zoom < 1 || zoom > 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image description or focus");
        var item = em.find(MenuItemJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (item == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!Long.valueOf(version).equals(item.getVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var picture = item.getImages().stream().filter(MenuItemImageJpaEntity::isPrimary).findFirst().orElse(null);
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 8 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            BufferedImage decoded;
            try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use JPEG or PNG");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName();
                    if (!(format.equalsIgnoreCase("JPEG") || format.equalsIgnoreCase("PNG"))
                            || (long) reader.getWidth(0) * reader.getHeight(0) > 16000000)
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use JPEG or PNG up to 16 megapixels");
                    decoded = reader.read(0);
                } finally { reader.dispose(); }
            } catch (javax.imageio.IIOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image");
            }
            var rgb = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = rgb.createGraphics();
            graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(decoded, 0, 0, null); graphics.dispose();
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + ".jpg";
            Path target = directory.resolve(name);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) try { Files.deleteIfExists(target); } catch (IOException ignored) { }
                }
            });
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                ImageIO.write(rgb, "JPEG", output);
            }
            if (picture == null) {
                picture = new MenuItemImageJpaEntity(); picture.setMenuItem(item); picture.setPrimary(true);
            }
            picture.setStorageKey("menu/" + name);
        }
        if (picture == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image first");
        picture.setAltText(alt); picture.setFocusX(x); picture.setFocusY(y); picture.setZoom(zoom);
        if (picture.getId() == null) em.persist(picture);
        em.lock(item, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
}
