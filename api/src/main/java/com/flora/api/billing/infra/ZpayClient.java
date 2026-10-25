package com.flora.api.billing.infra;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flora.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * z-pay HTTP 客户端：mapi 下单、查单、退款（mock 友好，单测可替换）。
 */
@Component
public class ZpayClient {

    private static final Logger log = LoggerFactory.getLogger(ZpayClient.class);
    private static final String ALIPAY = "alipay";

    private final ZpayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ZpayClient(ZpayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    ZpayClient(ZpayProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public MapiResult createAlipayOrder(MapiRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", properties.pid());
        params.put("type", ALIPAY);
        params.put("out_trade_no", request.outTradeNo());
        params.put("notify_url", request.notifyUrl());
        params.put("return_url", request.returnUrl());
        params.put("name", request.productName());
        params.put("money", ZpayMoney.fenToYuanString(request.moneyFen()));
        params.put("clientip", request.clientIp());
        params.put("cid", properties.cid());
        if (request.mobile()) {
            params.put("device", "mobile");
        }
        params.put("sign_type", "MD5");
        params.put("sign", ZpaySign.sign(params, properties.pkey()));

        String body = formEncode(params);
        String url = properties.normalizedApiBase() + "/mapi.php";
        JsonNode json = postForm(url, body);
        return parseMapi(json);
    }

    public OrderQueryResult queryOrder(String outTradeNo) {
        String base = properties.normalizedApiBase() + "/api.php";
        String query = "act=order&pid=" + encode(properties.pid())
                + "&key=" + encode(properties.pkey())
                + "&out_trade_no=" + encode(outTradeNo);
        JsonNode json = tryGetJson(base + "?" + query);
        if (json == null) {
            json = postForm(base + "?act=order", formEncode(Map.of(
                    "pid", properties.pid(),
                    "key", properties.pkey(),
                    "out_trade_no", outTradeNo)));
        }
        return parseOrderQuery(json);
    }

    public void refundByOutTradeNo(String outTradeNo, long moneyFen) {
        refund(outTradeNo, null, moneyFen);
    }

    public void refundByTradeNo(String tradeNo, long moneyFen) {
        refund(null, tradeNo, moneyFen);
    }

    private void refund(String outTradeNo, String tradeNo, long moneyFen) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", properties.pid());
        params.put("key", properties.pkey());
        params.put("money", ZpayMoney.fenToYuanString(moneyFen));
        if (outTradeNo != null) {
            params.put("out_trade_no", outTradeNo);
        }
        if (tradeNo != null) {
            params.put("trade_no", tradeNo);
        }
        String url = properties.normalizedApiBase() + "/api.php?act=refund";
        JsonNode json = postForm(url, formEncode(params));
        if (!isSuccessCode(json.path("code"))) {
            log.warn("z-pay refund failed out={} trade={} body={}", outTradeNo, tradeNo, json);
            throw JobException.rechargeProviderError();
        }
    }

    private JsonNode tryGetJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode postForm(String url, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("z-pay HTTP {} url={}", response.statusCode(), url);
                throw JobException.rechargeProviderError();
            }
            return objectMapper.readTree(response.body());
        } catch (JobException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("z-pay request failed url={}", url, e);
            throw JobException.rechargeProviderError();
        }
    }

    private MapiResult parseMapi(JsonNode json) {
        if (!isSuccessCode(json.path("code"))) {
            String msg = json.path("msg").asText("upstream error");
            log.warn("z-pay mapi failed: {}", msg);
            throw JobException.rechargeProviderError();
        }
        String tradeNo = textOrNull(json, "trade_no");
        String payurl = textOrNull(json, "payurl");
        String qrcode = textOrNull(json, "qrcode");
        String payurl2 = textOrNull(json, "payurl2");
        return new MapiResult(tradeNo, payurl, qrcode, payurl2);
    }

    private OrderQueryResult parseOrderQuery(JsonNode json) {
        boolean paid = isPaid(json) || isPaid(json.path("data"));
        String tradeNo = firstText(json, "trade_no");
        if (tradeNo == null) {
            tradeNo = firstText(json.path("data"), "trade_no");
        }
        String money = firstText(json, "money");
        if (money == null) {
            money = firstText(json.path("data"), "money");
        }
        return new OrderQueryResult(paid, tradeNo, money);
    }

    private static boolean isPaid(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return false;
        }
        String tradeStatus = textOrNull(node, "trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus)) {
            return true;
        }
        String status = textOrNull(node, "status");
        return "1".equals(status);
    }

    private static boolean isSuccessCode(JsonNode codeNode) {
        if (codeNode.isMissingNode() || codeNode.isNull()) {
            return false;
        }
        if (codeNode.isNumber()) {
            return codeNode.intValue() == 1;
        }
        return "1".equals(codeNode.asText());
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static String firstText(JsonNode node, String field) {
        return textOrNull(node, field);
    }

    private static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record MapiRequest(
            String outTradeNo,
            String notifyUrl,
            String returnUrl,
            String productName,
            long moneyFen,
            String clientIp,
            boolean mobile) {
    }

    public record MapiResult(String tradeNo, String payurl, String qrcode, String payurl2) {
    }

    public record OrderQueryResult(boolean paid, String tradeNo, String moneyYuan) {
    }
}
