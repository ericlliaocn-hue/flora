package com.flora.worker.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TOS 写入：stub 落盘与 http V4 签名 PUT。
 *
 * <p>http 模式用 JDK 内置的 {@link HttpServer} 起本地桶：服务端会 **重新计算**
 * TOS4-HMAC-SHA256 头签名并比对，对不上就 403。
 */
class TosObjectStorageAdapterTest {

    private static final String ACCESS_KEY = "flora-local-ak";
    private static final String SECRET_KEY = "flora-local-sk";
    private static final String REGION = "cn-beijing";
    private static final String BUCKET = "flora-dev";
    private static final String OBJECT_KEY = "anytoken/img/x/1.png";

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------- stub 模式

    @Test
    void stubModeWritesTheObjectUnderLocalDirBucketKey() throws IOException {
        TosObjectStorageAdapter adapter = stubAdapter(tempDir);

        adapter.putObject(OBJECT_KEY, MinimalPng.BYTES, "image/png");

        Path target = tempDir.resolve(BUCKET).resolve(OBJECT_KEY);
        assertThat(target).exists();
        assertThat(Files.readAllBytes(target)).isEqualTo(MinimalPng.BYTES);
        assertThat(Files.readAllBytes(target)[0]).isEqualTo((byte) 0x89);
    }

    @Test
    void stubModeNormalizesTheLeadingSlash() throws IOException {
        TosObjectStorageAdapter adapter = stubAdapter(tempDir);

        adapter.putObject("/" + OBJECT_KEY, MinimalPng.BYTES, "image/png");

        assertThat(tempDir.resolve(BUCKET).resolve(OBJECT_KEY)).exists();
    }

    @Test
    void pathTraversalIsRejected() {
        TosObjectStorageAdapter adapter = stubAdapter(tempDir);

        assertThatThrownBy(() -> adapter.putObject("anytoken/../../etc/passwd", MinimalPng.BYTES, "image/png"))
                .isInstanceOf(TosStorageException.class)
                .hasMessageContaining("..");
        assertThat(tempDir.resolve("etc")).doesNotExist();
    }

    @Test
    void emptyContentIsRejected() {
        TosObjectStorageAdapter adapter = stubAdapter(tempDir);

        assertThatThrownBy(() -> adapter.putObject(OBJECT_KEY, new byte[0], "image/png"))
                .isInstanceOf(TosStorageException.class);
        assertThatThrownBy(() -> adapter.putObject(OBJECT_KEY, null, "image/png"))
                .isInstanceOf(TosStorageException.class);
    }

    @Test
    void emptyKeyIsRejected() {
        TosObjectStorageAdapter adapter = stubAdapter(tempDir);

        assertThatThrownBy(() -> adapter.putObject("  ", MinimalPng.BYTES, "image/png"))
                .isInstanceOf(TosStorageException.class);
    }

    // ------------------------------------------------------------- http 模式

