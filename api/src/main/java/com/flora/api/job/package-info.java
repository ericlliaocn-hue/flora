/**
 * 预留：画布创作任务（Job）领域边界。
 *
 * <p>WP4 起在这里落地 {@code job} / {@code job_step} 表、任务状态机与 outbox。
 * 状态机与队列解耦：MQ 消息体只有 {@code job_id}，换 MQ 不换 job 表。
 *
 * <p>WP3 阶段本包为空，不含任何业务实现。
 */
package com.flora.api.job;
