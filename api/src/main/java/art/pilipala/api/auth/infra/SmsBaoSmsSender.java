package art.pilipala.api.auth.infra;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 短信宝 HTTP 接口（GET）。文档：https://www.smsbao.com/openapi/
 */
public class SmsBaoSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmsBaoSmsSender.class);

    private static final String DEFAULT_TEMPLATE = "【pilipala】您的验证码是{code}，5分钟内有效。若非本人操作请忽略。";

    private final AuthChannelProperties.SmsSmsbao config;

    public SmsBaoSmsSender(AuthChannelProperties.SmsSmsbao config) {
        this.config = config;
    }

    @Override
    public void sendOtp(String phoneE164, String purpose, String code) {
        if (config == null || isBlank(config.username()) || isBlank(config.apiKey())) {
            log.error("短信宝未配置用户名或 ApiKey purpose={} phone={}", purpose, maskPhone(phoneE164));
            throw AuthException.providerUnavailable();
        }
        String mobile = toSmsBaoMobile(phoneE164);
        if (mobile == null) {
            log.error("短信宝手机号无法解析 purpose={} phone={}", purpose, maskPhone(phoneE164));
            throw AuthException.providerUnavailable();
        }
        String template = isBlank(config.contentTemplate()) ? DEFAULT_TEMPLATE : config.contentTemplate();
        String content = template.replace("{code}", code);
        try {
            String base = isBlank(config.baseUrl()) ? "https://api.smsbao.com/sms" : config.baseUrl().trim();
            StringBuilder url = new StringBuilder(base);
            url.append("?u=").append(encode(config.username()));
            url.append("&p=").append(encode(config.apiKey()));
            url.append("&m=").append(encode(mobile));
            url.append("&c=").append(encode(content));
            if (!isBlank(config.goodsId())) {
                url.append("&g=").append(encode(config.goodsId()));
            }

            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder(URI.create(url.toString())).GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.error("短信宝 HTTP {} purpose={} phone={}", response.statusCode(), purpose, maskPhone(phoneE164));
                throw AuthException.providerUnavailable();
            }
            String body = response.body() == null ? "" : response.body().trim();
            if (!"0".equals(body)) {
                log.error("短信宝发送失败 code={} message={} phone={} purpose={}",
                        body, smsBaoErrorMessage(body), maskPhone(phoneE164), purpose);
                throw AuthException.providerUnavailable();
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信宝请求异常 phone={} purpose={}", maskPhone(phoneE164), purpose, e);
            throw AuthException.providerUnavailable();
        }
    }

    static String toSmsBaoMobile(String phoneE164) {
        if (phoneE164 == null || phoneE164.isBlank()) {
            return null;
        }
        String digits = phoneE164.trim();
        if (digits.startsWith("+86")) {
            return digits.substring(3);
        }
        if (digits.startsWith("86") && digits.length() == 13) {
            return digits.substring(2);
        }
        return digits.startsWith("1") && digits.length() == 11 ? digits : null;
    }

    private static String smsBaoErrorMessage(String code) {
        return switch (code) {
            case "30" -> "接口密码/API Key 不正确";
            case "40" -> "账号不存在";
            case "41" -> "余额不足";
            case "43" -> "IP 地址限制";
            case "50" -> "内容含有敏感词";
            case "51" -> "手机号码不正确";
            default -> "上游返回错误";
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
