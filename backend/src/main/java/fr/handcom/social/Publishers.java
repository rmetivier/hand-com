package fr.handcom.social;

import fr.handcom.post.Platform;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registre des intégrations, indexé par réseau. */
@Component
public class Publishers {

    private final Map<Platform, SocialPublisher> byPlatform = new EnumMap<>(Platform.class);

    public Publishers(List<SocialPublisher> publishers) {
        publishers.forEach(p -> byPlatform.put(p.platform(), p));
    }

    public SocialPublisher get(Platform platform) {
        SocialPublisher p = byPlatform.get(platform);
        if (p == null) throw new IllegalArgumentException("Réseau non pris en charge : " + platform);
        return p;
    }
}
