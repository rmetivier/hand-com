package fr.handcom.social;

import fr.handcom.post.Platform;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Jetons "state" OAuth à usage unique, valables 10 minutes (protection CSRF du retour OAuth). */
@Component
public class OAuthStateStore {

    private record Entry(Platform platform, Instant expiresAt) {
    }

    private static final Duration TTL = Duration.ofMinutes(10);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> states = new ConcurrentHashMap<>();

    public String create(Platform platform) {
        states.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(Instant.now()));
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        states.put(state, new Entry(platform, Instant.now().plus(TTL)));
        return state;
    }

    /** Consomme le state ; vrai seulement s'il existe, n'a pas expiré et correspond au réseau. */
    public boolean consume(String state, Platform platform) {
        if (state == null) return false;
        Entry entry = states.remove(state);
        return entry != null && entry.platform() == platform && entry.expiresAt().isAfter(Instant.now());
    }
}
