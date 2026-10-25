package art.pilipala.worker.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * TOS V4 签名的确定性向量。
 *
 * <p>api 模块有一份同名的 {@code TosV4SignerTest}，用 **同一组向量**：
 * 两边的密钥派生逻辑必须完全一致（两个模块不共用代码，只能靠向量对齐）。
 */
class TosV4SignerTest {

    private static final String ACCESS_KEY = "flora-local-ak";
    private static final String SECRET_KEY = "flora-local-sk";
    private static final String REGION = "cn-beijing";
    private static final String DATE = "20261024";

    @Test
    void derivedSigningKeyMatchesTheSharedVector() {
        assertThat(TosV4Signer.hex(TosV4Signer.deriveSigningKey(SECRET_KEY, DATE, REGION)))
                .isEqualTo("8de685c0a1d681a8c98a1ed0c361b45d0012ec2942a638c1bc9ac68aff490a5e");
    }

    @Test
    void sha256HexMatchesKnownVectors() {
        assertThat(TosV4Signer.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TosV4Signer.sha256Hex(new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void minimalPngIsAValid67BytePng() {
        assertThat(MinimalPng.BYTES).hasSize(67);
        assertThat(MinimalPng.BYTES[0]).isEqualTo((byte) 0x89);
        assertThat(MinimalPng.BYTES[1]).isEqualTo((byte) 0x50);
        assertThat(MinimalPng.BYTES[2]).isEqualTo((byte) 0x4e);
        assertThat(MinimalPng.BYTES[3]).isEqualTo((byte) 0x47);
        assertThat(TosV4Signer.sha256Hex(MinimalPng.BYTES))
                .isEqualTo("6df39ea1346f410f5c71e6e08057096453fef66a65badd519dd6a7aab0c2d959");
    }

    @Test
    void putAuthorizationMatchesTheDeterministicVector() {
        String authorization = TosV4Signer.authorization(ACCESS_KEY, SECRET_KEY, REGION,
                "/flora-dev/anytoken/img/x/1.png", "127.0.0.1:18080", "image/png",
                TosV4Signer.sha256Hex(MinimalPng.BYTES), Instant.parse("2026-10-24T04:30:00Z"));

        assertThat(authorization).isEqualTo("TOS4-HMAC-SHA256 "
                + "Credential=flora-local-ak/20261024/cn-beijing/tos/request, "
                + "SignedHeaders=content-type;host;x-tos-content-sha256;x-tos-date, "
                + "Signature=047f56261cf45379d5131ab7d0641d6149ef77f8312267db9f24b0267fbde50f");
    }

    @Test
    void credentialScopeAndStringToSignFollowTheFixedLayout() {
        assertThat(TosV4Signer.credentialScope("20261024", "cn-beijing"))
                .isEqualTo("20261024/cn-beijing/tos/request");

        String stringToSign = TosV4Signer.stringToSign("20261024T043000Z", "cn-beijing", "PUT\n/\n\nhost:h\n\nhost\nsha");
        assertThat(stringToSign).startsWith("TOS4-HMAC-SHA256\n20261024T043000Z\n20261024/cn-beijing/tos/request\n");
        assertThat(stringToSign.split("\n")).hasSize(4);
    }

    @Test
    void uriEncodeKeepsUnreservedAndEncodesTheRest() {
        assertThat(TosV4Signer.uriEncode("aA0-_.~")).isEqualTo("aA0-_.~");
        assertThat(TosV4Signer.uriEncode("a/b")).isEqualTo("a%2Fb");
        assertThat(TosV4Signer.uriEncodePath("anytoken/img/x/1.png")).isEqualTo("anytoken/img/x/1.png");
    }
}
