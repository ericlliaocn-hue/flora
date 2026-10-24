package com.flora.api.job.application;

import java.time.Instant;
import java.util.List;

/**
 * 生图历史条目（C 端跨设备可见）。
 *
 * @param resultUrls 仅成功任务有值：每个对象键现签的短期 TOS 读地址
 * @param finishedAt 终态时取最后更新时间，未终态为 null
 */
public record JobHistoryItemView(
        String jobId,
        String status,
        String capability,
        String model,
        String prompt,
        long costCredits,
        List<String> resultUrls,
        String errorCode,
        String message,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt) {
}
