package com.pilipala.api.job.application;

/**
 * submit 的成功载荷。
 *
 * @param jobId  权威任务 id
 * @param status 状态机枚举；刚提交通常是 queued
 * @param holdId 钱包冻结单；WP6 之前恒为 null
 */
public record SubmitJobView(String jobId, String status, String holdId) {
}
