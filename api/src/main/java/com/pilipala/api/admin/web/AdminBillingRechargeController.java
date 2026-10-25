package com.pilipala.api.admin.web;

import java.util.List;
import java.util.UUID;

import com.pilipala.api.admin.application.AdminGuard;
import com.pilipala.api.admin.application.AdminRechargeOrderView;
import com.pilipala.api.admin.application.ListAdminRechargeOrders;
import com.pilipala.api.billing.application.RefundRechargeOrder;
import com.pilipala.api.billing.application.RechargeOrderView;
import com.pilipala.api.common.ApiResponse;
import com.pilipala.api.job.infra.JobException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/billing/recharge-orders")
public class AdminBillingRechargeController {

    private final AdminGuard guard;
    private final ListAdminRechargeOrders list;
    private final RefundRechargeOrder refund;

    public AdminBillingRechargeController(AdminGuard guard, ListAdminRechargeOrders list, RefundRechargeOrder refund) {
        this.guard = guard;
        this.list = list;
        this.refund = refund;
    }

    @GetMapping
    public ApiResponse<List<AdminRechargeOrderView>> list(
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "out_trade_no", required = false) String outTradeNo,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        guard.requireAdmin();
        return ApiResponse.ok(list.list(userId, status, outTradeNo, limit));
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<RechargeOrderView> refund(@PathVariable String id) {
        guard.requireAdmin();
        return ApiResponse.ok(refund.refund(orderId(id)));
    }

    private UUID orderId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.validation();
        }
    }
}
