package art.pilipala.api.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * TOS V4 签名的确定性向量。
 *
 * <p>worker 模块有一份同名的 {@code TosV4SignerTest}，用 **同一组向量**：
 * 两边的密钥派生逻辑必须完全一致（两个模块不共用代码，只能靠向量对齐）。
 */
class TosV4SignerTest {

    private static final String SECRET_KEY = "pilipala-local-sk";
    private static final String REGION = "cn-beijing";
    private static final String DATE = "20261024";

    @Test
    void derivedSigningKeyMatchesTheSharedVector() {
        assertThat(TosV4Signer.hex(TosV4Signer.deriveSigningKey(SECRET_KEY, DATE, REGION)))
                .isEqualTo("b15386b40b371aaf262cb95a45ffb5e6f6aa469db8a9671212648e385473fe81");
    }

    @Test
    void sha256HexMatchesKnownVectors() {
        assertThat(TosV4Signer.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TosV4Signer.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void hmacHexMatchesRfc4231Vector() {
        // RFC 4231 测试用例 1：key = 0x0b×20，data = "Hi There"
        byte[] key = new byte[20];
        java.util.Arrays.fill(key, (byte) 0x0b);

        assertThat(TosV4Signer.hmacHex(key, "Hi There"))
                .isEqualTo("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
    }

    @Test
    void basicUtcIsIso8601WithoutSeparators() {
        assertThat(TosV4Signer.formatBasicUtc(Instant.parse("2026-10-24T04:30:00Z")))
                .isEqualTo("20261024T043000Z");
        assertThat(TosV4Signer.yyyymmdd("20261024T043000Z")).isEqualTo("20261024");
    }

    @Test
    void credentialScopeAndStringToSignFollowTheFixedLayout() {
        assertThat(TosV4Signer.credentialScope("20261024", "cn-beijing"))
                .isEqualTo("20261024/cn-beijing/tos/request");

        String stringToSign = TosV4Signer.stringToSign("20261024T043000Z", "cn-beijing", "GET\n/\n\nhost:h\n\nhost\nUNSIGNED-PAYLOAD");
        assertThat(stringToSign).startsWith("TOS4-HMAC-SHA256\n20261024T043000Z\n20261024/cn-beijing/tos/request\n");
        assertThat(stringToSign.split("\n")).hasSize(4);
    }

    @Test
    void uriEncodeKeepsUnreservedAndEncodesTheRest() {
        assertThat(TosV4Signer.uriEncode("aA0-_.~")).isEqualTo("aA0-_.~");
        assertThat(TosV4Signer.uriEncode("a/b")).isEqualTo("a%2Fb");
        assertThat(TosV4Signer.uriEncode("20261024/cn-beijing/tos/request"))
                .isEqualTo("20261024%2Fcn-beijing%2Ftos%2Frequest");
        assertThat(TosV4Signer.uriEncodePath("anytoken/img/x/1.png"))
                .isEqualTo("anytoken/img/x/1.png");
    }
}
