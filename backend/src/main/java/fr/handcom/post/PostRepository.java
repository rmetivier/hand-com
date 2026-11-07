package fr.handcom.post;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByStatusAndScheduledAtLessThanEqual(PostStatus status, Instant now);

    List<Post> findByStatus(PostStatus status);

    List<Post> findAllByOrderByScheduledAtDesc();
}
