package fr.handcom.social;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Petites aides pour appeler les API et lire leurs réponses JSON (désérialisées en Map). */
public final class ApiCalls {

    public static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };

    private ApiCalls() {
    }

    /** Exécute un appel HTTP et transforme les erreurs en message lisible. */
    public static <T> T call(String what, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            if (body.length() > 500) body = body.substring(0, 500) + "…";
            throw new SocialApiException(what + " : HTTP " + e.getStatusCode().value() + " " + body);
        } catch (RestClientException e) {
            throw new SocialApiException(what + " : " + e.getMessage());
        }
    }

    public static String str(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        return v == null ? null : v.toString();
    }

    public static long num(Map<String, Object> map, String key, long fallback) {
        Object v = map == null ? null : map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        if (v instanceof Map<?, ?> m) return (Map<String, Object>) m;
        if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SocialApiException("Interrompu");
        }
    }
}
