package fr.handcom.post;

public enum PostStatus {
    /** Pas de date : brouillon. */
    DRAFT,
    /** Date fixée, en attente du planificateur. */
    SCHEDULED,
    /** Publication en cours. */
    PUBLISHING,
    /** Publié (ou envoyé en brouillon TikTok) sur tous les réseaux choisis. */
    PUBLISHED,
    /** Réussi sur certains réseaux seulement. */
    PARTIAL,
    FAILED;

    public boolean editable() {
        return this == DRAFT || this == SCHEDULED || this == FAILED || this == PARTIAL;
    }
}
