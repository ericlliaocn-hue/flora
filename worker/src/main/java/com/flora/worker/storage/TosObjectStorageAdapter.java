package com.flora.worker.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

/**
 * 火山 TOS 写入的唯一实现：stub（本地落盘）与 http（V4 Header 签名 PUT）双模式。
 *
 * <p>stub 模式不发外部请求，把字节写到 {@code ${local-dir}/{bucket}/{object_key}}，
 * 方便本地双进程零密钥联调；http 模式才真的调火山 TOS。
 *
 * <p>对象键规范化后还要校验目标路径仍在落盘根目录内，拒绝 {@code ..} 路径穿越。
 *
 * <p>脱敏：异常与日志里只有 HTTP 状态码和一句摘要，**绝不**出现 access key /
 * secret key / Authorization 头（ADR 0005）。
 */
@Component
public class TosObjectStorageAdapter implements TosObjectStorage {

    private final TosProperties properties;
    private final HttpClient httpClient;

    public TosObjectStorageAdapter(TosProperties properties) {
        validate(properties);
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .build();
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        String key = normalizeKey(objectKey);
        if (content == null || content.length == 0) {
            throw new TosStorageException(0, "对象内容不能为空");
        }
        String type = (contentType == null || contentType.isBlank()) ? "image/png" : contentType;
        if ("stub".equals(properties.mode())) {
            writeToLocalDisk(key, content);
        } else {
            putToTos(key, content, type);
        }
    }

    // ---------------------------------------------------------------- stub

    private void writeToLocalDisk(String key, byte[] content) {
        Path root = Path.of(properties.localDir()).normalize().toAbsolutePath();
        Path target = root.resolve(properties.bucket()).resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new TosStorageException(0, "对象键越界，拒绝写入");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new TosStorageException(0, "本地落盘失败", e);
        }
    }

    // ---------------------------------------------------------------- http

    private void putToTos(String key, byte[] content, String contentType) {
        Instant now = Instant.now();
        String iso = TosV4Signer.formatBasicUtc(now);
        String host = hostHeader();
        String canonicalUri = "/" + properties.bucket() + "/" + TosV4Signer.uriEncodePath(key);
        String contentSha256 = TosV4Signer.sha256Hex(content);
        String authorization = TosV4Signer.authorization(properties.accessKey(), properties.secretKey(),
                properties.region(), canonicalUri, host, contentType, contentSha256, now);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpointBase() + canonicalUri))
                .timeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .header("Content-Type", contentType)
                .header("X-Tos-Date", iso)
                .header("X-Tos-Content-Sha256", contentSha256)
                .header("Authorization", authorization)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new TosStorageException(status, "TOS 上传失败，HTTP " + status);
            }
        } catch (HttpTimeoutException e) {
            throw new TosStorageException(0, "TOS 上传超时", e);
        } catch (IOException e) {
            throw new TosStorageException(0, "TOS 上传 IO 失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TosStorageException(0, "TOS 上传被中断", e);
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 对象键规范化：去前导 {@code /}，拒绝空串与 {@code ..} 路径穿越。 */
    static String normalizeKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new TosStorageException(0, "对象键不能为空");
        }
        String key = objectKey.trim();
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isEmpty()) {
            throw new TosStorageException(0, "对象键不能为空");
        }
        if (key.contains("..")) {
            throw new TosStorageException(0, "对象键不能包含 ..");
        }
        return key;
    }

    private static void validate(TosProperties properties) {
        requireText(properties.endpoint(), "flora.storage.tos.endpoint");
        requireText(properties.region(), "flora.storage.tos.region");
        requireText(properties.bucket(), "flora.storage.tos.bucket");
        if (!"stub".equals(properties.mode()) && !"http".equals(properties.mode())) {
            throw new IllegalStateException("flora.storage.tos.mode 只能是 stub 或 http，当前是 " + properties.mode());
        }
        if ("http".equals(properties.mode())) {
            requireText(properties.accessKey(), "flora.storage.tos.access-key");
            requireText(properties.secretKey(), "flora.storage.tos.secret-key");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 不能为空");
        }
    }

    private String endpointBase() {
        String endpoint = properties.endpoint().trim();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }

    /** 参与签名的 host 头：非默认端口要带端口号。 */
    private String hostHeader() {
        URI uri = URI.create(endpointBase());
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("flora.storage.tos.endpoint 不是合法的 URI：" + properties.endpoint());
        }
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            return host + ":" + port;
        }
        return host;
    }
}
