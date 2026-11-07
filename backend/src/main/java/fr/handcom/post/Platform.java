package fr.handcom.post;

public enum Platform {
    INSTAGRAM, TIKTOK;

    public static Platform fromPath(String value) {
        try {
            return Platform.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Réseau inconnu : " + value);
        }
    }
}
