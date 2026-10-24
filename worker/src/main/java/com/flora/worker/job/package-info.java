/**
 * worker 侧任务消费边界。
 *
 * <p>worker 保持瘦：读 job、调 Anytoken、写 step 与结果、ack / 重试 / DLQ。
 * 不在这里拉画布图、不做创作编排、不做计费确认。
 *
 * <p>WP4 落地的是 api 侧的 Job 域（状态机 + outbox + 投递）；真实消费者在 WP5。
 * WP4 不给 worker 加 amqp 依赖，本包仍是空壳说明。
 */
package com.flora.worker.job;
