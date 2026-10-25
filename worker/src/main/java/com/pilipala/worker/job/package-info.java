/**
 * worker 侧任务消费边界。
 *
 * <p>worker 保持瘦：读 job、调唯一 Anytoken 适配器、写 step 与结果、ack / 重试 / DLQ。
 * 不在这里拉画布图、不做创作编排、不做计费确认。
 *
 * <p>WP5 落地：{@code consumer}（RabbitMQ 监听 + 死信）、{@code application}
 * （claim / complete 两段短事务 + 上游调用编排）、{@code domain}（最小 job 视图）、
 * {@code infra}（JDBC 仓储 + Redis 短锁 + 拓扑声明）。
 * 供应商适配器只有一个：{@code com.pilipala.worker.gateway.AnytokenGateway}。
 */
package com.pilipala.worker.job;
