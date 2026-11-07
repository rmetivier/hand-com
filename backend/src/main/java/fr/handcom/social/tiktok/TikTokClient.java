package fr.handcom.social.tiktok;

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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Intégration TikTok Content Posting API, mode « brouillon » (inbox) :
 * la vidéo arrive dans les notifications de l'app TikTok et vous la publiez d'un tap.
 * Ce mode fonctionne sans audit de l'app par TikTok (contrairement à la publication directe).
 */
@Component
public class TikTokClient implements SocialPublisher {

    private static final String API = "https://open.tiktokapis.com/v2";
    private static final String SCOPES = "user.info.basic,video.upload";
    private static final long MB = 1024 * 1024;
    /** En dessous de cette taille, la vidéo est envoyée en un seul morceau. */
    private static final long SINGLE_CHUNK_MAX = 64 * MB;
    private static final long CHUNK_SIZE = 10 * MB;

    private final RestClient http = RestClient.create();
    private final AppProperties props;
    private final MediaService mediaService;

    public TikTokClient(AppProperties props, MediaService mediaService) {
        this.props = props;
        this.mediaService = mediaService;
    }

    @Override
    public Platform platform() {
        return Platform.TIKTOK;
    }

    @Override
    public boolean configured() {
        return props.tiktok().configured();
    }

    private String redirectUri() {
        return props.publicBase() + "/api/oauth/tiktok/callback";
    }

    // ---------------------------------------------------------------- OAuth

    @Override
    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://www.tiktok.com/v2/auth/authorize/")
                .queryParam("client_key", props.tiktok().clientKey())
                .queryParam("scope", SCOPES)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    @Override
    public void completeAuthorization(String code, SocialAccount account) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", props.tiktok().clientKey());
        form.add("client_secret", props.tiktok().clientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri());
        Map<String, Object> tokens = tokenRequest("TikTok (échange du code)", form);
        applyTokens(account, tokens);
        account.setExternalUserId(str(tokens, "open_id"));

        Map<String, Object> info = call("TikTok (profil)", () -> http.get()
                .uri(API + "/user/info/?fields=open_id,display_name")
                .header("Authorization", "Bearer " + account.getAccessToken())
                .retrieve().body(JSON));
        checkError(info, "TikTok (profil)");
        Map<String, Object> user = obj(obj(info, "data"), "user");
        account.setUsername(str(user, "display_name"));
    }

    @Override
    public void refreshIfNeeded(SocialAccount account) {
        // Jeton d'accès valable 24 h, jeton de rafraîchissement 365 jours.
        Instant expires = account.getAccessExpiresAt();
        if (expires != null && expires.isAfter(Instant.now().plus(Duration.ofMinutes(10)))) return;
        if (account.getRefreshExpiresAt() != null && account.getRefreshExpiresAt().isBefore(Instant.now())) {
            throw new SocialApiException("Connexion TikTok expirée : reconnectez le compte depuis la page « Comptes ».");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_key", props.tiktok().clientKey());
        form.add("client_secret", props.tiktok().clientSecret());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", account.getRefreshToken());
        applyTokens(account, tokenRequest("TikTok (rafraîchissement du jeton)", form));
    }

    private Map<String, Object> tokenRequest(String what, MultiValueMap<String, String> form) {
        Map<String, Object> res = call(what, () -> http.post()
                .uri(API + "/oauth/token/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JSON));
        String error = str(res, "error");
        if (error != null && !error.isBlank()) {
            throw new SocialApiException(what + " : " + error + " " + str(res, "error_description"));
        }
        return res;
    }

    private static void applyTokens(SocialAccount account, Map<String, Object> t) {
        Instant now = Instant.now();
        account.updateTokens(
                str(t, "access_token"), now.plusSeconds(num(t, "expires_in", 86400)),
                str(t, "refresh_token"), now.plusSeconds(num(t, "refresh_expires_in", 31536000)));
    }

    // ---------------------------------------------------------- Publication

    @Override
    public Result publish(Post post, SocialAccount account) {
        MediaFile video = post.getMedia().get(0);
        Path file = mediaService.path(video);
        long size = video.getSizeBytes();
        long chunkSize = size <= SINGLE_CHUNK_MAX ? size : CHUNK_SIZE;
        // Le dernier morceau absorbe le reste (autorisé jusqu'à 128 Mo par TikTok).
        long chunkCount = size <= SINGLE_CHUNK_MAX ? 1 : size / CHUNK_SIZE;

        // 1. Initialisation de l'envoi
        Map<String, Object> body = Map.of("source_info", Map.of(
                "source", "FILE_UPLOAD",
                "video_size", size,
                "chunk_size", chunkSize,
                "total_chunk_count", chunkCount));
        Map<String, Object> init = call("TikTok (initialisation)", () -> http.post()
                .uri(API + "/post/publish/inbox/video/init/")
                .header("Authorization", "Bearer " + account.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JSON));
        checkError(init, "TikTok (initialisation)");
        Map<String, Object> data = obj(init, "data");
        String publishId = str(data, "publish_id");
        String uploadUrl = str(data, "upload_url");

        // 2. Envoi du fichier par morceaux
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            for (long i = 0; i < chunkCount; i++) {
                long start = i * chunkSize;
                long end = (i == chunkCount - 1) ? size - 1 : start + chunkSize - 1;
                byte[] bytes = new byte[(int) (end - start + 1)];
                raf.seek(start);
                raf.readFully(bytes);
                String range = "bytes " + start + "-" + end + "/" + size;
                call("TikTok (envoi de la vidéo)", () -> http.put()
                        .uri(java.net.URI.create(uploadUrl))
                        .contentType(MediaType.parseMediaType(video.getContentType()))
                        .header("Content-Range", range)
                        .body(bytes)
                        .retrieve().toBodilessEntity());
            }
        } catch (IOException e) {
            throw new SocialApiException("Lecture de la vidéo impossible : " + e.getMessage());
        }

        // 3. Suivi du traitement par TikTok (jusqu'à ~1 min)
        for (int attempt = 0; attempt < 12; attempt++) {
            sleep(5000);
            Map<String, Object> status = call("TikTok (statut)", () -> http.post()
                    .uri(API + "/post/publish/status/fetch/")
                    .header("Authorization", "Bearer " + account.getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("publish_id", publishId))
                    .retrieve().body(JSON));
            checkError(status, "TikTok (statut)");
            Map<String, Object> s = obj(status, "data");
            String st = str(s, "status");
            if ("SEND_TO_USER_INBOX".equals(st) || "PUBLISH_COMPLETE".equals(st)) {
                return new Result(PostPublication.Status.DRAFT_SENT, publishId,
                        "Vidéo envoyée dans l'app TikTok : ouvrez la notification pour la publier (collez-y la légende).");
            }
            if ("FAILED".equals(st)) {
                throw new SocialApiException("TikTok a refusé la vidéo : " + str(s, "fail_reason"));
            }
        }
        return new Result(PostPublication.Status.DRAFT_SENT, publishId,
                "Vidéo transmise, TikTok la traite encore : elle arrivera bientôt dans les notifications de l'app.");
    }

    /** TikTok renvoie {"error": {"code": "ok"}} quand tout va bien. */
    private static void checkError(Map<String, Object> res, String what) {
        Map<String, Object> error = obj(res, "error");
        String code = str(error, "code");
        if (code != null && !"ok".equals(code)) {
            throw new SocialApiException(what + " : " + code + " " + str(error, "message"));
        }
    }
}
