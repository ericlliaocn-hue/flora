package com.pilipala.api.billing.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.pilipala.api.billing.application.HandleZpayNotify;
import com.pilipala.api.job.infra.JobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/recharge/webhook/zpay")
public class ZpayWebhookController {

    private final HandleZpayNotify notify;

    public ZpayWebhookController(HandleZpayNotify notify) {
        this.notify = notify;
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> webhook(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        try {
            notify.handle(params);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
        } catch (JobException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("error");
        }
    }
}
