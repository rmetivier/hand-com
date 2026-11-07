package fr.handcom.social;

import fr.handcom.post.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Un compte connecté par réseau (Instagram du club, TikTok du club). */
@Entity
@Table(name = "social_accounts")
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Platform platform;

    /** Instagram : user_id du compte pro. TikTok : open_id. */
    private String externalUserId;

    private String username;

    @Column(columnDefinition = "text", nullable = false)
    private String accessToken;

    @Column(columnDefinition = "text")
    private String refreshToken;

    private Instant accessExpiresAt;
    private Instant refreshExpiresAt;
    /** Date d'obtention du jeton actuel. */
    private Instant tokenIssuedAt;
    private Instant connectedAt;

    protected SocialAccount() {
    }

    public SocialAccount(Platform platform) {
        this.platform = platform;
        this.connectedAt = Instant.now();
    }

    public void updateTokens(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt) {
        this.accessToken = accessToken;
        this.accessExpiresAt = accessExpiresAt;
        if (refreshToken != null) this.refreshToken = refreshToken;
        if (refreshExpiresAt != null) this.refreshExpiresAt = refreshExpiresAt;
        this.tokenIssuedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Platform getPlatform() { return platform; }
    public String getExternalUserId() { return externalUserId; }
    public void setExternalUserId(String externalUserId) { this.externalUserId = externalUserId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getAccessExpiresAt() { return accessExpiresAt; }
    public Instant getRefreshExpiresAt() { return refreshExpiresAt; }
    public Instant getTokenIssuedAt() { return tokenIssuedAt; }
    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
}
