package fr.handcom.post;

import fr.handcom.media.MediaFile;
import fr.handcom.media.MediaRepository;
import fr.handcom.post.PostDtos.PostRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository posts;
    private final MediaRepository mediaRepository;

    public PostService(PostRepository posts, MediaRepository mediaRepository) {
        this.posts = posts;
        this.mediaRepository = mediaRepository;
    }

    @Transactional(readOnly = true)
    public List<Post> list() {
        return posts.findAllByOrderByScheduledAtDesc();
    }

    @Transactional(readOnly = true)
    public Post get(Long id) {
        return posts.findById(id).orElseThrow(() -> new NoSuchElementException("Post introuvable : " + id));
    }

    @Transactional
    public Post create(PostRequest request) {
        Post post = new Post();
        apply(post, request);
        return posts.save(post);
    }

    @Transactional
    public Post update(Long id, PostRequest request) {
        Post post = get(id);
        if (!post.getStatus().editable()) {
            throw new IllegalStateException("Ce post est en cours de publication ou déjà publié : il n'est plus modifiable.");
        }
        apply(post, request);
        return posts.save(post);
    }

    @Transactional
    public void delete(Long id) {
        Post post = get(id);
        if (post.getStatus() == PostStatus.PUBLISHING) {
            throw new IllegalStateException("Publication en cours, réessayez dans quelques instants.");
        }
        posts.delete(post);
    }

    /** Publie dès le prochain passage du planificateur (quelques secondes). */
    @Transactional
    public Post publishNow(Long id) {
        Post post = get(id);
        if (!post.getStatus().editable()) {
            throw new IllegalStateException("Ce post est déjà publié ou en cours de publication.");
        }
        validate(post.getMedia(), post.getPlatforms());
        post.setScheduledAt(Instant.now());
        post.setStatus(PostStatus.SCHEDULED);
        return posts.save(post);
    }

    private void apply(Post post, PostRequest request) {
        List<MediaFile> media = loadMediaInOrder(request.mediaIds());
        validate(media, request.platforms());

        if (request.scheduledAt() != null
                && request.scheduledAt().isBefore(Instant.now().minus(Duration.ofMinutes(1)))) {
            throw new IllegalArgumentException("La date de publication est déjà passée.");
        }

        post.setCaption(request.caption() == null ? "" : request.caption().strip());
        post.getMedia().clear();
        post.getMedia().addAll(media);
        post.getPlatforms().clear();
        post.getPlatforms().addAll(request.platforms());
        post.setScheduledAt(request.scheduledAt());
        post.setStatus(request.scheduledAt() == null ? PostStatus.DRAFT : PostStatus.SCHEDULED);
    }

    private List<MediaFile> loadMediaInOrder(List<Long> ids) {
        Map<Long, MediaFile> byId = mediaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));
        List<MediaFile> ordered = new ArrayList<>();
        for (Long id : ids) {
            MediaFile m = byId.get(id);
            if (m == null) throw new IllegalArgumentException("Média introuvable : " + id);
            ordered.add(m);
        }
        return ordered;
    }

    /** Règles imposées par les API des réseaux. */
    static void validate(List<MediaFile> media, java.util.Set<Platform> platforms) {
        if (platforms.contains(Platform.INSTAGRAM)) {
            if (media.isEmpty() || media.size() > 10) {
                throw new IllegalArgumentException("Instagram : entre 1 et 10 médias (au-delà d'un, c'est un carrousel).");
            }
        }
        if (platforms.contains(Platform.TIKTOK)) {
            if (media.size() != 1 || !media.get(0).isVideo()) {
                throw new IllegalArgumentException("TikTok : exactement une vidéo.");
            }
        }
    }
}
