package fr.handcom.post;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.handcom.media.MediaFile;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostValidationTest {

    private static MediaFile media(MediaFile.Kind kind) {
        MediaFile m = new MediaFile();
        m.setKind(kind);
        return m;
    }

    @Test
    void instagramAccepteUneImageOuUnCarrousel() {
        assertThatCode(() -> PostService.validate(List.of(media(MediaFile.Kind.IMAGE)), Set.of(Platform.INSTAGRAM)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PostService.validate(
                Collections.nCopies(10, media(MediaFile.Kind.IMAGE)), Set.of(Platform.INSTAGRAM)))
                .doesNotThrowAnyException();
    }

    @Test
    void instagramRefusePlusDeDixMedias() {
        assertThatThrownBy(() -> PostService.validate(
                Collections.nCopies(11, media(MediaFile.Kind.IMAGE)), Set.of(Platform.INSTAGRAM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tiktokExigeExactementUneVideo() {
        assertThatCode(() -> PostService.validate(List.of(media(MediaFile.Kind.VIDEO)), Set.of(Platform.TIKTOK, Platform.INSTAGRAM)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> PostService.validate(List.of(media(MediaFile.Kind.IMAGE)), Set.of(Platform.TIKTOK)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostService.validate(
                List.of(media(MediaFile.Kind.VIDEO), media(MediaFile.Kind.VIDEO)), Set.of(Platform.TIKTOK)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
