package art.pilipala.api.job.infra;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

/**
 * 幂等用的 request_hash 计算。
 *
 * <p>算法（写在代码注释里，PR 里也写明）：
 * <ol>
 *   <li>把 params 的 JSON 树转成普通 Map / List；</li>
 *   <li>用「对象键按字典序排序、数组保序」的 JSON 序列化，消除键顺序差异；</li>
 *   <li>拼成 {@code capability + "\n" + model + "\n" + 规范化 JSON}；</li>
 *   <li>算 SHA-256，取 hex。</li>
 * </ol>
 *
 * <p>同一 key + 同一 body → 同一 hash（重放返回原 job_id）；
 * 同一 key + 不同 body → 不同 hash → IDEMPOTENCY_CONFLICT。
 */
@Component
public class JobRequestHasher {

    /** 只用于规范化序列化，不参与 HTTP 响应。 */
    private static final JsonMapper CANONICAL = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    public String hash(String capability, String model, JsonNode params) {
        Object plain = CANONICAL.convertValue(params, Object.class);
        String canonicalJson;
        try {
            canonicalJson = CANONICAL.writeValueAsString(plain);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("params 无法规范化", e);
        }
        return sha256Hex(capability + "\n" + model + "\n" + canonicalJson);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
