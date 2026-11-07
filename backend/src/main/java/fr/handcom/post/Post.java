package fr.handcom.post;

import fr.handcom.media.MediaFile;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "text")
    private String caption;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "post_media",
            joinColumns = @JoinColumn(name = "post_id"),
            inverseJoinColumns = @JoinColumn(name = "media_id"))
    @OrderColumn(name = "sort_order")
    @Fetch(FetchMode.SUBSELECT)
    private List<MediaFile> media = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_platforms", joinColumns = @JoinColumn(name = "post_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    @Fetch(FetchMode.SUBSELECT)
    private Set<Platform> platforms = new HashSet<>();

    /** Null = brouillon. */
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status = PostStatus.DRAFT;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("attemptedAt ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<PostPublication> publications = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addPublication(PostPublication publication) {
        publication.setPost(this);
        publications.add(publication);
    }

    /** Dernière tentative pour un réseau donné. */
    public Optional<PostPublication> lastPublication(Platform platform) {
        PostPublication last = null;
        for (PostPublication p : publications) {
            if (p.getPlatform() == platform) last = p;
        }
        return Optional.ofNullable(last);
    }

    /** Vrai si ce réseau a déjà reçu le post avec succès (on ne republie pas lors d'un nouvel essai). */
    public boolean alreadyDoneOn(Platform platform) {
        return lastPublication(platform).map(p -> p.getStatus().ok()).orElse(false);
    }

    public Long getId() { return id; }
    public String getCaption() { return caption; }
    public void setCaption(String caption) { this.caption = caption; }
    public List<MediaFile> getMedia() { return media; }
    public Set<Platform> getPlatforms() { return platforms; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }
    public List<PostPublication> getPublications() { return publications; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
