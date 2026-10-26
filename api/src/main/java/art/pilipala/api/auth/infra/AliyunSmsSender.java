package art.pilipala.api.auth.infra;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阿里云 Dysmsapi HTTP 调用（{@code PILIPALA_SMS_PROVIDER=aliyun}）。
 */
public class AliyunSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(AliyunSmsSender.class);

    private final AuthChannelProperties.SmsAliyun sms;

    public AliyunSmsSender(AuthChannelProperties.SmsAliyun sms) {
        this.sms = sms;
    }

    @Override
    public void sendOtp(String phoneE164, String purpose, String code) {
        if (this.sms == null || isBlank(sms.accessKeyId()) || isBlank(sms.accessKeySecret())) {
            log.error("Aliyun SMS 未配置 purpose={} phone={}", purpose, maskPhone(phoneE164));
            throw AuthException.providerUnavailable();
        }
        String template = templateFor(purpose, sms);
        if (isBlank(template)) {
            log.error("Aliyun SMS 模板未配置 purpose={}", purpose);
            throw AuthException.providerUnavailable();
        }
        String mobile = phoneE164.startsWith("+86") ? phoneE164.substring(3) : phoneE164;
        try {
            Map<String, String> params = new TreeMap<>();
            params.put("AccessKeyId", sms.accessKeyId());
            params.put("Action", "SendSms");
            params.put("Format", "JSON");
            params.put("PhoneNumbers", mobile);
            params.put("RegionId", "cn-hangzhou");
            params.put("SignName", sms.signName());
            params.put("SignatureMethod", "HMAC-SHA1");
            params.put("SignatureNonce", UUID.randomUUID().toString());
            params.put("SignatureVersion", "1.0");
            params.put("TemplateCode", template);
            params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
            params.put("Timestamp", Instant.now().toString().replace("Z", "Z"));
            params.put("Version", "2017-05-25");

            String canonical = params.entrySet().stream()
                    .map(e -> percentEncode(e.getKey()) + "=" + percentEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
            String stringToSign = "GET&%2F&" + percentEncode(canonical);
            String signature = sign(stringToSign, sms.accessKeySecret() + "&");
            String endpoint = isBlank(sms.endpoint()) ? "dysmsapi.aliyuncs.com" : sms.endpoint();
            String url = "https://" + endpoint + "/?Signature=" + percentEncode(signature) + "&" + canonical;

            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder(URI.create(url)).GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 400) {
                log.error("Aliyun SMS HTTP {} body={} phone={}", response.statusCode(), body, maskPhone(phoneE164));
                throw AuthException.providerUnavailable();
            }
            if (!body.contains("\"Code\":\"OK\"")) {
                log.error("Aliyun SMS API 未成功 body={} phone={} purpose={}", body, maskPhone(phoneE164), purpose);
                throw AuthException.providerUnavailable();
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Aliyun SMS 发送失败 phone={} purpose={}", maskPhone(phoneE164), purpose, e);
            throw AuthException.providerUnavailable();
        }
    }

    private static String templateFor(String purpose, AuthChannelProperties.SmsAliyun sms) {
        return switch (purpose) {
            case "register" -> sms.templateRegister();
            case "login" -> sms.templateLogin();
            case "rebind_phone", "rebind_email" -> sms.templateRebind();
            case "reset_password" -> sms.templateReset();
            default -> sms.templateLogin();
        };
    }

    private static String sign(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(raw);
    }

    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
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
