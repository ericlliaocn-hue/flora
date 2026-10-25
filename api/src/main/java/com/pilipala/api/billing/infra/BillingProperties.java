package com.pilipala.api.billing.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code pilipala.billing.*} 的配置。全部来自 application.yml 的环境变量占位。
 *
 * @param defaultJobCost 单次任务默认冻结 credit 数（正整数）
 * @param initialCredits 新注册用户初始赠送 credit 数；0 表示不赠送
 * @param settlePollMs   后台对账扫描间隔（毫秒）
 * @param settleBatchSize 单轮最多结算多少张冻结单
 */
@ConfigurationProperties(prefix = "pilipala.billing")
public record BillingProperties(
        @DefaultValue("10") long defaultJobCost,
        @DefaultValue("0") long initialCredits,
        @DefaultValue("200") long settlePollMs,
        @DefaultValue("50") int settleBatchSize) {
}
