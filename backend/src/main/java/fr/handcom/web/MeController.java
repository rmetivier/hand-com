package fr.handcom.web;

import fr.handcom.config.AppProperties;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

    private final AppProperties props;

    public MeController(AppProperties props) {
        this.props = props;
    }

    /** Sert à vérifier les identifiants à la connexion. */
    @GetMapping("/me")
    Map<String, String> me(Principal principal) {
        return Map.of("username", principal.getName());
    }

    /** État de la configuration, affiché sur la page « Comptes ». */
    @GetMapping("/config")
    Map<String, Object> config() {
        return Map.of(
                "publicBaseUrl", props.publicBase(),
                "publicUrlReachable", props.publicUrlIsReachable(),
                "instagramConfigured", props.instagram().configured(),
                "tiktokConfigured", props.tiktok().configured(),
                "instagramRedirectUri", props.publicBase() + "/api/oauth/instagram/callback",
                "tiktokRedirectUri", props.publicBase() + "/api/oauth/tiktok/callback");
    }
}