    @Test
    void httpModePutsWithAV4SignatureAndTheServerAcceptsIt() throws IOException {
        Map<String, byte[]> stored = new ConcurrentHashMap<>();
        Map<String, String> headers = new ConcurrentHashMap<>();
        HttpServer server = server(stored, headers);
        try {
            TosObjectStorageAdapter adapter = httpAdapter(server);

            adapter.putObject(OBJECT_KEY, MinimalPng.BYTES, "image/png");

            assertThat(stored).containsKey("/" + BUCKET + "/" + OBJECT_KEY);
            assertThat(stored.get("/" + BUCKET + "/" + OBJECT_KEY)).isEqualTo(MinimalPng.BYTES);
            assertThat(headers.get("Authorization")).startsWith("TOS4-HMAC-SHA256 Credential=" + ACCESS_KEY + "/");
            assertThat(headers.get("Authorization")).contains("SignedHeaders=content-type;host;x-tos-content-sha256;x-tos-date");
            assertThat(headers.get("X-Tos-Date")).matches("\\d{8}T\\d{6}Z");
            assertThat(headers.get("X-Tos-Content-Sha256")).isEqualTo(TosV4Signer.sha256Hex(MinimalPng.BYTES));
            assertThat(headers.get("Content-Type")).isEqualTo("image/png");
            // 密钥不出现在任何请求头之外的地方。
            assertThat(headers.get("Authorization")).doesNotContain(SECRET_KEY);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpModeRaisesOnNon2xx() throws IOException {
        HttpServer server = server(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
        try {
            // 换一个路径，让服务端返回 404（模拟 PUT 被拒）。
            TosObjectStorageAdapter adapter = httpAdapter(server);
            assertThatThrownBy(() -> adapter.putObject("missing/../x.png", MinimalPng.BYTES, "image/png"))
                    .isInstanceOf(TosStorageException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpModeRaisesWhenTheServerIsUnreachable() {
        TosObjectStorageAdapter adapter = new TosObjectStorageAdapter(new TosProperties("http",
                "http://127.0.0.1:1", REGION, BUCKET, ACCESS_KEY, SECRET_KEY, 900, 200, tempDir.toString()));

        assertThatThrownBy(() -> adapter.putObject(OBJECT_KEY, MinimalPng.BYTES, "image/png"))
                .isInstanceOf(TosStorageException.class)
                .hasMessageContaining("TOS");
    }

    @Test
    void httpModeRequiresCredentials() {
        assertThatThrownBy(() -> new TosObjectStorageAdapter(new TosProperties("http", "http://127.0.0.1:18080",
                REGION, BUCKET, "", "", 900, 15000, tempDir.toString())))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------------------------------------------------------------- 工具

    private TosObjectStorageAdapter stubAdapter(Path localDir) {
        return new TosObjectStorageAdapter(new TosProperties("stub", "http://127.0.0.1:18080", REGION, BUCKET,
                ACCESS_KEY, SECRET_KEY, 900, 15000, localDir.toString()));
    }

    private TosObjectStorageAdapter httpAdapter(HttpServer server) {
        return new TosObjectStorageAdapter(new TosProperties("http",
                "http://127.0.0.1:" + server.getAddress().getPort(), REGION, BUCKET,
                ACCESS_KEY, SECRET_KEY, 900, 5000, tempDir.toString()));
    }

    /** 本地桶：校验 TOS4-HMAC-SHA256 头签名，通过才收下对象。 */
    private HttpServer server(Map<String, byte[]> stored, Map<String, String> headers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            headers.put("Authorization", authorization);
            headers.put("X-Tos-Date", exchange.getRequestHeaders().getFirst("X-Tos-Date"));
            headers.put("X-Tos-Content-Sha256", exchange.getRequestHeaders().getFirst("X-Tos-Content-Sha256"));
            headers.put("Content-Type", exchange.getRequestHeaders().getFirst("Content-Type"));

            String expected = expectedAuthorization(exchange, body);
            if (expected == null || !expected.equals(authorization)) {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
                return;
            }
            stored.put(exchange.getRequestURI().getRawPath(), body);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    /** 服务端独立重算一遍 PUT 头签名。 */
    private String expectedAuthorization(HttpExchange exchange, byte[] body) {
        String iso = exchange.getRequestHeaders().getFirst("X-Tos-Date");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String contentSha256 = TosV4Signer.sha256Hex(body);
        String host = exchange.getRequestHeaders().getFirst("Host");
        String canonicalUri = exchange.getRequestURI().getRawPath();
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-tos-content-sha256:" + contentSha256 + "\n"
                + "x-tos-date:" + iso + "\n";
        String signedHeaders = "content-type;host;x-tos-content-sha256;x-tos-date";
        String canonicalRequest = "PUT\n"
                + canonicalUri + "\n"
                + "" + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + contentSha256;
        String stringToSign = TosV4Signer.stringToSign(iso, REGION, canonicalRequest);
        String signature = TosV4Signer.signature(SECRET_KEY, REGION, TosV4Signer.yyyymmdd(iso), stringToSign);
        return "TOS4-HMAC-SHA256 Credential=" + ACCESS_KEY + "/"
                + TosV4Signer.credentialScope(TosV4Signer.yyyymmdd(iso), REGION)
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    private void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        try (OutputStream out = exchange.getResponseBody()) {
            out.flush();
        }
    }
}
