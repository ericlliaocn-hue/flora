package art.pilipala.api.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityNormalizerTest {

    @Test
    void normalizesChinaMobile() {
        assertThat(IdentityNormalizer.normalizePhone("13800138000")).isEqualTo("+8613800138000");
        assertThat(IdentityNormalizer.normalizePhone("+8613800138000")).isEqualTo("+8613800138000");
    }

    @Test
    void normalizesEmail() {
        assertThat(IdentityNormalizer.normalizeEmail("  User@Example.COM ")).isEqualTo("user@example.com");
    }
}
