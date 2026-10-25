package art.pilipala.api.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 密码哈希：必须是 BCrypt，明文不落库。
 */
class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashesWithBcryptAndNeverKeepsPlaintext() {
        String hash = hasher.hash("password1");
        assertThat(hash).isNotEqualTo("password1");
        assertThat(hash).startsWith("$2");
    }

    @Test
    void matchesOnlyTheOriginalPassword() {
        String hash = hasher.hash("password1");
        assertThat(hasher.matches("password1", hash)).isTrue();
        assertThat(hasher.matches("password2", hash)).isFalse();
    }

    @Test
    void equalizerNeverMatchesButStillCostsHashing() {
        String hash = hasher.hash("password1");
        assertThat(hasher.matches("password1", hash)).isTrue();
        hasher.matchEqualizer("password1");
    }
}
