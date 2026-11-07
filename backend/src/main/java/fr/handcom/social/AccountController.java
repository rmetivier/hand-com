package fr.handcom.social;

import fr.handcom.config.AppProperties;
import fr.handcom.post.Platform;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    public record AccountDto(Platform platform, boolean configured, boolean connected, String username,
                             Instant connectedAt, Instant accessExpiresAt) {
    }

    private final SocialAccountRepository accounts;
    private final Publishers publishers;
    private final OAuthStateStore states;
    private final AppProperties props;

    public AccountController(SocialAccountRepository accounts, Publishers publishers, OAuthStateStore states,
                             AppProperties props) {
        this.accounts = accounts;
        this.publishers = publishers;
        this.states = states;
        this.props = props;
    }

    @GetMapping("/api/accounts")
    List<AccountDto> list() {
        return Arrays.stream(Platform.values()).map(p -> {
            boolean configured = publishers.get(p).configured();
            return accounts.findByPlatform(p)
                    .map(a -> new AccountDto(p, configured, true, a.getUsername(), a.getConnectedAt(), a.getAccessExpiresAt()))
                    .orElse(new AccountDto(p, configured, false, null, null, null));
        }).toList();
    }

    @DeleteMapping("/api/accounts/{platform}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void disconnect(@PathVariable String platform) {
        accounts.findByPlatform(Platform.fromPath(platform)).ifPresent(accounts::delete);
    }

    /** Étape 1 : l'interface récupère l'URL d'autorisation puis y redirige le navigateur. */
    @GetMapping("/api/oauth/{platform}/authorize-url")
    Map<String, String> authorizeUrl(@PathVariable String platform) {
        Platform p = Platform.fromPath(platform);
        SocialPublisher publisher = publishers.get(p);
        if (!publisher.configured()) {
            throw new IllegalStateException("Identifiants de l'app " + p + " manquants dans le fichier .env");
        }
        return Map.of("url", publisher.authorizeUrl(states.create(p)));
    }

    /** Étape 2 : le réseau renvoie le navigateur ici avec un code, qu'on échange contre des jetons. */
    @GetMapping("/api/oauth/{platform}/callback")
    void callback(@PathVariable String platform,
                  @RequestParam(required = false) String code,
                  @RequestParam(required = false) String state,
                  @RequestParam(required = false) String error,
                  @RequestParam(name = "error_description", required = false) String errorDescription,
                  HttpServletResponse response) throws IOException {
        Platform p = Platform.fromPath(platform);
        String target = props.frontendBase() + "/comptes?platform=" + p.name().toLowerCase();
        try {
            if (error != null) {
                throw new SocialApiException(errorDescription != null ? errorDescription : error);
            }
            if (!states.consume(state, p)) {
                throw new SocialApiException("Lien de connexion expiré ou invalide, recommencez.");
            }
            if (code == null || code.isBlank()) {
                throw new SocialApiException("Aucun code reçu");
            }
            SocialAccount account = accounts.findByPlatform(p).orElseGet(() -> new SocialAccount(p));
            publishers.get(p).completeAuthorization(code, account);
            account.setConnectedAt(Instant.now());
            accounts.save(account);
            response.sendRedirect(target + "&status=ok");
        } catch (RuntimeException e) {
            log.warn("Échec de connexion {} : {}", p, e.getMessage());
            response.sendRedirect(target + "&status=error&message=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        }
    }
}
