package com.pilipala.api.job.application;

/**
 * 领取结果。
 *
 * @param claimed 是否真的把任务抢到手（false 表示别人已经在跑或已终态）
 */
public record ClaimResult(String jobId, String status, boolean claimed) {
}
