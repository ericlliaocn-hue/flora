package art.pilipala.api.billing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import art.pilipala.api.job.infra.JobException;

/**
 * 解析 {@code credit_product.meta_json}（自定义充值、限购等）。
 */
public final class CreditProductMeta {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CreditProductMeta() {
    }

    public static CustomSlider parseCustomSlider(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            throw JobException.validation();
        }
        try {
            JsonNode node = MAPPER.readTree(metaJson);
            long min = node.path("min_credits").asLong(600);
            long max = node.path("max_credits").asLong(499_995);
            int perYuan = node.path("credits_per_yuan").asInt(15);
            if (perYuan <= 0) {
                throw JobException.validation();
            }
            return new CustomSlider(min, max, perYuan);
        } catch (Exception e) {
            throw JobException.validation();
        }
    }

    public record CustomSlider(long minCredits, long maxCredits, int creditsPerYuan) {
    }

    public static int priceFenForCredits(long credits, int creditsPerYuan) {
        if (credits <= 0 || creditsPerYuan <= 0) {
            throw JobException.validation();
        }
        long fen = credits * 100L / creditsPerYuan;
        if (fen <= 0 || fen > Integer.MAX_VALUE) {
            throw JobException.validation();
        }
        return (int) fen;
    }
}
