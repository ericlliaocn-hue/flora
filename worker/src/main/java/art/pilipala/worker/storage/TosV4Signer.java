package art.pilipala.worker.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 火山引擎 TOS V4 签名（TOS4-HMAC-SHA256）的纯函数工具。
 *
 * <p>密钥派生（与火山引擎签名机制一致）：
 *
 * <pre>
 * kDate    = HmacSHA256(secretKey, yyyymmdd)
 * kRegion  = HmacSHA256(kDate, region)
 * kService = HmacSHA256(kRegion, "tos")
 * kSigning = HmacSHA256(kService, "request")
 * </pre>
 *
 * <p>本类只负责 PUT 的 Header 签名；预签名 GET 由 api 侧负责。两个模块不共用代码，
 * 但两边的派生密钥用同一组测试向量校验一致。
 */
public final class TosV4Signer {

    public static final String ALGORITHM = "TOS4-HMAC-SHA256";
    public static final String SERVICE = "tos";

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    /** 百分号编码用大写十六进制（RFC 3986）。 */
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();
    private static final DateTimeFormatter BASIC_UTC =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private TosV4Signer() {
    }

    /** 派生签名密钥。 */
    public static byte[] deriveSigningKey(String secretKey, String yyyymmdd, String region) {
        byte[] kDate = hmac(secretKey.getBytes(StandardCharsets.UTF_8), yyyymmdd);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, SERVICE);
        return hmac(kService, "request");
    }

    /** {@code 2026-10-24T04:30:00Z} → {@code 20261024T043000Z}。 */
    public static String formatBasicUtc(Instant instant) {
        return BASIC_UTC.format(instant);
    }

    /** 从 ISO 8601 basic UTC 里取日期部分。 */
    public static String yyyymmdd(String iso8601BasicUtc) {
        return iso8601BasicUtc.substring(0, 8);
    }

    /** 凭证范围：{@code yyyymmdd/region/tos/request}。 */
    public static String credentialScope(String yyyymmdd, String region) {
        return yyyymmdd + "/" + region + "/" + SERVICE + "/request";
    }

    /** 待签名字符串（4 行）。 */
    public static String stringToSign(String iso8601BasicUtc, String region, String canonicalRequest) {
        return ALGORITHM + "\n"
                + iso8601BasicUtc + "\n"
                + credentialScope(yyyymmdd(iso8601BasicUtc), region) + "\n"
                + sha256Hex(canonicalRequest);
    }

    /** 签名值（十六进制小写）。 */
    public static String signature(String secretKey, String region, String yyyymmdd, String stringToSign) {
        return hmacHex(deriveSigningKey(secretKey, yyyymmdd, region), stringToSign);
    }

    /**
     * PUT 的 {@code Authorization} 头。
     *
     * @param canonicalUri    参与签名的 URI 路径，例如 {@code /flora-dev/anytoken/img/x/1.png}
     * @param host            参与签名的 host 头（非默认端口要带端口号）
     * @param contentType     内容类型
     * @param contentSha256   请求体的 SHA-256 十六进制
     */
    public static String authorization(String accessKey, String secretKey, String region, String canonicalUri, String host,
            String contentType, String contentSha256, Instant signingTime) {
        String iso = formatBasicUtc(signingTime);
        String date = yyyymmdd(iso);
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
        String stringToSign = stringToSign(iso, region, canonicalRequest);
        String signature = signature(secretKey, region, date, stringToSign);
        return ALGORITHM + " Credential=" + accessKey + "/" + credentialScope(date, region)
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    public static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }

    public static String hmacHex(byte[] key, String data) {
        return hex(hmac(key, data));
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = HEX[(bytes[i] >> 4) & 0xF];
            out[i * 2 + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(out);
    }

    /** RFC 3986 百分号编码：只有 unreserved 字符不编码。 */
    public static String uriEncode(String raw) {
        StringBuilder out = new StringBuilder();
        for (byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%').append(HEX_UPPER[(c >> 4) & 0xF]).append(HEX_UPPER[c & 0xF]);
            }
        }
        return out.toString();
    }

    /** 对象键按 {@code /} 分段编码，保留路径分隔符。 */
    public static String uriEncodePath(String objectKey) {
        return Arrays.stream(objectKey.split("/", -1))
                .map(TosV4Signer::uriEncode)
                .collect(Collectors.joining("/"));
    }
}
