package art.pilipala.api.billing.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 彩虹易支付 MD5 签名（下单与异步通知验签）。
 */
public final class ZpaySign {

    private static final Set<String> SIGN_KEYS = Set.of("sign", "sign_type");

    private ZpaySign() {
    }

    public static String sign(Map<String, String> params, String pkey) {
        String payload = canonicalPayload(params) + pkey;
        return md5HexLower(payload);
    }

    public static boolean verify(Map<String, String> params, String pkey) {
        String expected = params.get("sign");
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return expected.equalsIgnoreCase(sign(params, pkey));
    }

    static String canonicalPayload(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (SIGN_KEYS.contains(key)) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            sorted.put(key, value);
        }
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    private static String md5HexLower(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
