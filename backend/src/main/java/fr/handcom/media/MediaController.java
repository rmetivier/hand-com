package fr.handcom.media;

import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    public record MediaDto(Long id, String kind, String contentType, String originalName, long sizeBytes, String previewUrl) {
        public static MediaDto of(MediaFile m) {
            return new MediaDto(m.getId(), m.getKind().name(), m.getContentType(), m.getOriginalName(),
                    m.getSizeBytes(), MediaService.previewUrl(m));
        }
    }

    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping
    MediaDto upload(@RequestParam("file") MultipartFile file) throws IOException {
        return MediaDto.of(service.store(file));
    }
}
