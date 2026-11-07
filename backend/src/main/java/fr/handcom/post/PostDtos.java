package fr.handcom.post;

import fr.handcom.media.MediaController.MediaDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class PostDtos {

    private PostDtos() {
    }

    /** Création / modification. scheduledAt null = brouillon. */
    public record PostRequest(
            @Size(max = 2200, message = "2200 caractères maximum (limite Instagram)") String caption,
            @NotEmpty(message = "ajoutez au moins un média") List<Long> mediaIds,
            @NotEmpty(message = "choisissez au moins un réseau") Set<Platform> platforms,
            Instant scheduledAt) {
    }

    public record PublicationDto(Platform platform, PostPublication.Status status, String externalId,
                                 String message, Instant attemptedAt) {
        static PublicationDto of(PostPublication p) {
            return new PublicationDto(p.getPlatform(), p.getStatus(), p.getExternalId(), p.getMessage(), p.getAttemptedAt());
        }
    }

    public record PostDto(Long id, String caption, List<MediaDto> media, Set<Platform> platforms,
                          Instant scheduledAt, PostStatus status, List<PublicationDto> publications,
                          Instant createdAt, Instant updatedAt) {
        public static PostDto of(Post p) {
            return new PostDto(p.getId(), p.getCaption(),
                    p.getMedia().stream().map(MediaDto::of).toList(),
                    Set.copyOf(p.getPlatforms()),
                    p.getScheduledAt(), p.getStatus(),
                    p.getPublications().stream().map(PublicationDto::of).toList(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }
}
