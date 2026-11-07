package fr.handcom.social;

import static org.assertj.core.api.Assertions.assertThat;

import fr.handcom.post.Platform;
import org.junit.jupiter.api.Test;

class OAuthStateStoreTest {

    @Test
    void unStateNeSertQuUneFoisEtPourLeBonReseau() {
        OAuthStateStore store = new OAuthStateStore();
        String state = store.create(Platform.INSTAGRAM);
        assertThat(store.consume(state, Platform.TIKTOK)).isFalse();

        String other = store.create(Platform.INSTAGRAM);
        assertThat(store.consume(other, Platform.INSTAGRAM)).isTrue();
        assertThat(store.consume(other, Platform.INSTAGRAM)).isFalse();
        assertThat(store.consume(null, Platform.INSTAGRAM)).isFalse();
    }
}
