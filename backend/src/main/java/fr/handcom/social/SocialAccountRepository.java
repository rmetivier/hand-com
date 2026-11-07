package fr.handcom.social;

import fr.handcom.post.Platform;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByPlatform(Platform platform);
}
