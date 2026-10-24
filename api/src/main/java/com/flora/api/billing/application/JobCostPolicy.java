package com.flora.api.billing.application;

import com.flora.api.billing.infra.BillingProperties;
import org.springframework.stereotype.Component;

/**
 * 单次任务的标价策略。
 *
 * <p>stage-1 只有一种标价：{@code flora.billing.default-job-cost}。
 * 将来要按 capability / model 分档时，只改这里，冻结与结算逻辑不受影响。
 */
@Component
public class JobCostPolicy {

    private final BillingProperties properties;

    public JobCostPolicy(BillingProperties properties) {
        if (properties.defaultJobCost() <= 0) {
            throw new IllegalStateException("flora.billing.default-job-cost 必须是正整数");
        }
        this.properties = properties;
    }

    /**
     * 本次任务要冻结多少 credit。
     *
     * @param capability 能力标识，如 image.generate
     * @param model      flora 内部模型名
     */
    public long costOf(String capability, String model) {
        return properties.defaultJobCost();
    }
}
