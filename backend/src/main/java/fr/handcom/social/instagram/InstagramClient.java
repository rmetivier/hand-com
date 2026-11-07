package fr.handcom.social.instagram;

import static fr.handcom.social.ApiCalls.JSON;
import static fr.handcom.social.ApiCalls.call;
import static fr.handcom.social.ApiCalls.num;
import static fr.handcom.social.ApiCalls.obj;
import static fr.handcom.social.ApiCalls.sleep;
import static fr.handcom.social.ApiCalls.str;

import fr.handcom.config.AppProperties;
import fr.handcom.media.MediaFile;
import fr.handcom.media.MediaService;
import fr.handcom.post.Platform;
import fr.handcom.post.Post;
import fr.handcom.post.PostPublication;
import fr.handcom.social.SocialAccount;
import fr.handcom.social.SocialApiException;
import fr.handcom.social.SocialPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Intégration « Instagram API with Instagram Login » (graph.instagram.com).
 * Ne nécessite pas de page Facebook, seulement un compte Instagram professionnel.
 */
@Component
public class InstagramClient implements SocialPublisher {

    private static final Logger log = LoggerFactory.getLogger(InstagramClient.class);
    private static final String SCOPES = "instagram_business_basic,instagram_business_content_publish";
    private static final Duration MEDIA_PROCESSING_TIMEOUT = Duration.ofMinutes(5);

    private final RestClient http = RestClient.create();
    private final AppProperties props;
    private final MediaService mediaService;

    public InstagramClient(AppProperties props, MediaService mediaService) {
        this.props = props;
        this.mediaService = mediaService;
    }

    @Override
    public Platform platform() {
        return Platform.INSTAGRAM;
    }

    @Override
    public boolean configured() {
        return props.instagram().configured();
    }

    private String graph() {
        return "https://graph.instagram.com/" + props.instagram().graphVersion();
    }

    private String redirectUri() {
        return props.publicBase() + "/api/oauth/instagram/callback";
    }

    // ---------------------------------------------------------------- OAuth

