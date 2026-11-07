package fr.handcom.publish;

import fr.handcom.post.Platform;
import fr.handcom.post.Post;
import fr.handcom.post.PostPublication;
import fr.handcom.post.PostRepository;
import fr.handcom.post.PostStatus;
import fr.handcom.social.Publishers;
import fr.handcom.social.SocialAccount;
import fr.handcom.social.SocialAccountRepository;
import fr.handcom.social.SocialApiException;
import fr.handcom.social.SocialPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Vérifie régulièrement les posts arrivés à échéance et les publie sur chaque réseau choisi.
 * Les appels réseau (parfois longs pour les vidéos) se font hors transaction.
 */
@Component
public class PublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduler.class);

    private final PostRepository posts;
    private final SocialAccountRepository accounts;
    private final Publishers publishers;
    private final TransactionTemplate tx;

    public PublishScheduler(PostRepository posts, SocialAccountRepository accounts, Publishers publishers,
                            TransactionTemplate tx) {
        this.posts = posts;
        this.accounts = accounts;
        this.publishers = publishers;
        this.tx = tx;
    }

    /** Au démarrage : un post resté « en cours » vient d'un arrêt brutal, on le marque en échec. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        tx.executeWithoutResult(s -> posts.findByStatus(PostStatus.PUBLISHING).forEach(p -> {
            p.setStatus(PostStatus.FAILED);
            for (Platform platform : p.getPlatforms()) {
                if (!p.alreadyDoneOn(platform)) {
                    p.addPublication(new PostPublication(platform, PostPublication.Status.FAILED, null,
                            "Publication interrompue (serveur arrêté). Vérifiez sur le réseau avant de relancer."));
                }
            }
            posts.save(p);
        }));
    }

    @Scheduled(fixedDelayString = "${app.scheduler-delay-ms}", initialDelay = 5000)
    public void publishDuePosts() {
        List<Long> due = tx.execute(s -> {
            List<Long> ids = new ArrayList<>();
            for (Post p : posts.findByStatusAndScheduledAtLessThanEqual(PostStatus.SCHEDULED, Instant.now())) {
                p.setStatus(PostStatus.PUBLISHING);
                posts.save(p);
                ids.add(p.getId());
            }
            return ids;
        });
        if (due != null) due.forEach(this::publish);
    }

    void publish(Long postId) {
        Post post = posts.findById(postId).orElse(null);
        if (post == null) return;
        log.info("Publication du post {} sur {}", postId, post.getPlatforms());

        List<PostPublication> results = new ArrayList<>();
        for (Platform platform : post.getPlatforms()) {
            if (post.alreadyDoneOn(platform)) continue; // nouvel essai : on ne republie pas ce qui a réussi
            results.add(publishOn(platform, post));
        }

        tx.executeWithoutResult(s -> {
            Post fresh = posts.findById(postId).orElseThrow();
            results.forEach(fresh::addPublication);
            long ok = fresh.getPlatforms().stream().filter(fresh::alreadyDoneOn).count();
            fresh.setStatus(ok == fresh.getPlatforms().size() ? PostStatus.PUBLISHED
                    : ok == 0 ? PostStatus.FAILED : PostStatus.PARTIAL);
            posts.save(fresh);
            log.info("Post {} : {}", postId, fresh.getStatus());
        });
    }

    private PostPublication publishOn(Platform platform, Post post) {
        try {
            SocialPublisher publisher = publishers.get(platform);
            SocialAccount account = accounts.findByPlatform(platform)
                    .orElseThrow(() -> new SocialApiException("Compte " + platform + " non connecté (page « Comptes »)."));
            publisher.refreshIfNeeded(account);
            accounts.save(account);
            SocialPublisher.Result r = publisher.publish(post, account);
            return new PostPublication(platform, r.status(), r.externalId(), r.message());
        } catch (SocialApiException e) {
            log.warn("Échec {} pour le post {} : {}", platform, post.getId(), e.getMessage());
            return new PostPublication(platform, PostPublication.Status.FAILED, null, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Erreur inattendue {} pour le post {}", platform, post.getId(), e);
            return new PostPublication(platform, PostPublication.Status.FAILED, null, "Erreur inattendue : " + e.getMessage());
        }
    }
}
