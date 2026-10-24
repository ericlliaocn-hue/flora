package com.flora.worker.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.flora.worker.storage.MinimalPng;
import com.flora.worker.storage.TosObjectStorage;
import com.flora.worker.storage.TosObjectStorageAdapter;
import com.flora.worker.storage.TosProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 唯一 Anytoken 适配器：stub 与 http 两种模式。
 *
 * <p>http 模式用 JDK 内置的 {@link HttpServer} 起本地桩，零第三方依赖。
 */
class AnytokenGatewayAdapterTest {

    private static final String API_KEY = "super-secret-key";

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    java.nio.file.Path tempDir;

    // ------------------------------------------------------------- stub 模式

    @Test
    void stubSucceedsDeterministically() {
        UUID jobId = UUID.randomUUID();
        AnytokenAttemptResult result = stubAdapter().execute(request(jobId, 1, "{}"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isNull();
        assertThat(result.outputObjectKeys()).containsExactly("anytoken/img/" + jobId + "/1.png");
        assertThat(result.upstreamRequestId()).isEqualTo("stub-" + jobId + "-1");
        assertThat(result.rawStatus()).isEqualTo("200");

        JsonNode raw = readJson(result.rawBodyJson());
        assertThat(raw.get("provider").asText()).isEqualTo("anytoken");
        assertThat(raw.get("mode").asText()).isEqualTo("stub");
        assertThat(raw.get("status").asText()).isEqualTo("succeeded");
        assertThat(raw.get("model").asText()).isEqualTo("demo");
    }

    @Test
    void stubProviderUnavailableIsRetryable() {
        AnytokenAttemptResult result = stubAdapter().execute(request(UUID.randomUUID(), 1, "{\"stub_outcome\":\"provider_unavailable\"}"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.rawStatus()).isEqualTo("503");
        assertThat(readJson(result.rawBodyJson()).get("error").asText()).isEqualTo("upstream channel overloaded");
    }

    @Test
    void stubJobFailedIsNotRetryable() {
        AnytokenAttemptResult result = stubAdapter().execute(request(UUID.randomUUID(), 1, "{\"stub_outcome\":\"job_failed\"}"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("JOB_FAILED");
        assertThat(result.rawStatus()).isEqualTo("400");
    }

    /** WP8：stub 成功分支要把合法 PNG 字节写进 TOS（stub 模式落本地目录）。 */
    @Test
    void stubSuccessStoresThePngBytesInTos() throws IOException {
        UUID jobId = UUID.randomUUID();

        AnytokenAttemptResult result = stubAdapter().execute(request(jobId, 1, "{}"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.outputObjectKeys()).containsExactly("anytoken/img/" + jobId + "/1.png");
        java.nio.file.Path stored = tempDir.resolve("flora-dev").resolve("anytoken/img/" + jobId + "/1.png");
        assertThat(stored).exists();
        assertThat(java.nio.file.Files.readAllBytes(stored)).isEqualTo(MinimalPng.BYTES);
    }

    /** WP8：TOS 写失败 → 可重试的 PROVIDER_UNAVAILABLE，绝不算成功。 */
    @Test
    void stubTosUnavailableIsRetryableAndNotSuccessful() {
        AnytokenAttemptResult result = stubAdapter()
                .execute(request(UUID.randomUUID(), 1, "{\"stub_tos_outcome\":\"unavailable\"}"));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.rawStatus()).isEqualTo("TOS_503");
        assertThat(result.outputObjectKeys()).isEmpty();
        assertThat(readJson(result.rawBodyJson()).get("error").asText()).isEqualTo("tos storage unavailable");
    }

    /** WP8：http 模式的上游 Base64 图片会被解码存进 TOS，且原文不进 raw_body。 */
    @Test
    void httpBase64OutputIsDecodedStoredAndRedactedFromRawBody() throws IOException {
        String base64 = java.util.Base64.getEncoder().encodeToString(MinimalPng.BYTES);
        HttpServer server = server(exchange -> respond(exchange, 200,
                "{\"id\":\"upstream-1\",\"outputs\":[{\"object_key\":\"img/b64.png\",\"b64_json\":\"" + base64 + "\"}]}"));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.outputObjectKeys()).containsExactly("img/b64.png");
            assertThat(java.nio.file.Files.readAllBytes(tempDir.resolve("flora-dev").resolve("img/b64.png")))
                    .isEqualTo(MinimalPng.BYTES);
            assertThat(result.rawBodyJson()).doesNotContain(base64);
            assertThat(result.rawBodyJson()).contains("omitted base64 image bytes");
        } finally {
            server.stop(0);
        }
    }

    /** WP8：上游给的非法 Base64 → 可重试失败，不写对象键。 */
    @Test
    void httpInvalidBase64IsRetryable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200,
                "{\"outputs\":[{\"object_key\":\"img/bad.png\",\"b64_json\":\"!!!not-base64!!!\"}]}"));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(result.outputObjectKeys()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------- http 模式

    @Test
    void http200SucceedsAndExtractsUpstreamIdAndKeys() throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            received.set(readBody(exchange));
            respond(exchange, 200, "{\"id\":\"upstream-1\",\"object_keys\":[\"img/a.png\"]}");
        });
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 2, "{\"prompt\":\"hi\"}"));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.upstreamRequestId()).isEqualTo("upstream-1");
            assertThat(result.outputObjectKeys()).containsExactly("img/a.png");
            assertThat(result.rawStatus()).isEqualTo("200");

            JsonNode sent = readJson(received.get());
            assertThat(sent.get("attempt").asInt()).isEqualTo(2);
            assertThat(sent.get("capability").asText()).isEqualTo("image.generate");
            assertThat(sent.get("model").asText()).isEqualTo("demo");
            assertThat(sent.get("params").get("prompt").asText()).isEqualTo("hi");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpSendsTheAuthorizationHeaderButNeverStoresTheKey() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"id\":\"upstream-1\"}");
        });
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(authorization.get()).isEqualTo("Bearer " + API_KEY);
            assertThat(result.rawBodyJson()).doesNotContain(API_KEY);
            assertThat(result.rawBodyJson()).doesNotContain("Authorization");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void http4xxMapsToJobFailedAndIsNotRetryable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 400, "{\"error\":\"bad params\"}"));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(result.errorCode()).isEqualTo("JOB_FAILED");
            assertThat(result.rawStatus()).isEqualTo("400");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void http5xxAnd429MapToProviderUnavailableAndAreRetryable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 503, "{\"error\":\"overloaded\"}"));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(result.rawStatus()).isEqualTo("503");
        } finally {
            server.stop(0);
        }

        HttpServer throttled = server(exchange -> respond(exchange, 429, "{\"error\":\"slow down\"}"));
        try {
            AnytokenAttemptResult result = httpAdapter(throttled, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(result.rawStatus()).isEqualTo("429");
        } finally {
            throttled.stop(0);
        }
    }

    @Test
    void httpInvalidJsonIsProviderUnavailable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200, "not json at all"));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpTimeoutIsProviderUnavailable() throws IOException {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{}");
        });
        try {
            AnytokenAttemptResult result = httpAdapter(server, 120).execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(result.rawStatus()).isEqualTo("TIMEOUT");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void httpLongBodyIsTruncatedTo4096Chars() throws IOException {
        String longBody = "x".repeat(5000);
        HttpServer server = server(exchange -> respond(exchange, 200, longBody));
        try {
            AnytokenAttemptResult result = httpAdapter(server, 5000).execute(request(UUID.randomUUID(), 1, "{}"));

            JsonNode raw = readJson(result.rawBodyJson());
            assertThat(raw.get("body_excerpt").asText()).hasSize(4096);
            assertThat(raw.get("truncated").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unknownModeFailsFast() {
        AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                new AnytokenProperties("grpc", "http://127.0.0.1:1", "", 100, "/v1/generations"), mapper, tosStorage());

        assertThatThrownBy(() -> adapter.execute(request(UUID.randomUUID(), 1, "{}")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ 工具

    private AnytokenGatewayAdapter stubAdapter() {
        return new AnytokenGatewayAdapter(
                new AnytokenProperties("stub", "http://127.0.0.1:18080", "", 30000, "/v1/generations"), mapper, tosStorage());
    }

    private AnytokenGatewayAdapter httpAdapter(HttpServer server, long timeoutMs) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AnytokenGatewayAdapter(
                new AnytokenProperties("http", baseUrl, API_KEY, timeoutMs, "/v1/generations"), mapper, tosStorage());
    }

    private TosObjectStorage tosStorage() {
        return new TosObjectStorageAdapter(new TosProperties("stub", "http://127.0.0.1:18080", "cn-beijing",
                "flora-dev", "flora-local-ak", "flora-local-sk", 900, 15000, tempDir.toString()));
    }

    private AnytokenRequest request(UUID jobId, int attempt, String paramsJson) {
        return new AnytokenRequest(jobId, attempt, "image.generate", "demo", paramsJson);
    }

    private JsonNode readJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    private HttpServer server(Responder responder) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/generations", exchange -> responder.respond(exchange));
        server.start();
        return server;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
