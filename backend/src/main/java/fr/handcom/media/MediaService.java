package fr.handcom.media;

import fr.handcom.config.AppProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    /** Formats acceptés et extension associée. */
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "video/mp4", ".mp4",
            "video/quicktime", ".mov");

    private final MediaRepository repository;
    private final AppProperties props;
    private final Path dir;

    public MediaService(MediaRepository repository, AppProperties props) throws IOException {
        this.repository = repository;
        this.props = props;
        this.dir = Path.of(props.mediaDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
    }

    public MediaFile store(MultipartFile file) throws IOException {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.containsKey(type)) {
            throw new IllegalArgumentException("Format non supporté (" + type + "). Formats acceptés : JPEG, PNG, MP4, MOV.");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide");
        }

        MediaFile media = new MediaFile();
        media.setOriginalName(file.getOriginalFilename());
        String base = UUID.randomUUID().toString();

        if (type.equals("image/png")) {
            // L'API Instagram n'accepte que le JPEG : conversion automatique.
            Path target = dir.resolve(base + ".jpg");
            try (InputStream in = file.getInputStream()) {
                convertToJpeg(in, target);
            }
            media.setStoredName(target.getFileName().toString());
            media.setContentType("image/jpeg");
            media.setSizeBytes(Files.size(target));
        } else {
            Path target = dir.resolve(base + ALLOWED.get(type));
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            media.setStoredName(target.getFileName().toString());
            media.setContentType(type);
            media.setSizeBytes(Files.size(target));
        }
        media.setKind(type.startsWith("video/") ? MediaFile.Kind.VIDEO : MediaFile.Kind.IMAGE);
        return repository.save(media);
    }

    public Path path(MediaFile media) {
        return dir.resolve(media.getStoredName());
    }

    /** URL absolue (via le tunnel) utilisée par Instagram pour télécharger le fichier. */
    public String publicUrl(MediaFile media) {
        return props.publicBase() + "/media/" + media.getStoredName();
    }

    /** URL relative utilisée par l'interface pour l'aperçu. */
    public static String previewUrl(MediaFile media) {
        return "/media/" + media.getStoredName();
    }

    private static void convertToJpeg(InputStream in, Path target) throws IOException {
        BufferedImage src = ImageIO.read(in);
        if (src == null) throw new IllegalArgumentException("Image PNG illisible");
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE); // fond blanc à la place de la transparence
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        if (!ImageIO.write(rgb, "jpg", target.toFile())) {
            throw new IOException("Encodeur JPEG indisponible");
        }
    }
}
