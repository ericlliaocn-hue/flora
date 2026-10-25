/**
 * 画布创作任务（Job）领域边界。
 *
 * <p>WP4 起这里落地 {@code job} / {@code job_step} / {@code outbox_message} /
 * {@code idempotency_record} 四张表、任务状态机、事务内 outbox 与 RabbitMQ 投递。
 * 状态机与队列解耦：MQ 消息体只有 {@code job_id}，换 MQ 不换 job 表
 * （见 ADR 0003）。
 *
 * <p>分层：{@code web} 只做 HTTP 适配，{@code application} 是用例，
 * {@code domain} 是状态规则，{@code infra} 是 SQL / Redis / RabbitMQ。
 */
package com.pilipala.api.job;
