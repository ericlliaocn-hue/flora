package art.pilipala.api.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

/**
 * 预签名读地址：URL 形状、确定性向量、非法键拒绝，以及「拿签出来的 URL 真能取回字节」。
 *
 * <p>端到端用 JDK 内置 {@link HttpServer} 起本地 TOS 桩：服务端会 **重新计算**
 * Query 预签名并比对，签名对不上就 403。
 */
class TosUrlSignerTest {

    private static final String ACCESS_KEY = "flora-local-ak";
    private static final String SECRET_KEY = "flora-local-sk";
    private static final String REGION = "cn-beijing";
    private static final String BUCKET = "flora-dev";
    private static final String ENDPOINT = "http://127.0.0.1:18080";
    private static final String OBJECT_KEY = "anytoken/img/11111111-2222-3333-4444-555555555555/1.png";
    private static final Instant SIGNING_TIME = Instant.parse("2026-10-24T04:30:00Z");

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02};

    @Test
    void signedUrlMatchesTheDeterministicVector() {
        String url = signer(ENDPOINT).signGetUrl(OBJECT_KEY, SIGNING_TIME);

        assertThat(url).isEqualTo("http://127.0.0.1:18080/flora-dev/anytoken/img/11111111-2222-3333-4444-555555555555/1.png"
                + "?X-Tos-Algorithm=TOS4-HMAC-SHA256"
                + "&X-Tos-Credential=flora-local-ak%2F20261024%2Fcn-beijing%2Ftos%2Frequest"
                + "&X-Tos-Date=20261024T043000Z"
                + "&X-Tos-Expires=900"
                + "&X-Tos-SignedHeaders=host"
                + "&X-Tos-Signature=0398925e6d7404143b6579e6dcddfb43bf26e198a905edfca9ede0a06d38fe4f");
    }

    @Test
    void signedUrlCarriesEveryRequiredQueryParameter() {
        String url = signer(ENDPOINT).signGetUrl(OBJECT_KEY, SIGNING_TIME);

        assertThat(url).contains("X-Tos-Algorithm=TOS4-HMAC-SHA256");
        assertThat(url).contains("X-Tos-Credential=");
        assertThat(url).contains("X-Tos-Date=20261024T043000Z");
        assertThat(url).contains("X-Tos-Expires=900");
        assertThat(url).contains("X-Tos-SignedHeaders=host");
        assertThat(url).contains("X-Tos-Signature=");
        assertThat(url).doesNotContain(SECRET_KEY);
    }

    @Test
    void leadingSlashIsNormalizedAway() {
        assertThat(signer(ENDPOINT).signGetUrl("/" + OBJECT_KEY, SIGNING_TIME))
                .isEqualTo(signer(ENDPOINT).signGetUrl(OBJECT_KEY, SIGNING_TIME));
    }

    /** 真实火山域名强制 Virtual-Host Style：路径风格会被网关以 403 InvalidPathAccess 拒绝。 */
    @Test
    void virtualHostStyleUsedForRealVolcesDomain() {
        String virtualHost = "flora-media-test.tos-cn-guangzhou.volces.com";
        String canonicalUri = "/anytoken/img/11111111-2222-3333-4444-555555555555/1.png";
        String url = new TosUrlSigner(new TosProperties("http", "https://tos-cn-guangzhou.volces.com", "cn-guangzhou",
                "flora-media-test", ACCESS_KEY, SECRET_KEY, 900, 15000, "/tmp/flora-tos"))
                .signGetUrl(OBJECT_KEY, SIGNING_TIME);

        assertThat(url).startsWith("https://" + virtualHost + canonicalUri + "?");
        assertThat(url).doesNotContain("/flora-media-test/anytoken/");

        // 签名按 virtual host + 桶名无关的 canonicalUri 独立重算，必须逐字一致。
        Map<String, String> query = new TreeMap<>();
        query.put("X-Tos-Algorithm", TosV4Signer.ALGORITHM);
        query.put("X-Tos-Credential", ACCESS_KEY + "/20261024/cn-guangzhou/tos/request");
        query.put("X-Tos-Date", "20261024T043000Z");
        query.put("X-Tos-Expires", "900");
        query.put("X-Tos-SignedHeaders", "host");
        String canonicalQueryString = query.entrySet().stream()
                .map(entry -> TosV4Signer.uriEncode(entry.getKey()) + "=" + TosV4Signer.uriEncode(entry.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        String canonicalRequest = "GET\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + "host:" + virtualHost + "\n"
                + "\n"
                + "host\n"
                + TosV4Signer.UNSIGNED_PAYLOAD;
        String expected = TosV4Signer.signature(SECRET_KEY, "cn-guangzhou", "20261024",
                TosV4Signer.stringToSign("20261024T043000Z", "cn-guangzhou", canonicalRequest));

        assertThat(url).endsWith("&X-Tos-Signature=" + expected);
    }

    /** endpoint 已经是虚拟主机域名时不再重复拼桶名前缀。 */
    @Test
    void virtualHostPrefixIsNotDuplicated() {
        String url = new TosUrlSigner(new TosProperties("http", "https://flora-media-test.tos-cn-guangzhou.volces.com",
                "cn-guangzhou", "flora-media-test", ACCESS_KEY, SECRET_KEY, 900, 15000, "/tmp/flora-tos"))
                .signGetUrl(OBJECT_KEY, SIGNING_TIME);

        assertThat(url).startsWith("https://flora-media-test.tos-cn-guangzhou.volces.com/anytoken/img/");
        assertThat(url).doesNotContain("flora-media-test.flora-media-test");
    }

    @Test
    void emptyOrTraversalKeysAreRejected() {
        TosUrlSigner signer = signer(ENDPOINT);

        assertThatThrownBy(() -> signer.signGetUrl("", SIGNING_TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.signGetUrl("   ", SIGNING_TIME))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.signGetUrl("anytoken/../../etc/passwd", SIGNING_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidConfigurationFailsFast() {
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("stub", "", REGION, BUCKET, ACCESS_KEY, SECRET_KEY, 900, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("stub", ENDPOINT, "", BUCKET, ACCESS_KEY, SECRET_KEY, 900, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("stub", ENDPOINT, REGION, "", ACCESS_KEY, SECRET_KEY, 900, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("stub", ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, 59, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("stub", ENDPOINT, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, 86401, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
        // http 模式必须有密钥。
        assertThatThrownBy(() -> new TosUrlSigner(new TosProperties("http", ENDPOINT, REGION, BUCKET, "", "", 900, 15000, "/tmp")))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 端到端：拿签出来的 URL 去取字节，服务端会重新验签。 */
    @Test
    void theSignedUrlFetchesTheSameBytesBack() throws Exception {
        HttpServer server = server();
        try {
            String url = signer("http://127.0.0.1:" + server.getAddress().getPort())
                    .signGetUrl(OBJECT_KEY, Instant.now());

            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(PNG_BYTES);
        } finally {
            server.stop(0);
        }
    }

    /** 篡改过的签名必须被服务端拒绝。 */
    @Test
    void aTamperedSignatureIsRejected() throws Exception {
        HttpServer server = server();
        try {
            String url = signer("http://127.0.0.1:" + server.getAddress().getPort())
                    .signGetUrl(OBJECT_KEY, Instant.now());
            int index = url.indexOf("X-Tos-Signature=") + "X-Tos-Signature=".length();
            char flipped = url.charAt(index) == 'a' ? 'b' : 'a';
            String tampered = url.substring(0, index) + flipped + url.substring(index + 1);

            HttpResponse<byte[]> response = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder(URI.create(tampered)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

            assertThat(response.statusCode()).isEqualTo(403);
        } finally {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------- 工具

    private TosUrlSigner signer(String endpoint) {
        return new TosUrlSigner(new TosProperties("stub", endpoint, REGION, BUCKET, ACCESS_KEY, SECRET_KEY, 900, 15000,
                "/tmp/flora-tos"));
    }

    /** 本地 TOS 桩：校验 Query 预签名，通过才返回对象字节。 */
    private HttpServer server() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String rawQuery = exchange.getRequestURI().getRawQuery();
            Map<String, String> params = parseQuery(rawQuery);
            String signature = params.get("X-Tos-Signature");
            String iso = params.get("X-Tos-Date");
            String expected = expectedSignature(exchange.getRequestURI().getRawPath(), rawQuery, iso,
                    exchange.getRequestHeaders().getFirst("Host"));
            if (signature == null || !signature.equals(expected)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
            byte[] bytes = PNG_BYTES;
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return server;
    }

    /** 服务端独立重算一遍签名（与客户端的规范请求拼装一致）。 */
    private String expectedSignature(String rawPath, String rawQuery, String iso, String host) {
        Map<String, String> params = parseQuery(rawQuery);
        Map<String, String> sorted = new TreeMap<>(params);
        sorted.remove("X-Tos-Signature");
        String canonicalQuery = sorted.entrySet().stream()
                .map(entry -> TosV4Signer.uriEncode(entry.getKey()) + "=" + TosV4Signer.uriEncode(entry.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        String canonicalRequest = "GET\n"
                + rawPath + "\n"
                + canonicalQuery + "\n"
                + "host:" + host + "\n"
                + "\n"
                + "host\n"
                + TosV4Signer.UNSIGNED_PAYLOAD;
        String stringToSign = TosV4Signer.stringToSign(iso, REGION, canonicalRequest);
        return TosV4Signer.signature(SECRET_KEY, REGION, TosV4Signer.yyyymmdd(iso), stringToSign);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            if (index < 0) {
                params.put(decode(pair), "");
            } else {
                params.put(decode(pair.substring(0, index)), decode(pair.substring(index + 1)));
            }
        }
        return params;
    }

    private String decode(String raw) {
        return java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
    }
}
