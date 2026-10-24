package com.flora.api.billing.domain;

import java.util.Locale;

/**
 * 结算时关心的任务终态。
 *
 * <p>钱包包不认识 job 包（避免两个域互相依赖），只声明自己关心的三个终态；
 * job 状态与这里的映射由 job 包负责。
 */
public enum JobTerminalState {

    /** 成功：冻结转实扣。 */
    SUCCEEDED,
    /** 取消：冻结解冻退回。 */
    CANCELLED,
    /** 死信 / 用尽重试：冻结解冻退回。 */
    DEAD;

    /** 该终态对应的冻结单终态。 */
    public WalletHoldStatus settlement() {
        return this == SUCCEEDED ? WalletHoldStatus.CONFIRMED : WalletHoldStatus.RELEASED;
    }

    /** 解析 job.status 的小写文本；不是终态时返回 null。 */
    public static JobTerminalState of(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "succeeded" -> SUCCEEDED;
            case "cancelled" -> CANCELLED;
            case "dead" -> DEAD;
            default -> null;
        };
    }
}
