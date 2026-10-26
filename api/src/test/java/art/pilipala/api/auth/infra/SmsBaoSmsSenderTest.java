package art.pilipala.api.auth.infra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmsBaoSmsSenderTest {

    @Test
    void stripsE164ForSmsBao() {
        assertThat(SmsBaoSmsSender.toSmsBaoMobile("+8613800138000")).isEqualTo("13800138000");
        assertThat(SmsBaoSmsSender.toSmsBaoMobile("13800138000")).isEqualTo("13800138000");
    }
}