    @Override
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://www.instagram.com/oauth/authorize")
                .queryParam("enable_fb_login", "0")
                .queryParam("client_id", props.instagram().clientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    @Override
    public void completeAuthorization(String code, SocialAccount account) {
        // 1. Code -> jeton courte durée (1 h)
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.instagram().clientId());
        form.add("client_secret", props.instagram().clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri());
        form.add("code", code);
        Map<String, Object> shortLived = call("Instagram (échange du code)", () -> http.post()
                .uri("https://api.instagram.com/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JSON));
        // Selon les versions, la réponse est à plat ou dans "data": [...]
        Map<String, Object> data = obj(shortLived, "data") != null ? obj(shortLived, "data") : shortLived;
        String shortToken = str(data, "access_token");
        if (shortToken == null) throw new SocialApiException("Instagram : aucun jeton reçu");

        // 2. Jeton courte durée -> jeton longue durée (60 jours)
        Map<String, Object> longLived = call("Instagram (jeton longue durée)", () -> http.get()
                .uri("https://graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret={s}&access_token={t}",
                        props.instagram().clientSecret(), shortToken)
                .retrieve().body(JSON));
        String token = str(longLived, "access_token");
        long expiresIn = num(longLived, "expires_in", Duration.ofDays(60).toSeconds());
        account.updateTokens(token, Instant.now().plusSeconds(expiresIn), null, null);

        // 3. Identité du compte
        Map<String, Object> me = call("Instagram (profil)", () -> http.get()
                .uri(graph() + "/me?fields=user_id,username&access_token={t}", token)
                .retrieve().body(JSON));
        String userId = str(me, "user_id") != null ? str(me, "user_id") : str(data, "user_id");
        account.setExternalUserId(userId);
        account.setUsername(str(me, "username"));
    }

    @Override
    public void refreshIfNeeded(SocialAccount account) {
        // Les jetons longue durée se prolongent de 60 jours s'ils ont au moins 24 h et ne sont pas expirés.
        Instant expires = account.getAccessExpiresAt();
        if (expires == null || expires.isAfter(Instant.now().plus(Duration.ofDays(7)))) return;
        if (expires.isBefore(Instant.now())) {
            throw new SocialApiException("Jeton Instagram expiré : reconnectez le compte depuis la page « Comptes ».");
        }
        if (account.getTokenIssuedAt() != null && account.getTokenIssuedAt().isAfter(Instant.now().minus(Duration.ofHours(24)))) {
            return;
        }
        Map<String, Object> refreshed = call("Instagram (rafraîchissement du jeton)", () -> http.get()
                .uri("https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token={t}",
                        account.getAccessToken())
                .retrieve().body(JSON));
        long expiresIn = num(refreshed, "expires_in", Duration.ofDays(60).toSeconds());
        account.updateTokens(str(refreshed, "access_token"), Instant.now().plusSeconds(expiresIn), null, null);
        log.info("Jeton Instagram rafraîchi");
    }

    // ---------------------------------------------------------- Publication

    @Override
    public Result publish(Post post, SocialAccount account) {
        if (!props.publicUrlIsReachable()) {
            throw new SocialApiException("Instagram doit télécharger les médias depuis Internet : "
                    + "renseignez PUBLIC_BASE_URL avec l'URL https de votre tunnel (voir README).");
        }
        String igUserId = account.getExternalUserId();
        String token = account.getAccessToken();
        List<MediaFile> media = post.getMedia();
        String caption = post.getCaption();

        String creationId;
        if (media.size() == 1) {
            MediaFile m = media.get(0);
            MultiValueMap<String, String> params = mediaParams(m, false);
            params.add("caption", caption);
            creationId = createContainer(igUserId, token, params);
            waitUntilReady(creationId, token);
        } else {
            List<String> children = new ArrayList<>();
            for (MediaFile m : media) {
                String childId = createContainer(igUserId, token, mediaParams(m, true));
                waitUntilReady(childId, token);
                children.add(childId);
            }
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("media_type", "CAROUSEL");
            params.add("children", String.join(",", children));
            params.add("caption", caption);
            creationId = createContainer(igUserId, token, params);
            waitUntilReady(creationId, token);
        }

        MultiValueMap<String, String> publishForm = new LinkedMultiValueMap<>();
        publishForm.add("creation_id", creationId);
        publishForm.add("access_token", token);
        Map<String, Object> published = call("Instagram (publication)", () -> http.post()
                .uri(graph() + "/" + igUserId + "/media_publish")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(publishForm)
                .retrieve().body(JSON));
        String mediaId = str(published, "id");

        String permalink = null;
        try {
            Map<String, Object> info = http.get()
                    .uri(graph() + "/" + mediaId + "?fields=permalink&access_token={t}", token)
                    .retrieve().body(JSON);
            permalink = str(info, "permalink");
        } catch (RuntimeException e) {
            log.debug("Permalien indisponible : {}", e.getMessage());
        }
        return new Result(PostPublication.Status.SUCCESS, mediaId, permalink != null ? permalink : "Publié sur Instagram");
    }

    private MultiValueMap<String, String> mediaParams(MediaFile m, boolean carouselItem) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        String url = mediaService.publicUrl(m);
        if (m.isVideo()) {
            params.add("media_type", carouselItem ? "VIDEO" : "REELS");
            params.add("video_url", url);
        } else {
            params.add("image_url", url);
        }
        if (carouselItem) params.add("is_carousel_item", "true");
        return params;
    }

    private String createContainer(String igUserId, String token, MultiValueMap<String, String> params) {
        params.add("access_token", token);
        Map<String, Object> res = call("Instagram (préparation du média)", () -> http.post()
                .uri(graph() + "/" + igUserId + "/media")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve().body(JSON));
        String id = str(res, "id");
        if (id == null) throw new SocialApiException("Instagram : conteneur non créé " + res);
        return id;
    }

    /** Les vidéos sont traitées en asynchrone par Instagram : on attend le statut FINISHED. */
    private void waitUntilReady(String containerId, String token) {
        Instant deadline = Instant.now().plus(MEDIA_PROCESSING_TIMEOUT);
        while (true) {
            Map<String, Object> res = call("Instagram (statut du média)", () -> http.get()
                    .uri(graph() + "/" + containerId + "?fields=status_code,status&access_token={t}", token)
                    .retrieve().body(JSON));
            String code = str(res, "status_code");
            if ("FINISHED".equals(code) || "PUBLISHED".equals(code)) return;
            if ("ERROR".equals(code) || "EXPIRED".equals(code)) {
                throw new SocialApiException("Instagram a refusé le média : " + str(res, "status")
                        + " (vérifiez le format : JPEG, ratio entre 4:5 et 1.91:1, vidéo MP4 H.264)");
            }
            if (Instant.now().isAfter(deadline)) {
                throw new SocialApiException("Instagram : délai de traitement du média dépassé");
            }
            sleep(5000);
        }
    }
}
