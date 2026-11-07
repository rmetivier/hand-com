package fr.handcom.social;

/** Erreur renvoyée par l'API d'un réseau social (message affiché tel quel dans l'interface). */
public class SocialApiException extends RuntimeException {

    public SocialApiException(String message) {
        super(message);
    }
}
