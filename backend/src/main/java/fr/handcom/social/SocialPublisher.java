package fr.handcom.social;

import fr.handcom.post.Platform;
import fr.handcom.post.Post;
import fr.handcom.post.PostPublication;

/** Contrat commun aux intégrations Instagram et TikTok. */
public interface SocialPublisher {

    Platform platform();

    boolean configured();

    /** URL de la page d'autorisation du réseau. */
    String authorizeUrl(String state);

    /** Échange le code reçu au retour OAuth contre des jetons, et complète le compte. */
    void completeAuthorization(String code, SocialAccount account);

    /** Rafraîchit le jeton s'il expire bientôt. */
    void refreshIfNeeded(SocialAccount account);

    /** Publie le post. Lève SocialApiException en cas d'échec. */
    Result publish(Post post, SocialAccount account);

    record Result(PostPublication.Status status, String externalId, String message) {
    }
}
