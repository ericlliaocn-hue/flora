package art.pilipala.worker.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import art.pilipala.worker.storage.MinimalMp4;
import art.pilipala.worker.storage.MinimalPng;
import art.pilipala.worker.storage.TosObjectStorage;
import art.pilipala.worker.storage.TosObjectStorageAdapter;
import art.pilipala.worker.storage.TosProperties;
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

    // ------------------------------------------------------- 真实 Anytoken 协议

    /** baseUrl 末尾的 /v1 与 generatePath 开头的 /v1/ 只保留一个。 */
    @Test
    void resolveGenerateUriDropsTheDuplicatedV1Segment() {
        AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                new AnytokenProperties("http", "https://api.anytoken.work/v1", API_KEY, 30000, "/v1/images/generations"),
                mapper, tosStorage());

        assertThat(adapter.resolveGenerateUri()).hasToString("https://api.anytoken.work/v1/images/generations");
    }

    @Test
    void resolveGenerateUriKeepsASingleV1() {
        AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                new AnytokenProperties("http", "https://api.anytoken.work", API_KEY, 30000, "/v1/images/generations"),
                mapper, tosStorage());

        assertThat(adapter.resolveGenerateUri()).hasToString("https://api.anytoken.work/v1/images/generations");
    }

    /** 请求体同时带内部契约字段与标准 OpenAI 生图顶层字段。 */
    @Test
    void httpRequestBodyCarriesOpenAiImageFields() throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            received.set(readBody(exchange));
            respond(exchange, 200, "{\"id\":\"upstream-1\"}");
        });
        try {
            AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                    new AnytokenProperties("http", "http://127.0.0.1:" + server.getAddress().getPort(), API_KEY, 5000,
                            "/v1/generations"),
                    mapper, tosStorage());

            adapter.execute(request(UUID.randomUUID(), 1, "{\"prompt\":\"a garden\",\"size\":\"1:1\",\"quality\":\"high\"}"));

            JsonNode sent = readJson(received.get());
            // 内部契约字段（既有断言不能破）
            assertThat(sent.get("job_id").asText()).isNotBlank();
            assertThat(sent.get("params").get("prompt").asText()).isEqualTo("a garden");
            // OpenAI /v1/images/generations 顶层字段
            assertThat(sent.get("prompt").asText()).isEqualTo("a garden");
            assertThat(sent.get("size").asText()).isEqualTo("1024x1024");
            assertThat(sent.get("quality").asText()).isEqualTo("high");
            assertThat(sent.get("n").asInt()).isEqualTo(1);
            assertThat(sent.get("response_format").asText()).isEqualTo("b64_json");
        } finally {
            server.stop(0);
        }
    }

    /** gpt-image 系列默认返回 base64：不带 response_format。 */
    @Test
    void gptImageModelDoesNotSendResponseFormat() throws IOException {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            received.set(readBody(exchange));
            respond(exchange, 200, "{\"id\":\"upstream-1\"}");
        });
        try {
            AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                    new AnytokenProperties("http", "http://127.0.0.1:" + server.getAddress().getPort(), API_KEY, 5000,
                            "/v1/generations"),
                    mapper, tosStorage());

            adapter.execute(new AnytokenRequest(UUID.randomUUID(), 1, "image.generate", "gpt-image-2", "{\"prompt\":\"x\"}"));

            assertThat(readJson(received.get()).has("response_format")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    /** 上游只给临时图片 URL 时，worker 下载字节并写进 TOS。 */
    @Test
    void httpTemporaryImageUrlIsDownloadedAndStoredInTos() throws IOException {
        HttpServer server = serverWithImage();
        try {
            UUID jobId = UUID.randomUUID();
            AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                    new AnytokenProperties("http", "http://127.0.0.1:" + server.getAddress().getPort(), API_KEY, 5000,
                            "/v1/generations"),
                    mapper, tosStorage());

            AnytokenAttemptResult result = adapter.execute(request(jobId, 7, "{\"prompt\":\"x\"}"));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.outputObjectKeys()).containsExactly("anytoken/img/" + jobId + "/7.png");
            assertThat(java.nio.file.Files.readAllBytes(tempDir.resolve("flora-dev").resolve("anytoken/img/" + jobId + "/7.png")))
                    .isEqualTo(MinimalPng.BYTES);
        } finally {
            server.stop(0);
        }
    }

    /** 临时图片下载失败 → 可重试失败，绝不假装成功。 */
    @Test
    void httpTemporaryImageDownloadFailureIsRetryable() throws IOException {
        HttpServer server = server(exchange -> respond(exchange, 200, "{\"data\":[{\"url\":\"http://127.0.0.1:1/none.png\"}]}"));
        try {
            AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                    new AnytokenProperties("http", "http://127.0.0.1:" + server.getAddress().getPort(), API_KEY, 5000,
                            "/v1/generations"),
                    mapper, tosStorage());

            AnytokenAttemptResult result = adapter.execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
            assertThat(result.outputObjectKeys()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    /** 视频能力用独立的 video key（WP12 起视频走 Seedance 的三步协议，不再打生图端点）。 */
    @Test
    void videoCapabilityUsesTheVideoApiKey() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = videoServer(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"id\":\"upstream-1\",\"status\":\"completed\"}");
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/content")) {
                respondBytes(exchange, 200, MinimalMp4.BYTES);
                return;
            }
            respond(exchange, 200, "{\"status\":\"completed\"}");
        });
        try {
            AnytokenGatewayAdapter adapter = videoAdapter(server, "video-secret");

            adapter.execute(videoRequest(UUID.randomUUID(), 1, "{}"));

            assertThat(authorization.get()).isEqualTo("Bearer video-secret");
        } finally {
            server.stop(0);
        }
    }

    /** 生图能力仍然用通用 key。 */
    @Test
    void imageCapabilityKeepsTheGeneralApiKey() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"id\":\"upstream-1\"}");
        });
        try {
            AnytokenGatewayAdapter adapter = new AnytokenGatewayAdapter(
                    new AnytokenProperties("http", "http://127.0.0.1:" + server.getAddress().getPort(), API_KEY, "video-secret",
                            5000, "/v1/generations"),
                    mapper, tosStorage());

            adapter.execute(request(UUID.randomUUID(), 1, "{}"));

            assertThat(authorization.get()).isEqualTo("Bearer " + API_KEY);
        } finally {
            server.stop(0);
        }
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

    /** 上游只回临时图片 URL 的场景：同一个桩既当生图接口，也当图片源站。 */
    private HttpServer serverWithImage() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/generations", exchange -> {
            String host = exchange.getRequestHeaders().getFirst("Host");
            respond(exchange, 200, "{\"data\":[{\"url\":\"http://" + host + "/generated.png\"}]}");
        });
        server.createContext("/generated.png", exchange -> {
            byte[] bytes = MinimalPng.BYTES;
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
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

    private void respondBytes(HttpExchange exchange, int status, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "video/mp4");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    // ------------------------------------------------------- Seedance 视频协议

    /**
     * Seedance 三步协议的桩：提交、轮询、内容下载都挂在 {@code /v1/videos} 前缀下，
     * 具体分支由处理函数按路径与请求方法自己分。
     */
    private HttpServer videoServer(Responder responder) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/videos", exchange -> responder.respond(exchange));
        server.start();
        return server;
    }

    private AnytokenGatewayAdapter videoAdapter(HttpServer server, String videoKey) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new AnytokenGatewayAdapter(
                new AnytokenProperties("http", baseUrl, API_KEY, videoKey, 5000, "/v1/images/generations",
                        "/v1/videos/generations", "/v1/videos", 1L, 5),
                mapper, tosStorage());
    }

    private AnytokenRequest videoRequest(UUID jobId, int attempt, String paramsJson) {
        return new AnytokenRequest(jobId, attempt, "video.generate", "seedance2", paramsJson);
    }

    /** WP12：stub 模式下视频任务写入 .mp4 对象键与 video/mp4 内容类型。 */
    @Test
    void stubVideoStoresTheMp4BytesInTos() throws IOException {
        UUID jobId = UUID.randomUUID();

        AnytokenAttemptResult result = stubAdapter().execute(videoRequest(jobId, 1, "{}"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.outputObjectKeys()).containsExactly("anytoken/vid/" + jobId + "/1.mp4");
        java.nio.file.Path stored = tempDir.resolve("flora-dev").resolve("anytoken/vid/" + jobId + "/1.mp4");
        assertThat(stored).exists();
        assertThat(java.nio.file.Files.readAllBytes(stored)).isEqualTo(MinimalMp4.BYTES);
    }

    /** WP12：提交 → 轮询（processing → completed）→ 下载内容入桶。 */
    @Test
    void httpVideoSubmitsPollsAndStoresTheContent() throws IOException {
        AtomicReference<String> submitted = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicInteger polls = new AtomicInteger();
        HttpServer server = videoServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/generations")) {
                submitted.set(readBody(exchange));
                respond(exchange, 200, "{\"id\":\"task-1\",\"status\":\"processing\"}");
                return;
            }
            if (path.endsWith("/content")) {
                respondBytes(exchange, 200, MinimalMp4.BYTES);
                return;
            }
            polls.incrementAndGet();
            respond(exchange, 200, polls.get() == 1
                    ? "{\"status\":\"processing\"}"
                    : "{\"status\":\"completed\",\"data\":{\"status\":\"completed\"}}");
        });
        try {
            UUID jobId = UUID.randomUUID();
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(jobId, 2, "{\"prompt\":\"a cat\",\"size\":\"1280x720\",\"duration\":\"8\",\"resolution\":\"1080p\"}"));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.upstreamRequestId()).isEqualTo("task-1");
            assertThat(result.outputObjectKeys()).containsExactly("anytoken/vid/" + jobId + "/2.mp4");
            assertThat(polls.get()).isEqualTo(2);
            assertThat(authorization.get()).isEqualTo("Bearer video-secret");

            // 提交体：比例、时长、清晰度都按协议映射
            JsonNode body = readJson(submitted.get());
            assertThat(body.get("model").asText()).isEqualTo("seedance2");
            assertThat(body.get("prompt").asText()).isEqualTo("a cat");
            assertThat(body.get("aspect_ratio").asText()).isEqualTo("16:9");
            assertThat(body.get("duration").asInt()).isEqualTo(8);
            assertThat(body.get("resolution").asText()).isEqualTo("1080p");

            assertThat(java.nio.file.Files.readAllBytes(tempDir.resolve("flora-dev").resolve("anytoken/vid/" + jobId + "/2.mp4")))
                    .isEqualTo(MinimalMp4.BYTES);
        } finally {
            server.stop(0);
        }
    }

    /** WP12：竖版尺寸映射成 9:16；没传 size 时默认 16:9。 */
    @Test
    void httpVideoMapsTheVerticalSizeToNineBySixteen() throws IOException {
        AtomicReference<String> submitted = new AtomicReference<>();
        HttpServer server = videoServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                submitted.set(readBody(exchange));
                respond(exchange, 200, "{\"id\":\"task-2\",\"status\":\"completed\"}");
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/content")) {
                respondBytes(exchange, 200, MinimalMp4.BYTES);
                return;
            }
            respond(exchange, 200, "{\"status\":\"completed\"}");
        });
        try {
            videoAdapter(server, "video-secret").execute(videoRequest(UUID.randomUUID(), 1, "{\"size\":\"720x1280\"}"));
            assertThat(readJson(submitted.get()).get("aspect_ratio").asText()).isEqualTo("9:16");
        } finally {
            server.stop(0);
        }
    }

    /**
     * WP12 修复：上游把成片直链放在 {@code result.url}，且 {@code /content} 端点在本部署**不存在**（404）。
     *
     * <p>这是实测 Anytoken Seedance 的真实形状：{@code /content} 404 之后必须能回退到
     * {@code result.url} 把视频字节下下来，否则任务会以 PROVIDER_UNAVAILABLE 失败。
     */
    @Test
    void httpVideoFallsBackToTheResultUrlWhenTheContentEndpointIsMissing() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/v1/videos", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"id\":\"task-r\",\"status\":\"processing\"}");
                return;
            }
            if (path.endsWith("/content")) {
                respond(exchange, 404, "{\"error\":{\"message\":\"video not found\"}}");
                return;
            }
            respond(exchange, 200, "{\"id\":\"task-r\",\"status\":\"completed\",\"progress\":100,"
                    + "\"result\":{\"url\":\"" + baseUrl + "/file/task-r.mp4\",\"mime_type\":\"video/mp4\"}}");
        });
        server.createContext("/file", exchange -> respondBytes(exchange, 200, MinimalMp4.BYTES));
        server.start();
        try {
            UUID jobId = UUID.randomUUID();
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(jobId, 1, "{\"prompt\":\"a cat\"}"));

            assertThat(result.succeeded()).isTrue();
            assertThat(result.outputObjectKeys()).containsExactly("anytoken/vid/" + jobId + "/1.mp4");
            assertThat(java.nio.file.Files.readAllBytes(tempDir.resolve("flora-dev").resolve("anytoken/vid/" + jobId + "/1.mp4")))
                    .isEqualTo(MinimalMp4.BYTES);
        } finally {
            server.stop(0);
        }
    }

    /**
     * WP12 修复：上游视频报文**不能**带 flora 内部字段。
     *
     * <p>实测 Anytoken 看到 {@code capability} 会把请求路由到 xAI 上游并返回 400
     * （"xAI upstream returned status 400"），Seedance 视频根本起不来。
     */
    @Test
    void httpVideoPayloadCarriesNoFloraInternalFields() throws IOException {
        AtomicReference<String> submitted = new AtomicReference<>();
        HttpServer server = videoServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                submitted.set(readBody(exchange));
                respond(exchange, 200, "{\"id\":\"task-9\",\"status\":\"completed\"}");
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/content")) {
                respondBytes(exchange, 200, MinimalMp4.BYTES);
                return;
            }
            respond(exchange, 200, "{\"status\":\"completed\"}");
        });
        try {
            videoAdapter(server, "video-secret").execute(videoRequest(UUID.randomUUID(), 1, "{\"prompt\":\"a cat\"}"));
            JsonNode body = readJson(submitted.get());
            assertThat(body.has("job_id")).isFalse();
            assertThat(body.has("attempt")).isFalse();
            assertThat(body.has("capability")).isFalse();
            // 协议自己的字段照常带。
            assertThat(body.get("model").asText()).isEqualTo("seedance2");
            assertThat(body.get("prompt").asText()).isEqualTo("a cat");
        } finally {
            server.stop(0);
        }
    }

    /** WP12：上游视频任务进失败终态 → 不可重试。 */
    @Test
    void httpVideoFailedTerminalIsNotRetryable() throws IOException {
        HttpServer server = videoServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"id\":\"task-3\",\"status\":\"processing\"}");
                return;
            }
            respond(exchange, 200, "{\"status\":\"failed\",\"error\":{\"message\":\"content policy\"}}");
        });
        try {
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(result.errorCode()).isEqualTo("UPSTREAM_VIDEO_FAILED");
            assertThat(result.rawStatus()).isEqualTo("failed");
            assertThat(result.outputObjectKeys()).isEmpty();
        } finally {
            server.stop(0);
        }
    }

    /** WP12：轮询到上限仍未完成 → 可重试的 GATEWAY_TIMEOUT。 */
    @Test
    void httpVideoTimesOutAndStaysRetryable() throws IOException {
        HttpServer server = videoServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"id\":\"task-4\",\"status\":\"processing\"}");
                return;
            }
            respond(exchange, 200, "{\"status\":\"processing\"}");
        });
        try {
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.errorCode()).isEqualTo("GATEWAY_TIMEOUT");
        } finally {
            server.stop(0);
        }
    }

    /** WP12：上游没给任务 id → 不可重试的 GATEWAY_INVALID_RESPONSE。 */
    @Test
    void httpVideoWithoutATaskIdIsInvalid() throws IOException {
        HttpServer server = videoServer(exchange -> respond(exchange, 200, "{\"status\":\"processing\"}"));
        try {
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(result.errorCode()).isEqualTo("GATEWAY_INVALID_RESPONSE");
        } finally {
            server.stop(0);
        }
    }

    /** WP12：拿不到真实视频字节时宁可失败，也不写假 MP4。 */
    @Test
    void httpVideoWithoutContentIsRetryableInsteadOfFakingAFile() throws IOException {
        HttpServer server = videoServer(exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{\"id\":\"task-5\",\"status\":\"completed\"}");
                return;
            }
            if (exchange.getRequestURI().getPath().endsWith("/content")) {
                respondBytes(exchange, 404, new byte[0]);
                return;
            }
            respond(exchange, 200, "{\"status\":\"completed\"}");
        });
        try {
            AnytokenAttemptResult result = videoAdapter(server, "video-secret")
                    .execute(videoRequest(UUID.randomUUID(), 1, "{}"));

            assertThat(result.succeeded()).isFalse();
            assertThat(result.retryable()).isTrue();
            assertThat(result.outputObjectKeys()).isEmpty();
        } finally {
            server.stop(0);
        }
    }
}
