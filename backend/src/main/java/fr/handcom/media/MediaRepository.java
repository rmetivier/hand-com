package fr.handcom.media;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaRepository extends JpaRepository<MediaFile, Long> {
}
