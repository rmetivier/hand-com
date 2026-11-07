package fr.handcom.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Une tentative de publication d'un post sur un réseau. */
@Entity
@Table(name = "post_publications")
public class PostPublication {

    public enum Status {
        SUCCESS,
        /** TikTok : vidéo envoyée dans la boîte de réception de l'app, à valider sur le téléphone. */
        DRAFT_SENT,
        FAILED;

        public boolean ok() {
            return this != FAILED;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String externalId;

    @Column(columnDefinition = "text")
    private String message;

    private Instant attemptedAt;

    protected PostPublication() {
    }

    public PostPublication(Platform platform, Status status, String externalId, String message) {
        this.platform = platform;
        this.status = status;
        this.externalId = externalId;
        this.message = message;
        this.attemptedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    void setPost(Post post) { this.post = post; }
    public Platform getPlatform() { return platform; }
    public Status getStatus() { return status; }
    public String getExternalId() { return externalId; }
    public String getMessage() { return message; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
