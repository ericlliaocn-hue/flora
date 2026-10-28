package art.pilipala.worker.gateway.newapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * NewAPI 客户端的协议回归：用 JDK 自带 HttpServer 模拟上游，不摸底真实 Key、不产生费用。
 */
class HttpNewApiVideoClientTest {

    private final List<String> authHeaders = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/video/generations", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void submitSendsAuthAndParsesTheTaskId() {
        NewApiSubmitResult result = client().submit(request());

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(result.status()).isEqualTo("RUNNING");
        assertThat(result.errorCode()).isNull();
        assertThat(authHeaders).contains("Bearer test-key");
    }

    @Test
    void querySuccessReadsTheObjectUrl() {
        NewApiTaskStatus status = client().query("task-1");

        assertThat(status.succeeded()).isTrue();
        assertThat(status.terminal()).isTrue();
        assertThat(status.outputUrl()).isEqualTo("https://cdn.example/v.mp4");
        assertThat(status.errorCode()).isNull();
        assertThat(authHeaders).contains("Bearer test-key");
    }

    @Test
    void queryFailedPrefixIsTerminalAndNotRetryable() {
        NewApiTaskStatus status = client().query("failed");

        assertThat(status.succeeded()).isFalse();
        assertThat(status.terminal()).isTrue();
        assertThat(status.retryable()).isFalse();
        assertThat(status.errorCode()).isEqualTo("UPSTREAM_VIDEO_FAILED");
        assertThat(status.message()).contains("bad prompt");
    }

    @Test
    void queryMissingTaskIsTerminal() {
        NewApiTaskStatus status = client().query("missing");

        assertThat(status.terminal()).isTrue();
        assertThat(status.retryable()).isFalse();
        assertThat(status.errorCode()).isEqualTo("GATEWAY_INVALID_RESPONSE");
    }

    @Test
    void queryOverloadIsRetryable() {
        NewApiTaskStatus status = client().query("overload");

        assertThat(status.terminal()).isFalse();
        assertThat(status.retryable()).isTrue();
        assertThat(status.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void queryUnknownStatusIsRetryable() {
        NewApiTaskStatus status = client().query("unknown");

        assertThat(status.terminal()).isFalse();
        assertThat(status.retryable()).isTrue();
        assertThat(status.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void unconfiguredClientDoesNotCallUpstream() {
        HttpNewApiVideoClient unconfigured = new HttpNewApiVideoClient(
                new NewApiProperties("", "", 5000), new ObjectMapper());

        NewApiSubmitResult result = unconfigured.submit(request());

        assertThat(result.accepted()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(authHeaders).isEmpty();
    }

    private HttpNewApiVideoClient client() {
        return new HttpNewApiVideoClient(new NewApiProperties(baseUrl, "test-key", 5000), new ObjectMapper());
    }

    private NewApiVideoRequest request() {
        return new NewApiVideoRequest("sd-2-c4", "a red ball rolling", 4, "1280x720", "{}");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        authHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
        if ("POST".equals(method) && path.equals("/v1/video/generations")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (!body.contains("\"model\":\"sd-2-c4\"") || !body.contains("\"duration\":4")) {
                respond(exchange, 400, "{\"code\":\"bad_request\"}");
                return;
            }
            respond(exchange, 200, "{\"id\":\"task-1\",\"task_id\":\"task-1\",\"status\":\"RUNNING\"}");
            return;
        }
        if ("GET".equals(method) && path.startsWith("/v1/video/generations/")) {
            if (path.endsWith("/task-1")) {
                respond(exchange, 200,
                        "{\"id\":\"task-1\",\"status\":\"SUCCEEDED\",\"object\":\"https://cdn.example/v.mp4\"}");
                return;
            }
            if (path.endsWith("/failed")) {
                respond(exchange, 200, "{\"status\":\"FAILED: bad prompt\"}");
                return;
            }
            if (path.endsWith("/missing")) {
                respond(exchange, 400, "{\"code\":\"task_not_exist\"}");
                return;
            }
            if (path.endsWith("/overload")) {
                respond(exchange, 503, "{}");
                return;
            }
            if (path.endsWith("/unknown")) {
                respond(exchange, 200, "{\"status\":\"WHATEVER\"}");
                return;
            }
        }
        respond(exchange, 404, "{}");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
