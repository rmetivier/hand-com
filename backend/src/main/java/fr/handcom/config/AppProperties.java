package fr.handcom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
        String adminUsername,
        String adminPassword,
        String publicBaseUrl,
        String frontendUrl,
        String mediaDir,
        Instagram instagram,
        TikTok tiktok) {

    public record Instagram(String clientId, String clientSecret, String graphVersion) {
        public boolean configured() {
            return notBlank(clientId) && notBlank(clientSecret);
        }
    }

    public record TikTok(String clientKey, String clientSecret) {
        public boolean configured() {
            return notBlank(clientKey) && notBlank(clientSecret);
        }
    }

    /** URL publique sans slash final. */
    public String publicBase() {
        return stripSlash(publicBaseUrl);
    }

    public String frontendBase() {
        return stripSlash(frontendUrl);
    }

    /** Vrai si l'URL publique est joignable depuis Internet (pas localhost). */
    public boolean publicUrlIsReachable() {
        String u = publicBase().toLowerCase();
        return u.startsWith("https://") && !u.contains("localhost") && !u.contains("127.0.0.1");
    }

    private static String stripSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
