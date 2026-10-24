package com.flora.api.storage;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * 给 TOS 对象签发短期可读的预签名 GET 地址。
 *
 * <p>签发的是 TOS V4（{@code TOS4-HMAC-SHA256}）Query 预签名：把
 * {@code X-Tos-Algorithm} / {@code X-Tos-Credential} / {@code X-Tos-Date} /
 * {@code X-Tos-Expires} / {@code X-Tos-SignedHeaders} 按字典序写进 query，
 * 最后拼上 {@code X-Tos-Signature}。
 *
 * <p>worker 只写对象键（{@code job.result_object_keys}），读地址由 api 现签，
 * 不落库、不进 MQ 消息（ADR 0003）。
 */
@Component
public class TosUrlSigner {

    static final long MIN_TTL_SECONDS = 60;
    static final long MAX_TTL_SECONDS = 86400;

    private final TosProperties properties;

    public TosUrlSigner(TosProperties properties) {
        validate(properties);
        this.properties = properties;
    }

    /** 用当前时间签发。 */
    public String signGetUrl(String objectKey) {
        return signGetUrl(objectKey, Instant.now());
    }

    /**
     * 签发预签名 GET 地址。
     *
     * @param objectKey  对象键；允许前导 {@code /}，会被规范化掉
     * @param signingTime 签名时间（UTC）
     * @throws IllegalArgumentException 对象键为空或含 {@code ..}
     */
    public String signGetUrl(String objectKey, Instant signingTime) {
        String key = normalizeKey(objectKey);
        String iso = TosV4Signer.formatBasicUtc(signingTime);
        String date = TosV4Signer.yyyymmdd(iso);

        URI uri = URI.create(endpointBase());
        String scheme = uri.getScheme();
        String rawHost = uri.getHost();
        int port = uri.getPort();
        if (scheme == null || rawHost == null || rawHost.isBlank()) {
            throw new IllegalStateException("flora.storage.tos.endpoint 不是合法的 URI：" + properties.endpoint());
        }

        // 回环地址走 Path-Style（本地 HttpServer 绑不了子域名）；
        // 真实火山域名强制 Virtual-Host Style，否则网关返回 403 InvalidPathAccess。
        String host;
        String canonicalUri;
        if (isLoopbackHost(rawHost)) {
            host = formatHostPort(rawHost, port);
            canonicalUri = "/" + properties.bucket() + "/" + TosV4Signer.uriEncodePath(key);
        } else {
            String bucketPrefix = properties.bucket() + ".";
            String virtualHost = rawHost.regionMatches(true, 0, bucketPrefix, 0, bucketPrefix.length())
                    ? rawHost
                    : bucketPrefix + rawHost;
            host = formatHostPort(virtualHost, port);
            canonicalUri = "/" + TosV4Signer.uriEncodePath(key);
        }
        String baseUrl = scheme + "://" + host;

        Map<String, String> query = new TreeMap<>();
        query.put("X-Tos-Algorithm", TosV4Signer.ALGORITHM);
        query.put("X-Tos-Credential",
                properties.accessKey() + "/" + TosV4Signer.credentialScope(date, properties.region()));
        query.put("X-Tos-Date", iso);
        query.put("X-Tos-Expires", String.valueOf(properties.presignTtlSeconds()));
        query.put("X-Tos-SignedHeaders", "host");
        String canonicalQueryString = query.entrySet().stream()
                .map(entry -> TosV4Signer.uriEncode(entry.getKey()) + "=" + TosV4Signer.uriEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        String canonicalHeaders = "host:" + host + "\n";
        String signedHeaders = "host";
        String canonicalRequest = "GET\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + TosV4Signer.UNSIGNED_PAYLOAD;
        String stringToSign = TosV4Signer.stringToSign(iso, properties.region(), canonicalRequest);
        String signature = TosV4Signer.signature(properties.secretKey(), properties.region(), date, stringToSign);

        return baseUrl + canonicalUri + "?" + canonicalQueryString + "&X-Tos-Signature=" + signature;
    }

    /**
     * 回环地址判定：本地单测的 HttpServer 只绑 127.0.0.1 / localhost，必须保持 Path-Style。
     */
    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "127.0.0.1".equals(lower)
                || "localhost".equals(lower)
                || "::1".equals(lower)
                || "[::1]".equals(lower);
    }

    /** 参与签名的 host 头：非默认端口要带端口号。 */
    private static String formatHostPort(String host, int port) {
        if (port != -1 && port != 80 && port != 443) {
            return host + ":" + port;
        }
        return host;
    }

    /** 对象键规范化：去前导 {@code /}，拒绝空串与 {@code ..} 路径穿越。 */
    static String normalizeKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        String key = objectKey.trim();
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("对象键不能为空");
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("对象键不能包含 ..");
        }
        return key;
    }

    private static void validate(TosProperties properties) {
        requireText(properties.endpoint(), "flora.storage.tos.endpoint");
        requireText(properties.region(), "flora.storage.tos.region");
        requireText(properties.bucket(), "flora.storage.tos.bucket");
        if (properties.presignTtlSeconds() < MIN_TTL_SECONDS || properties.presignTtlSeconds() > MAX_TTL_SECONDS) {
            throw new IllegalStateException("flora.storage.tos.presign-ttl-seconds 必须在 " + MIN_TTL_SECONDS
                    + ".." + MAX_TTL_SECONDS + " 之间，当前是 " + properties.presignTtlSeconds());
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

    /** 服务端点：去掉末尾的 {@code /}。 */
    private String endpointBase() {
        String endpoint = properties.endpoint().trim();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }
}
